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
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.OptionValues._

import sync._

class TestCopy extends SohvaTestSpec with ShouldMatchers {

  "the target document" should "be created if it does not exist yet" in {
    val doc = TestDoc2("my-doc", 4)
    val saved = db.saveDoc(doc)

    val targetUnknown = db.getDocById[TestDoc2]("my-doc-copy")

    targetUnknown should not be('defined)

    val ok = db.copy("my-doc", "my-doc-copy")

    ok should be(true)

    val target = db.getDocById[TestDoc2]("my-doc-copy")

    target should be('defined)
    target.value.toto should be(4)

  }

  "the target document" should "be update if it already exists" in {

    val saved = db.getDocById[TestDoc2]("my-doc")
    saved should be('defined)

    val target = TestDoc2("my-doc-target", 5432)

    val targetSaved = db.saveDoc(target)

    targetSaved.toto should be(5432)
    targetSaved._rev should be('defined)

    val ok = db.copy("my-doc", "my-doc-target", targetRev = targetSaved._rev)

    ok should be(true)

    val targetUpdated = db.getDocById[TestDoc2]("my-doc-target")

    targetUpdated.value.toto should be(4)
    targetUpdated.value._rev should not be(targetSaved._rev)

  }

}

