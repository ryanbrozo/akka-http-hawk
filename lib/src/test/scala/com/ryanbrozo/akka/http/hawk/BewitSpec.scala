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

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit._
import com.ryanbrozo.akka.http.hawk.HawkAuthenticator._
import com.ryanbrozo.akka.http.hawk.HawkError.MultipleAuthenticationError
import org.scalatest._

import scala.concurrent.duration._

class BewitSpec extends FlatSpec with Matchers with ScalatestRouteTest {

  import common.Imports._

  override implicit val executor = system.dispatcher
  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(60, SECONDS))

  "The 'authenticateHawk()' directive" should "properly authenticate if authentication information is encoded in a bewit" in {
    Get("http://example.com:8000/resource/1?b=1&a=2&bewit=ZGgzN2ZnajQ5MmplXDEzNTM4MzYyMzRcZ2tIRXZVU3VWVis5aEEzcnd6R2hadDM3RnlVZk5xdnNacHQzMHNoUGZFcz1cc3ByYXktaGF3aw%3D%3D") ~> {
      authenticateHawk(userRetrieverDoAuth, realm) { user =>
        complete(user.name)
      }
    } ~> check {
      responseAs[String] === "Bob"
    }
  }

  it should "reject the request if both Hawk Authorization header and bewit parameter are present" in {
    Get("http://example.com:8000/resource/1?b=1&a=2&bewit=ZGgzN2ZnajQ5MmplXDEzNTM4MzYyMzRcZ2tIRXZVU3VWVis5aEEzcnd6R2hadDM3RnlVZk5xdnNacHQzMHNoUGZFcz1cc3ByYXktaGF3aw%3D%3D")
      .withHeaders(Authorization(hawkCredentials_GET_withPort) :: Nil) ~>
      authenticateHawk(userRetrieverDoAuth, realm, defaultTimeGenerator _, Util.stupidNonceValidator) { user =>
        complete(user.name)
      } ~> check {
      rejection === produceHawkRejection(MultipleAuthenticationError)
    }
  }

  it should "properly authenticate if both bewit is and an Authentication header with scheme other than Hawk is present" in {
    Get("http://example.com:8000/resource/1?b=1&a=2&bewit=ZGgzN2ZnajQ5MmplXDEzNTM4MzYyMzRcZ2tIRXZVU3VWVis5aEEzcnd6R2hadDM3RnlVZk5xdnNacHQzMHNoUGZFcz1cc3ByYXktaGF3aw%3D%3D")
      .withHeaders(Authorization(BasicHttpCredentials("someuser", "somepassword")) :: Nil) ~>
      authenticateHawk(userRetrieverDoAuth, realm, defaultTimeGenerator _, Util.stupidNonceValidator) { user =>
        complete(user.name)
      } ~> check {
      responseAs[String] === "Bob"
    }
  }
}

