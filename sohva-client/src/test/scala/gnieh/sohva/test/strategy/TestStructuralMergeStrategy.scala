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
package strategy

import org.scalatest._
import org.scalatest.OptionValues._

import sync._
import gnieh.sohva.strategy.StructuralMergeStrategy

import spray.json._

class TestStructuralMergeStrategy extends FlatSpec with ShouldMatchers {

  "a path deleted by a first patch" should "not be modified by a subsequent patch" in {
    val json1 = "[1, 2, 3]".parseJson
    val json2 = "[1, 3]".parseJson
    val json3 = "[1, 4, 3]".parseJson

    StructuralMergeStrategy(Some(json1), Some(json2), json3) should be(Some(json2))

  }

  it should "be ignored if it is also modified by a subsequent patch" in {
    val json1 = "[1, 2, 3]".parseJson
    val json2 = "[1, 4, 3]".parseJson
    val json3 = "[1, 5, 3]".parseJson

    StructuralMergeStrategy(Some(json1), Some(json2), json3) should be(Some(json3))
  }

  "a path modified by a first path" should "be deleted if a subsequent patch deletes it" in {

    val json1 = "[1, 2, 3]".parseJson
    val json2 = "[1, 4, 3]".parseJson
    val json3 = "[1, 3]".parseJson

    StructuralMergeStrategy(Some(json1), Some(json2), json3) should be(Some(json3))

  }

  it should "be modified as in the subsequent path if modified by both" in {

    val json1 = "[1, 2, 3]".parseJson
    val json2 = "[1, 4, 3]".parseJson
    val json3 = "[1, 5, 3]".parseJson

    StructuralMergeStrategy(Some(json1), Some(json2), json3) should be(Some(json3))

  }

  it should "be modifies as in this path if not touched by the subsequent patch" in {

    val json1 = "[1, 2, 3]".parseJson
    val json2 = "[1, 4, 3]".parseJson
    val json3 = "[1, 2, 3, 4]".parseJson

    StructuralMergeStrategy(Some(json1), Some(json2), json3) should be(Option("[1, 4, 3, 4]".parseJson))

  }

  "a path added by a first patch" should "be the one from the subsequent patch if it adds the same path" in {

    val json1 = "[1, 2, 3]".parseJson
    val json2 = "[1, 2, 3, 4]".parseJson
    val json3 = "[1, 2, 3, 5]".parseJson

    StructuralMergeStrategy(Some(json1), Some(json2), json3) should be(Some(json3))

    val json4 = "[1, 2, 3]".parseJson
    val json5 = "[1, 4, 2, 3]".parseJson
    val json6 = "[1, 5, 2, 3]".parseJson

    StructuralMergeStrategy(Some(json1), Some(json2), json3) should be(Some(json3))

  }

  "non conflicting modifications" should "be all present in the result" in {

    val json1 = """{
                   |  "f1": 1,
                   |  "f2": [1, 2, 3],
                   |  "f3": {
                   |    "f1": [
                   |      {},
                   |      { "f1": true }
                   |    ]
                   |  }
                   |}""".stripMargin.parseJson
    val json2 = """{
                   |  "f1": 18,
                   |  "f2": [1, 8, 2, 3, 5],
                   |  "f3": {
                   |    "f1": [
                   |      { "f1": false },
                   |      { "f1": true },
                   |      { "f1": 0 }
                   |    ]
                   |  }
                   |}""".stripMargin.parseJson
    val json3 = """{
                   |  "f1": 1,
                   |  "f2": [1, 2, 4],
                   |  "f3": {
                   |    "f1": [
                   |      {},
                   |      { "f1": false }
                   |    ]
                   |  }
                   |}""".stripMargin.parseJson
    val json4 = """{
                   |  "f1": 18,
                   |  "f2": [1, 8, 2, 4, 5],
                   |  "f3": {
                   |    "f1": [
                   |      { "f1": false },
                   |      { "f1": false },
                   |      { "f1": 0 }
                   |    ]
                   |  }
                   |}""".stripMargin.parseJson

    StructuralMergeStrategy(Some(json1), Some(json2), json3) should be(Some(json4))

  }

  "merging with an empty database object" should "result in the unmodified current object" in {
    val json1 = "{\"a\": [1, 2, 3, 4]}".parseJson
    val json2 = "{\"a\": [7, 4]}".parseJson

    StructuralMergeStrategy(Some(json1), None, json2) should be(Some(json2))
  }

}
