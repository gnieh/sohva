/*
* This file is part of the sohva project.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package gnieh.sohva
package test

import org.scalatest._

import spray.json._

class TestSerializer extends SohvaTestSpec with ShouldMatchers {

  "a string" should "be correctly serialized" in {
    "this is my string to serialize".toJson should be(JsString("this is my string to serialize"))
  }

  "an integer" should "be correctly serialized" in {
    4.toJson should be(JsNumber(4))
  }

  "a long" should "be correctly serialized" in {
    12345678901234l.toJson should be(JsNumber(12345678901234l))
  }

  "a big integer" should "be correctly serialized" in {
    BigInt("12345678901234567890").toJson should be(JsNumber(BigInt("12345678901234567890")))
  }

  "a float" should "be correctly serialized" in {
    3.14159.toJson should be(JsNumber(3.14159))
  }

  "a double" should "be correctly serialized" in {
    1.123456789012345d.toJson should be(JsNumber(1.123456789012345))
  }

  "a big decimal" should "be correctly serialized" in {
    BigDecimal("1.123456789012345").toJson should be(JsNumber(1.123456789012345))
  }

  "true" should "be correctly serialized" in {
    true.toJson should be(JsBoolean(true))
  }

  "false" should "be correctly serialized" in {
    false.toJson should be(JsBoolean(false))
  }

}
