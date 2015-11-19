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

import akka.http.scaladsl.model.headers.GenericHttpCredentials

trait Credentials {
  this: Common =>

  /**
    * Hawk HTTP authentication headers, represented as Akka HTTP's [[akka.http.scaladsl.model.headers.GenericHttpCredentials]]
    */
  val hawkCredentials_GET_withPort = GenericHttpCredentials("Hawk", Map(
    "id" -> "dh37fgj492je",
    "ts" -> defaultTime.toString,
    "nonce" -> "j4h3g2",
    "ext" -> "some-app-ext-data",
    "mac" -> "6R4rV5iE+NPoym+WwjeHzjAGXUtLNIxmo1vpMofpLAE=")
  )

  val hawkCredentials_GET_withPort_timestamp_left_of_timeframe = GenericHttpCredentials("Hawk", Map(
    "id" -> "dh37fgj492je",
    "ts" -> (defaultTime - 61000).toString, // 1353832234 - 61 secs (61000 millis)
    "nonce" -> "j4h3g2",
    "ext" -> "some-app-ext-data",
    "mac" -> "TsmGb+yKA6tXvQsBOGobUoBJoy8U7cHXJm/ZybG2Xuc=")
  )

  val hawkCredentials_GET_withPort_timestamp_right_of_timeframe = GenericHttpCredentials("Hawk", Map(
    "id" -> "dh37fgj492je",
    "ts" -> (defaultTime + 61000).toString, // 1353832234 + 61 secs (61000 millis)
    "nonce" -> "j4h3g2",
    "ext" -> "some-app-ext-data",
    "mac" -> "AB5kPX4S2RSWIrYgw4R5IMVeLco3y2nFBZfMyZd1Pfc=")
  )

  val hawkCredentials_POST_withPort = GenericHttpCredentials("Hawk", Map(
    "id" -> "dh37fgj492je",
    "ts" -> defaultTime.toString,
    "nonce" -> "j4h3g2",
    "ext" -> "some-app-ext-data",
    "mac" -> "56wgBMHr4oIwA/dGZspMm6Zk4rnf3aiwwVeL0VtWoGo=")
  )

  val hawkCredentials_POST_withPortWithPayload = GenericHttpCredentials("Hawk", Map(
    "id" -> "dh37fgj492je",
    "ts" -> defaultTime.toString,
    "nonce" -> "j4h3g2",
    "hash" -> "Yi9LfIIFRtBEPt74PVmbTF/xVAwPn7ub15ePICfgnuY=",
    "ext" -> "some-app-ext-data",
    "mac" -> "aSe1DERmZuRl3pI36/9BdZmnErTw3sNzOOAUlfeKjVw=")
  )

  val hawkCredentials_GET_withoutPort = GenericHttpCredentials("Hawk", Map(
    "id" -> "dh37fgj492je",
    "ts" -> defaultTime.toString,
    "nonce" -> "j4h3g2",
    "ext" -> "some-app-ext-data",
    "mac" -> "fmzTiKheFFqAeWWoVIt6vIflByB9X8TeYQjCdvq9bf4=")
  )

  val invalidHawkCredentialsScheme = GenericHttpCredentials("Hawkz", Map(
    "id" -> "dh37fgj492je",
    "ts" -> defaultTime.toString,
    "nonce" -> "j4h3g2",
    "ext" -> "some-app-ext-data",
    "mac" -> "6R4rV5iE+NPoym+WwjeHzjAGXUtLNIxmo1vpMofpLAE=")
  )

}
