/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Ryan C. Brozo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.ryanbrozo.akka.http.hawk

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives._
import akka.stream.Materializer
import akka.util.ByteString.ByteString1C
import com.ryanbrozo.akka.http.hawk.HawkError._
import com.ryanbrozo.scala.hawk._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}
import scala.util._

object HawkAuthenticator {

  private val _conf = ConfigFactory.load()

  private val _payloadValidationEnabled = _conf.getBoolean("akka.http.hawk.payloadValidation")
  private val _timeSkewValidationEnabled = _conf.getBoolean("akka.http.hawk.timeSkewValidation")
  private val _timeSkewInSeconds = _conf.getLong("akka.http.hawk.timeSkewInSeconds")
  private val _maxUserRetrieverTimeInSeconds = _conf.getLong("akka.http.hawk.maxUserRetrieverTimeInSeconds") seconds


  def authenticateHawk[U <: HawkUser](userRetriever: UserRetriever[U], realm: String): AuthenticationDirective[U] =
    authenticateHawk(userRetriever, realm, Util.defaultTimestampProvider, Util.defaultNonceValidator)

  def authenticateHawk[U <: HawkUser](userRetriever: UserRetriever[U], realm: String, timestampProvider: TimeStampProvider): AuthenticationDirective[U] =
    authenticateHawk(userRetriever, realm, timestampProvider, Util.defaultNonceValidator)

  def authenticateHawk[U <: HawkUser](userRetriever: UserRetriever[U], realm: String, nonceValidator: NonceValidator): AuthenticationDirective[U] =
    authenticateHawk(userRetriever, realm, Util.defaultTimestampProvider, nonceValidator)

  def authenticateHawk[U <: HawkUser](userRetriever: UserRetriever[U], realm: String, timestampProvider: TimeStampProvider,
                                      nonceValidator: NonceValidator): AuthenticationDirective[U] = {
    extractMaterializer.flatMap { implicit mat =>
      extractExecutionContext.flatMap { implicit ec ⇒
        extractRequest.flatMap { req =>
          val hawkRequest = HawkRequest(req)
          val authenticator = new HawkAuthenticator[U](timestampProvider, nonceValidator)(realm, userRetriever)
          onSuccess(authenticator.authenticate(hawkRequest)).flatMap {
            case Right(u) =>
              provide(u)
            case Left(e) =>
              reject(HawkRejection(e, authenticator.getChallengeHeaders(e))): Directive1[U]
          }
        }
      }
    }
  }


}

class HawkAuthenticator[U <: HawkUser](timestampProvider: TimeStampProvider, nonceValidator: NonceValidator)
                                      (realm: String, userRetriever: UserRetriever[U])
                                        (implicit val executionContext: ExecutionContext)
  extends StrictLogging {

  import HawkAuthenticator._

  val SCHEME = HEADER_NAME

  /**
    * Produces a list of Http Challenge Headers
    *
    * @param hawkError HawkError used to produce the challenge headers
    * @return List of challenge headers
    */
  private def getChallengeHeaders(hawkError: HawkError): List[HttpHeader] = {
    val params = hawkError match {
      case err: StaleTimestampError =>
        val currentTimestamp = timestampProvider()
        Map(
          "ts" -> currentTimestamp.toString,
          "tsm" -> HawkTimestamp(currentTimestamp, err.hawkUser).mac,
          "error" -> err.message
        )
      case err =>
        Map(
          "error" -> err.message
        )
    }
    `WWW-Authenticate`(HttpChallenge(SCHEME, realm, params)) :: Nil
  }

  /**
    * Authenticates an incoming request. This method checks if Hawk credentials came from bewit or Authorization header
    * and validates accordingly
    *
    * @param hawkRequest HawkRequest instance to validate.
    * @return Either a HawkError, if authorization is not valid, or a HawkUser if authorization is valid. Result is wrapped
    *         in a Future
    */
  private def authenticate(hawkRequest: HawkRequest): Future[Either[HawkError, U]] = {
    def validate(id: String, validateFunc: (Option[U], HawkRequest) => Either[HawkError, U]): Future[Either[HawkError, U]] = {
      val userTry = Try {
        // Assume the supplied userRetriever function can throw an exception
        userRetriever(id)
      }
      userTry match {
        case Success(userFuture) =>
          userFuture map { validateFunc(_, hawkRequest) }
        case Failure(e) =>
          logger.warn(s"An error occurred while retrieving a hawk user: ${e.getMessage}")
          Future.successful(Left(UserRetrievalError(e)))
      }
    }

    // Determine whether to use bewit parameter or Authorization header
    // Request should not have both
    if (hawkRequest.hasBewit && hawkRequest.hasAuthorizationHeader) {
      Future.successful(Left(MultipleAuthenticationError))
    }
    else {
      // Ensure bewit is valid
      if (hawkRequest.hasBewit) {
        if (hawkRequest.bewitAttributes.isInvalid.isDefined) {
          Future.successful(Left(hawkRequest.bewitAttributes.isInvalid.get))
        }
        else validate(hawkRequest.bewitAttributes.id, validateBewitCredentials)
      }
      else {
        if (!hawkRequest.authHeaderAttributes.isPresent) {
          Future.successful(Left(CredentialsMissingError))
        }
        else validate(hawkRequest.authHeaderAttributes.id, validateAuthHeaderCredentials)
      }
    }
  }

  /**
    * Checks if given bewit credentials are valid
    *
    * @param hawkUserOption Hawk user, wrapped in an Option
    * @param hawkRequest HawkRequest instance
    * @return Either a HawkError or the validated HawkUser
    */
  private def validateBewitCredentials(hawkUserOption: Option[U], hawkRequest: HawkRequest): Either[HawkError, U] = {
    def checkMethod(implicit hawkUser: U): Either[HawkError, U] = {
      if (hawkRequest.request.method != HttpMethods.GET)
        Left(InvalidMacError)
      else
        Right(hawkUser)
    }

    def checkExpiry(implicit hawkUser: U): Either[HawkError, U] = {
      val currentTimestamp = timestampProvider()
      if (hawkRequest.bewitAttributes.exp * 1000 <= currentTimestamp)
        Left(AccessExpiredError)
      else
        Right(hawkUser)
    }

    def checkMac(implicit hawkUser: U): Either[HawkError, U] = {
      if (hawkRequest.bewitAttributes.mac != Hawk(hawkUser, hawkRequest.bewitOptions, Hawk.TYPE_BEWIT).mac)
        Left(InvalidMacError)
      else
        Right(hawkUser)
    }

    hawkUserOption map { implicit hawkUser =>
      for {
        methodOk <- checkMethod.right
        expiryOk <- checkExpiry.right
        macOk <- checkMac.right
      } yield expiryOk
    } getOrElse Left(InvalidCredentialsError)
  }

  /**
    * Checks if given Authorization header is valid
    *
    * @param hawkUserOption Hawk user, wrapped in an Option
    * @param hawkRequest HawkRequest instance
    * @return Either a HawkError or the validated HawkUser
    */
  private def validateAuthHeaderCredentials(hawkUserOption: Option[U], hawkRequest: HawkRequest): Either[HawkError, U] = {

    def checkMac(implicit hawkUser: U): Either[HawkError, U] = {
      (for {
        mac <- hawkRequest.authHeaderAttributes.mac if mac == Hawk(hawkUser, hawkRequest.hawkOptions, Hawk.TYPE_HEADER).mac
      } yield Right(hawkUser))
        .getOrElse(Left(InvalidMacError))
    }

    def checkPayload(implicit hawkUser: U): Either[HawkError, U] = {
      hawkRequest.authHeaderAttributes.hash match {
        case Some(hash) if _payloadValidationEnabled =>
          (for {
            (payload, contentType) <- hawkRequest.payload
            hawkPayload <- Option(HawkPayload(payload, contentType, hawkUser.algorithm.hashAlgo))
            if hawkPayload.hash == hash
          } yield Right(hawkUser))
            .getOrElse(Left(InvalidPayloadHashError))
        case _ =>
          // 'hash' is not supplied? then no payload validation is needed.
          // Return the obtained credentials
          Right(hawkUser)
      }
    }

    def checkNonce(implicit hawkUser: U): Either[HawkError, U] = {
      hawkRequest.authHeaderAttributes.nonce match {
        case Some(n) if nonceValidator(n, hawkUser.key, hawkRequest.authHeaderAttributes.ts) => Right(hawkUser)
        case _ => Left(InvalidNonceError)
      }
    }

    def checkTimestamp(implicit hawkUser: U): Either[HawkError, U] = {
      if (_timeSkewValidationEnabled) {
        val timestamp = hawkRequest.authHeaderAttributes.ts
        val currentTimestamp = timestampProvider()
        val lowerBound = currentTimestamp - _timeSkewInSeconds
        val upperBound = currentTimestamp + _timeSkewInSeconds
        if (lowerBound <= timestamp && timestamp <= upperBound)
          Right(hawkUser)
        else
          Left(StaleTimestampError(hawkUser))
      }
      else Right(hawkUser)
    }

    hawkUserOption map { implicit hawkUser =>
      for {
        macOk <- checkMac.right
        // According to Hawk specs, payload validation should should only
        // happen if MAC is validated.
        payloadOk <- checkPayload.right
        nonceOk <- checkNonce.right
        tsOk <- checkTimestamp.right
      } yield tsOk
    } getOrElse Left(InvalidCredentialsError)
  }
}
