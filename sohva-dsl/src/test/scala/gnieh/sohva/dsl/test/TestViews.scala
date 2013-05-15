/*
* This file is part of the sohva project.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*couch.http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package gnieh.sohva
package dsl
package test

import org.scalatest._

class TestViews extends FlatSpec with ShouldMatchers {

  val expectedMap =
    """(function map() {
      |var x0 = function(x1) {
      |var x2 = x1._id;
      |var x3 = emit(x2, 1);
      |};
      |return x0
      |}
      |)()""".stripMargin

  val expectedReduce =
    """(function reduce() {
      |var x5 = function(x6,x7,x8) {
      |var x10 = x7;
      |var x12 = sum(x10);
      |return x12
      |};
      |return x5
      |}
      |)()""".stripMargin

  "compiling a view with only a map" should "be correct" in {
    val view = DSL.compile(new JSView[String, Int] {
      val map: Rep[Doc => Unit] = fun { doc =>
        emit(doc._id, 1)
      }
    })

    val expected = ViewDoc(expectedMap, None)

    view should be(expected)
  }

  "compiling a view with a reduce method" should "be correct" in {
    val view = DSL.compile(new JSView[String, Int] {
      val map: Rep[Doc => Unit] = fun { doc =>
        emit(doc._id, 1)
      }

      override val reduce = fun { (keys: Rep[Array[(String, String)]], values: Rep[Array[Int]], rereduce: Rep[Boolean]) =>
        sum(values)
      }

    })

    val expected = ViewDoc(expectedMap, Some(expectedReduce))

    view should be(expected)

  }

  "requiring a module" should "be correctly translated and checked" in {
    val view = DSL.compile(new JSView[String, Int] {
      val map: Rep[Doc => Unit] = fun { doc =>
        val module = require[Int]("path")
        emit(doc._id, module)
      }
    })

    val expectedRequire =
      """(function map() {
        |var x0 = function(x1) {
        |var x2 = require("path");
        |var x3 = x1._id;
        |var x4 = emit(x3, x2);
        |};
        |return x0
        |}
        |)()""".stripMargin

    val expected = ViewDoc(expectedRequire, None)

    view should be(expected)
  }

}
