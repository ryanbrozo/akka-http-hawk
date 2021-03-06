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
import akka.stream.Materializer
import com.ryanbrozo.scala.hawk._

import scala.concurrent._

/**
  * Class that extracts parameters relevant to Hawk authentication from a Akka Http [[HttpRequest]] instance.
  *
  * @param request Akka Http [[HttpRequest]] instance to extract information to from
  */
private [hawk] case class HawkRequest(request: HttpRequest)(implicit materializer: Materializer, executionContext: ExecutionContext) extends Util {

  /**
    * Hawk options extracted from the request (host, port, uri, and method)
    */
  lazy val requestAttributes: RequestAttributes = RequestAttributes(request)

  /**
    * Hawk options extracted from Authorization header (id, ts, nonce, hash, ext, and mac)
    */
  lazy val authHeaderAttributes: AuthHeaderAttributes = AuthHeaderAttributes(request)

  /**
    * Bewit parameter extracted from query parameters
    */
  lazy val bewitAttributes: BewitAttributes = BewitAttributes(request)

  /**
    * Complete options used to calculate HMAC of the request via the Authorization header.
    * Basically consists of requestAttributes and authHeaderAttributes
    */
  lazy val hawkOptions: HawkOptions = {
    Map(
      HawkOptionKeys.Method -> Option(requestAttributes.method),
      HawkOptionKeys.Uri -> Option(requestAttributes.uri),
      HawkOptionKeys.Host -> Option(requestAttributes.host),
      HawkOptionKeys.Port -> Option(requestAttributes.port.toString),
      HawkOptionKeys.Ts -> authHeaderAttributes.tsString,
      HawkOptionKeys.Nonce -> authHeaderAttributes.nonce,
      HawkOptionKeys.Ext -> authHeaderAttributes.ext,
      HawkOptionKeys.Hash -> authHeaderAttributes.hash).collect { case (k, Some(v)) => k -> v }
  }

  /**
    * Complete options used to calculate HMAC of the request via the bewit parameter.
    */
  lazy val bewitOptions: HawkOptions = {
    Map(
      // Hard-coded get, since bewit-based auth should only be GET.
      // Don't obtain method from our request
      HawkOptionKeys.Method -> Some("GET"),
      HawkOptionKeys.Uri -> Option(bewitAttributes.uriWithoutBewit),
      HawkOptionKeys.Host -> Option(requestAttributes.host),
      HawkOptionKeys.Port -> Option(requestAttributes.port.toString),
      HawkOptionKeys.Ts -> Option(bewitAttributes.exp.toString),
      HawkOptionKeys.Ext -> Option(bewitAttributes.ext)).collect { case (k, Some(v)) => k -> v }
  }

  /**
    * Payload associated with the request with associated media type
    */
  lazy val payload: Future[Option[(Array[Byte], String)]] = extractPayload(request)

  /**
    * Determines whether this request has given authorization parameters
    */
  lazy val hasAuthorizationHeader: Boolean = authHeaderAttributes.isPresent

  /**
    * Determines whether this request has given bewit parameter
    */
  lazy val hasBewit: Boolean = bewitAttributes.isPresent

}
