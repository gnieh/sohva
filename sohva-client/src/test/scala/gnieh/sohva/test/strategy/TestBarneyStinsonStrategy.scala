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

object TestBarneyStinsonStrategy extends SohvaTestSpec with ShouldMatchers with BeforeAndAfterEach {

  override val db = couch.database("sohva-tests", 1)

  override def afterEach {
    db.deleteDoc("conflicting_doc")
  }

  "The new document" should "overwrite the old document if a conflict occurs" in {
    val baseDoc = TestDoc("conflicting_doc", 3)()
    val firstSaved = db.saveDoc(baseDoc)

    firstSaved should have(
      '_id("conflicting_doc"),
      'toto(3))

    val conflictDoc = TestDoc("conflicting_doc", 17)(firstSaved._rev)

    val secondSaved = db.saveDoc(conflictDoc)

    secondSaved should have(
      '_id("conflicting_doc"),
      'toto(17))

    // try to save a new document based on the base revision (not the last one)
    val newDoc = TestDoc("conflicting_doc", 42)(firstSaved._rev)

    val thirdSaved = db.saveDoc(newDoc)

    thirdSaved should have(
      '_id("conflicting_doc"),
      'toto(42))

  }

  it should "be saved if the document was deleted inbetween" in {

    val baseDoc = TestDoc("conflicting_doc", 3)()
    val firstSaved = db.saveDoc(baseDoc)

    firstSaved should have(
      '_id("conflicting_doc"),
      'toto(3))

    // delete the document
    db.deleteDoc("conflicting_doc") should be(true)

    val newDoc = TestDoc("conflicting_doc", 42)(firstSaved._rev)

    val secondSaved = db.saveDoc(newDoc)

    secondSaved should have(
      '_id("conflicting_doc"),
      'toto(42))

  }

  it should "be saved if we think it is a new document but it is not" in {

    val baseDoc = TestDoc("conflicting_doc", 3)()
    val firstSaved = db.saveDoc(baseDoc)

    firstSaved should have(
      '_id("conflicting_doc"),
      'toto(3))

    val newDoc = TestDoc("conflicting_doc", 42)()

    val secondSaved = db.saveDoc(newDoc)

    secondSaved should have(
      '_id("conflicting_doc"),
      'toto(42))

  }

  it should "overwrite any previously saved document" in {

    val baseDoc = ComplexDoc("conflicting_doc", "test", 3)
    val firstSaved = db.saveDoc(baseDoc)

    firstSaved should have(
      '_id("conflicting_doc"),
      'f1("test"),
      'f2(3))

    val newDoc = TestDoc("conflicting_doc", 42)()

    val secondSaved = db.saveDoc(newDoc)

    secondSaved should have(
      '_id("conflicting_doc"),
      'toto(42))

  }

}

case class ComplexDoc(_id: String, f1: String, f2: Int, _rev: Option[String] = None)
