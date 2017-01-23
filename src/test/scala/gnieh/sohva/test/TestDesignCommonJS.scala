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
import org.scalatest.OptionValues._

import gnieh.diffson.sprayJson._

import spray.json._

class TestDesignCommonJS extends SohvaTestSpec with Matchers {

  "Adding a view-level CommonJS library" should "work" in {
    val design = db.design("test-design-common-js2")

    synced(design.create).views should be('empty)

    synced(design.saveView("lib", "function(doc) { emit(doc._id, 1); }", Some("_count"), Map("test" -> JsString("exports.value = 3;"))))

    val views = synced(design.getDesignDocument).value.views

    views.size should be(1)
    views.get("lib") should be('defined)
    views.get("lib").value match {
      case StandardView(map, reduce, libs) =>
        libs.get("test") should be('defined)
        libs.get("test").value should be(JsString("exports.value = 3;"))
        map should be("function(doc) { emit(doc._id, 1); }")
        reduce.value should be("_count")
      case _ =>
        fail("View should be a standard view")
    }
  }

  it should "also work if no map function is defined" in {
    val design = db.design("test-design-common-js3")

    synced(design.create).views should be('empty)

    synced(design.saveViewLib(Map("test" -> JsString("exports.value = 3;"))))

    val views = synced(design.getDesignDocument).value.views

    views.size should be(1)
    views.get("lib") should be('defined)
    views.get("lib").value match {
      case CommonJSView(libs) =>
        libs.get("test") should be('defined)
        libs.get("test").value should be(JsString("exports.value = 3;"))
      case _ =>
        fail("View should be a CommonJS view")
    }
  }

}
