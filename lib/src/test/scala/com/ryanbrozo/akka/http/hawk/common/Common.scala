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

package com.ryanbrozo.akka.http.hawk.common

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.{HttpChallenge, `WWW-Authenticate`}
import com.ryanbrozo.akka.http.hawk.{HawkRejection, HawkError}
import com.ryanbrozo.akka.http.hawk.common.Imports._
import com.ryanbrozo.scala.hawk._

import scala.concurrent.Future

trait Common {

  /**
    * Our user model, which implements HawkUser
    */
  case class User(name: String, id: String, key: String, algorithm: HawkHashAlgorithms) extends HawkUser

  /**
    * Hawk user to be used in tests
    */
  val hawkUser = User("Bob", "dh37fgj492je", "werxhqb98rpaxn39848xrunpaw3489ruxnpa98w4rxn", HawkHashAlgorithms.HawkSHA256)

  /**
    * Test Realm
    */
  val realm = "testRealm"

  /**
    * Constant moment in time. Used to isolate time-independent features of the protocol
    */
  val defaultTime = 1353832234L

  /**
    * Constant time to isolate tests that are agnostic to time skew
    * @return Constant moment in time (1353832234L)
    */
  def defaultTimeGenerator: TimeStamp = defaultTime

  /**
    * Test exception to throw when testing our routes
    */
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

}
