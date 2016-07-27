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

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import com.ryanbrozo.scala.hawk.{AuthHeaderKeys, Hawk, HawkCredentials, HawkOptionKeys, _}

import scala.concurrent.ExecutionContext

/**
  * Imports.scala
  *
  * Created by rye on 7/27/16.
  */
object Imports {

  implicit class HawkHttpRequest(val value: HttpRequest) {

    private def generateRawHeader(timestampProvider: TimeStampProvider, nonceProvider: NonceProvider,
                                  ext: ExtData, credentials: HawkCredentials, withPayloadValidation: Boolean)
                                 (implicit executionContext: ExecutionContext, materializer: Materializer): RawHeader = {

      val hawkRequest = HawkRequest(value)

      //      // Do we need to compute 'hash' param?
      //      val payloadHashOption = if (withPayloadValidation) {
      //        hawkRequest.payload map {
      //          case (payload, contentType) => HawkPayload(payload, contentType, credentials.algorithm.hashAlgo).hash
      //        }
      //      } else None

      // First, let's extract URI-related hawk options
      val hawkOptions = hawkRequest.hawkOptions

      // Then add our user-specified parameters
      val ts = timestampProvider().toString
      val nonce = nonceProvider()
      val updatedOptions = hawkOptions ++ Map(
        HawkOptionKeys.Ts -> Option(ts),
        HawkOptionKeys.Nonce -> Option(nonce),
        HawkOptionKeys.Ext -> Option(ext),
        HawkOptionKeys.Hash -> None
      ).collect { case (k, Some(v)) => k -> v }

      // Compute our MAC
      val mac = Hawk(credentials, updatedOptions, Hawk.TYPE_HEADER).mac

      // Then create our Hawk Authorization header
      val authHeader = Map(
        AuthHeaderKeys.Id -> Option(credentials.id),
        AuthHeaderKeys.Ts -> Option(ts),
        AuthHeaderKeys.Nonce -> Option(nonce),
        AuthHeaderKeys.Ext -> Option(ext),
        AuthHeaderKeys.Mac -> Option(mac),
        AuthHeaderKeys.Hash -> None
      )
        .collect({ case (k, Some(v)) => k.toString + "=" + "\"" + v + "\"" })
        .mkString(", ")

      RawHeader("Authorization", s"$HEADER_NAME $authHeader")
    }

    def withHawkCredentials(ext: ExtData, creds: HawkCredentials, withPayloadValidation: Boolean = false,
                            timeStampProvider: TimeStampProvider = Util.defaultTimestampProvider,
                            nonceProvider: NonceProvider = Util.defaultNonceGenerator)
                           (implicit executionContext: ExecutionContext, materializer: Materializer): HttpRequest = {
      value.copy(headers = value.headers.+:(generateRawHeader(timeStampProvider, nonceProvider, ext, creds,withPayloadValidation)))
    }
  }

}
