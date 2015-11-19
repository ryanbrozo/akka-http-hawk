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
import akka.http.scaladsl.model.headers._
import com.ryanbrozo.scala.hawk._

/**
  * Abstracts access to attributes specific to a Hawk Authorization header. Given an HttpRequest, this class extracts the
  * `Authorization` header attributes
  *
  * @param request Akka HttpRequest to extract attributes from.
  */
private[hawk] case class AuthHeaderAttributes(request: HttpRequest) {
  import AuthHeaderKeys._

  private lazy val _authHeader: Option[Authorization] = request.headers
    .collect {
      case h: Authorization if h.credentials.scheme == HEADER_NAME => h
    }
    .headOption

  lazy val isPresent: Boolean = _authHeader.isDefined

  private lazy val _credentials = _authHeader.map {
    case Authorization(creds: GenericHttpCredentials) => creds
  }

  private lazy val _extractor = _credentials map extractAuthKey

  /**
    * Extracts a key from the Authorization header
    *
    * @param credentials Authorization header represented as [[akka.http.scaladsl.model.headers.GenericHttpCredentials]]
    * @param key Key of value to obtain
    * @return Extracted value wrapped as a [[scala.Option]]
    */
  private def extractAuthKey(credentials: GenericHttpCredentials)(key: AuthHeaderKeys.Value): Option[String] =
    credentials.params.get(key.toString)

  lazy val id: String = _extractor flatMap {_(Id)} getOrElse ""
  lazy val tsString: Option[String] = _extractor flatMap {_(Ts)}
  lazy val ts: TimeStamp = tsString map {_.toDouble.toLong} getOrElse 0
  lazy val nonce: Option[Nonce] = _extractor flatMap {_(Nonce)}
  lazy val hash: Option[String] = _extractor flatMap {_(Hash)}
  lazy val ext: Option[ExtData] = _extractor flatMap {_(Ext)}
  lazy val mac: Option[String] = _extractor flatMap {_(Mac)}
}
