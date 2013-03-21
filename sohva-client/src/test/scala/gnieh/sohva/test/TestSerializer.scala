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

object TestSerializer extends SohvaTestSpec with ShouldMatchers {

  import couch.serializer.toJson

  "a string" should "be correctly serialized" in {
    toJson("this is my string to serialize") should be("\"this is my string to serialize\"")
  }

  "an integer" should "be correctly serialized" in {
    toJson(4) should be("4")
  }

  "a long" should "be correctly serialized" in {
    toJson(12345678901234l) should be("12345678901234")
  }

  "a big integer" should "be correctly serialized" in {
    toJson(BigInt("12345678901234567890")) should be("12345678901234567890")
  }

  "a float" should "be correctly serialized" in {
    toJson(3.14159) should be("3.14159")
  }

  "a double" should "be correctly serialized" in {
    toJson(1.123456789012345d) should be("1.123456789012345")
  }

  "a big decimal" should "be correctly serialized" in {
    toJson(BigDecimal("1.123456789012345")) should be("1.123456789012345")
  }

  "true" should "be correctly serialized" in {
    toJson(true) should be("true")
  }

  "false" should "be correctly serialized" in {
    toJson(false) should be("false")
  }

}
