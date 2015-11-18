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

package com.ryanbrozo.scala.hawk

import org.scalatest._

class HawkTimestampSpec extends FlatSpec with Matchers {

  val credentials = HawkCredentials("id", "werxhqb98rpaxn39848xrunpaw3489ruxnpa98w4rxn", HawkHashAlgorithms.HawkSHA256)

  "HawkTimeStamp" should "return a valid normalized string" in {
    HawkTimestamp(1353832234, credentials)._normalized should be(
      "hawk.1.ts\n1353832234\n"
    )
  }

  it should "produce the correct MAC" in {
    HawkTimestamp(1353832234, credentials).mac should be(
      "2mw1eh/qXzl0wJZ/E6XvBhRMEJN7L3j8AyMA8eItEb0="
    )
  }
}
