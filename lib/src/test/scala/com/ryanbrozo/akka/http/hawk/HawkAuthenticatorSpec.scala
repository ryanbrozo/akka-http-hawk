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

import akka.http.scaladsl.model.{StatusCodes, HttpHeader}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, ExceptionHandler}
import akka.http.scaladsl.testkit._
import com.ryanbrozo.akka.http.hawk.HawkAuthenticator._
import com.ryanbrozo.akka.http.hawk.HawkError._
import com.ryanbrozo.scala.hawk._
import org.scalatest._

import scala.concurrent.Future
import scala.concurrent.duration._

class HawkAuthenticatorSpec
  extends FlatSpec
  with Matchers
  with ScalatestRouteTest {

  import common.Imports._

  override implicit val executor = system.dispatcher
  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(60, SECONDS))

  object TestException extends RuntimeException

  /**
    * A UserRetriever that always does not authenticate
    */
  val userRetrieverDontAuth: UserRetriever[User] = { _ => Future.successful(None) }

  /**
    * A UserRetriever that always authenticates
    */
  val userRetrieverDoAuth: UserRetriever[User] = { _ => Future.successful(Some(hawkUser)) }


  /**
    * A UserRetriever that always throws an exception
    */
  val userRetrieverThrowException: UserRetriever[User] = { _ => throw TestException }


  /**
    * Produces a WWW-Authenticate header
    * @param params Map of additional attributes to be added to the WWW-Authenticate header
    * @return WWW-Authenticate header
    */
  def produceWwwAuthHeader(params: Map[String, String]): List[HttpHeader] = {
    `WWW-Authenticate`(HttpChallenge("Hawk", realm, params)) :: Nil
  }

  /**
    * Produce a WWW-Authenticate header with additional error attribute
    * @param error Error string
    */
  def produceWwwAuthHeader(error: String): List[HttpHeader] = produceWwwAuthHeader(Map("error" -> error))

  def produceHawkRejection(hawkError: HawkError): HawkRejection = {
    HawkRejection(hawkError, produceWwwAuthHeader(hawkError.message))
  }

  "The authenticateHawk() directive" should "reject requests without Authorization header with an AuthenticationRequiredRejection" in {
    Get() ~> {
      authenticateHawk(userRetrieverDontAuth, realm) { user =>
        complete(user.name)
      }
    } ~> check {
      rejection should be(produceHawkRejection(CredentialsMissingError))
    }
  }

  it should "reject unauthenticated requests with Authorization header with an AuthorizationFailedRejection" in {
    Get("http://www.example.com:8000/abc")
      .withHeaders(Authorization(hawkCredentials_GET_withPort) :: Nil) ~>
      authenticateHawk(userRetrieverDontAuth, realm) { user =>
        complete(user.name)
      } ~> check {
      rejection should be(produceHawkRejection(InvalidCredentialsError))
    }
  }

  it should "reject incorrect mac in Authorization header with an AuthorizationFailedRejection" in {
    Get("http://www.example.com:8000/abc")
      .withHeaders(Authorization(hawkCredentials_GET_withPort) :: Nil) ~>
      authenticateHawk(userRetrieverDoAuth, realm, defaultTimeGenerator _, Util.stupidNonceValidator) { user =>
        complete(user.name)
      } ~> check {
      rejection should be(produceHawkRejection(InvalidMacError))
    }
  }

  it should "extract the object representing the user identity created by successful authentication" in {
    Get("http://example.com:8000/resource/1?b=1&a=2")
      .withHeaders(Authorization(hawkCredentials_GET_withPort) :: Nil) ~>
      authenticateHawk(userRetrieverDoAuth, realm, defaultTimeGenerator _, Util.stupidNonceValidator) { user =>
        complete(user.name)
      } ~> check {
      responseAs[String] should be("Bob")
    }
  }

  it should "extract the object representing the user identity created by successful authentication " +
    "in a POST request (without payload validation)" in {
    Post("http://example.com:8000/resource/1?b=1&a=2", "Thank you for flying Hawk")
      .withHeaders(Authorization(hawkCredentials_POST_withPort) :: Nil) ~>
      authenticateHawk(userRetrieverDoAuth, realm, defaultTimeGenerator _, Util.stupidNonceValidator) { user =>
        complete(user.name)
      } ~> check {
      responseAs[String] should be("Bob")
    }
  }

  it should "reject unauthenticated requests with invalid Authorization header scheme with an AuthorizationFailedRejection" in {
    Get("http://example.com:8000/resource/1?b=1&a=2")
      .withHeaders(Authorization(invalidHawkCredentialsScheme) :: Nil) ~>
      authenticateHawk(userRetrieverDoAuth, realm, defaultTimeGenerator _, Util.stupidNonceValidator) { user =>
        complete(user.name)
      } ~> check {
      rejection should be(produceHawkRejection(CredentialsMissingError))
    }
  }

  it should "properly handle exceptions thrown in its inner route" in {
    Get("http://example.com:8000/resource/1?b=1&a=2")
      .withHeaders(Authorization(hawkCredentials_GET_withPort) :: Nil) ~>
      Route.seal {
        authenticateHawk(userRetrieverDoAuth, realm, defaultTimeGenerator _, Util.stupidNonceValidator) { _ => throw TestException }
      } ~> check {
      status should be(StatusCodes.InternalServerError)
    }
  }

  it should "reject requests when an exception is encountered while retrieving a user" in {
    Get("http://example.com:8000/resource/1?b=1&a=2")
      .withHeaders(Authorization(hawkCredentials_GET_withPort) :: Nil) ~>
      authenticateHawk(userRetrieverThrowException, realm, defaultTimeGenerator _, Util.stupidNonceValidator) { user =>
        complete(user.name)
      } ~> check {
      rejection should be(produceHawkRejection(UserRetrievalError(TestException)))
    }
  }

  it should "properly handle X-Forwarded-Proto header in case it is set" in {
    Get("https://example.com/resource/1?b=1&a=2")
      .withHeaders(
        RawHeader("X-Forwarded-Proto", "http") ::
        Authorization(hawkCredentials_GET_withoutPort) :: Nil
      ) ~>
      authenticateHawk(userRetrieverDoAuth, realm, defaultTimeGenerator _, Util.stupidNonceValidator) { user =>
        complete(user.name)
      } ~> check {
      responseAs[String] should be("Bob")
    }
  }


}