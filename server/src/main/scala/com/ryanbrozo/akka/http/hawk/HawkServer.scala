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
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.ryanbrozo.scala.hawk._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
  * Akka-http server that demonstrates Hawk authentication. You can access it via http://localhost:8080/secured
  *
  */
object HawkServer extends App {

  import HawkAuthenticator._

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  /**
    * Our User model. This needs to extend the HawkUser trait for our UserCredentialsRetriever
    * to work
    */
  case class User(name: String, key: String, algorithm: HawkHashAlgorithms) extends HawkUser

  val userCredentialsRetriever: UserRetriever[User] = { id =>
    Future.successful {
      if (id == "dh37fgj492je") Some(User("Bob", "werxhqb98rpaxn39848xrunpaw3489ruxnpa98w4rxn", HawkHashAlgorithms.HawkSHA256))
      else Option.empty[User]
    }
  }

  val route = {
    path("secured"){
      handleRejections(hawkRejectionHandler) {
        authenticateHawk(userCredentialsRetriever, "hawk-test") { user =>
          get {
            complete {
              s"Welcome to spray, ${user.name}!"
            }
          } ~
            post {
              entity(as[String]) { body =>
                complete {
                  s"Welcome to akka-http-hawk, ${user.name}! Your post body was: $body"
                }
              }
            }
        }
      }
    }
  }


  val bindingFuture = Http().bindAndHandle(route, "localhost", 8081)

  sys.addShutdownHook{
    system.terminate()
    Await.result(system.whenTerminated, 30 seconds)
  }


}
