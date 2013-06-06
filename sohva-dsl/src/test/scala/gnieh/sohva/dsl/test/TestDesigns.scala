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

import scala.virtualization.lms.internal.GenerationFailedException

class TestDesigns extends FlatSpec with ShouldMatchers {

  val expectedDesign = DesignDoc("test-design", "javascript",
    views = Map(
      "view1" -> ViewDoc(
        """function(x0) {
        |var x1 = x0._id;
        |var x2 = emit(x1, 1);
        |}""".stripMargin,
        None
      ),
      "view2" -> ViewDoc(
        """function(x0) {
          |var x1 = x0._id;
          |var x2 = emit(null, x1);
          |}""".stripMargin,
          None
      )
    ),
    validate_doc_update = Some(
      """function(x0,x1,x2) {
        |var x3 = x0;
        |var x6 = x3._id;
        |var x4 = x1;
        |var x7 = x4._id;
        |var x8 = x6==x7;
        |return x8
        |}""".stripMargin
    ),
    shows = Map(
      "show1" -> """function(x10,x11) {
                   |var x14 = {'code' : 200,'body' : "test"};
                   |return x14
                   |}""".stripMargin
    ),
    updates = Map(
      "update1" -> """function(x15,x16) {
                     |var x17 = x15;
                     |var x19 = {'body' : "plop"};
                     |var x20 = [x17,x19];
                     |return x20
                     |}""".stripMargin
    )
  )

  "compiling a desing" should "be correct" in {
    val design = DSL.compile(new JSDesign {

      val _id = "test-design"

      view("view1")(new JSView[String, Int] {
        val map = function { (doc: Rep[Doc]) =>
          emit(doc._id, 1)
        }
      })

      view("view2")(new JSView[String, String] {
        val map = function { (doc: Rep[Doc]) =>
          emit(null, doc._id)
        }
      })

    override val validate_doc_update =
      function { (oldDoc: Rep[Doc], newDoc: Rep[Doc], ctx: Rep[UserCtx]) =>
        oldDoc._id == newDoc._id
      }

    show("show1")(function { (doc: Rep[Doc], req: Rep[Request]) =>
      new Record {
        val code = 200
        val body = "test"
      }
    })

    update("update1")(function { (doc: Rep[Doc], req: Rep[Request]) =>
      (doc, new Record{val body = "plop"})
    })

    })

    design should be(expectedDesign)

  }

}
