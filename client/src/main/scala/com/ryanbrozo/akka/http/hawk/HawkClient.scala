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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.ryanbrozo.scala.hawk._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * HawkClient.scala
  *
  * Created by rye on 7/27/16.
  */


object HawkClient extends App {

  import com.ryanbrozo.akka.http.hawk.Imports._

  implicit val system = ActorSystem("hawk-client")

  implicit val executor = system.dispatcher

  implicit val materializer = ActorMaterializer()

  val hawkCreds = HawkCredentials("dh37fgj492je", "werxhqb98rpaxn39848xrunpaw3489ruxnpa98w4rxn", HawkHashAlgorithms.HawkSHA256)

  val request = HttpRequest(uri = "http://localhost:8081/secured").withHawkCredentials("hawk-client", hawkCreds)

  val future = Http().singleRequest(request)
    .flatMap{ Unmarshal(_).to[String] }

  println(Await.result(future, 30 seconds))
}
