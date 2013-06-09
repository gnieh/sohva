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
package dsl
package test

import org.scalatest._

import scala.collection.mutable.Map
import scala.virtualization.lms.internal.GenerationFailedException

class TestViews extends FlatSpec with ShouldMatchers {

  val expectedMap =
    """function(x0) {
      |var x1 = x0._id;
      |var x2 = emit(x1, 1);
      |}""".stripMargin

  val expectedReduce =
    """function(x4,x5,x6) {
      |var x8 = x5;
      |var x10 = sum(x8);
      |return x10
      |}""".stripMargin

  "compiling a view with only a map" should "be correct" in {
    val view = DSL.compile(new JSView[String, Int] {
      val map: Exp[Doc => Unit] = function { doc =>
        emit(doc._id, 1)
      }
    })

    val expected = ViewDoc(expectedMap, None)

    view should be(expected)
  }

  "compiling a view with a reduce method" should "be correct" in {
    val view = DSL.compile(new JSView[String, Int] {
      val map: Exp[Doc => Unit] = function { doc =>
        emit(doc._id, 1)
      }

      override val reduce = function { (keys: Rep[List[(String, String)]], values: Rep[List[Int]], rereduce: Rep[Boolean]) =>
        sum(values)
      }

    })

    val expected = ViewDoc(expectedMap, Some(expectedReduce))

    view should be(expected)

  }

  "requiring a module" should "be correctly translated and checked" in {
    val view = DSL.compile(new JSView[String, Int] {
      val map: Exp[Doc => Unit] = function { doc =>
        val module = require[Int]("path")
        emit(doc._id, module)
      }
    })

    val expectedRequire =
      """function(x0) {
        |var x1 = require("path");
        |var x2 = x0._id;
        |var x3 = emit(x2, x1);
        |}""".stripMargin

    val expected = ViewDoc(expectedRequire, None)

    view should be(expected)
  }

  "built-in reduce function" should "correctly be typed-checked and translated" in {

    val view1 = DSL.compile(new JSView[String, Int] {
      val map: Exp[Doc => Unit] = function { doc =>
        emit(doc._id, 1)
      }

      override val reduce = _sum

    })

    val expected1 = ViewDoc(expectedMap, Some("\"_sum\""))

    view1 should be(expected1)

    val view2 = DSL.compile(new JSView[String, Int] {
      val map: Exp[Doc => Unit] = function { doc =>
        emit(doc._id, 1)
      }

      override val reduce = _count

    })

    val expected2 = ViewDoc(expectedMap, Some("\"_count\""))

    view2 should be(expected2)

    val view3 = DSL.compile(new JSView[String, Int] {
      val map: Exp[Doc => Unit] = function { doc =>
        emit(doc._id, 1)
      }

      override val reduce = _stats
    })

    val expected3 = ViewDoc(expectedMap, Some("\"_stats\""))

    view3 should be(expected3)
  }

  it should "not be possible to directly call them" in {

    val exn1 = evaluating {
      DSL.compile(new JSView[String, Int] {
        val map: Exp[Doc => Unit] = function { doc =>
          emit(doc._id, 1)
        }

        override val reduce = function {
          (keys: Rep[List[(String, String)]], values: Rep[List[Int]], rereduce: Rep[Boolean]) =>
            _sum(manifest[String], manifest[Int], implicitly[Numeric[Int]])((keys, values, rereduce))
        }
      })
    } should produce[GenerationFailedException]
    exn1.getMessage should be("built-in reduce function _sum cannot be called directly")

    val exn2 = evaluating {
      DSL.compile(new JSView[String, Int] {
        val map: Exp[Doc => Unit] = function { doc =>
          emit(doc._id, 1)
        }

        override val reduce = function {
          (keys: Rep[List[(String, String)]], values: Rep[List[Int]], rereduce: Rep[Boolean]) =>
            _count(manifest[String], manifest[Int])((keys, values, rereduce))
        }
      })
    } should produce[GenerationFailedException]
    exn2.getMessage should be("built-in reduce function _count cannot be called directly")

    val exn3 = evaluating {
      DSL.compile(new JSView[String, Int] {
        val map: Exp[Doc => Unit] = function { doc =>
          emit(doc._id, 1)
        }

        override val reduce = function {
          (keys: Rep[List[(String, String)]], values: Rep[List[Int]], rereduce: Rep[Boolean]) =>
            _stats(manifest[String], manifest[Int], implicitly[Numeric[Int]])((keys, values, rereduce))
        }
      })
    } should produce[GenerationFailedException]
    exn3.getMessage should be("built-in reduce function _stats cannot be called directly")

  }

}
