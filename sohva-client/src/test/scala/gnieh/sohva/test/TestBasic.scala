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

import gnieh.diffson._

import spray.json._

class TestBasic extends SohvaTestSpec with Matchers {

  "an unknown document" should "not be retrieved" in {
    synced(db.getDocById[TestDoc]("unknown-doc")) should be(None)
  }

  it should "be added correctly and can then be retrieved" in {
    val doc = TestDoc2("new-doc", 4)
    val saved = synced(db.saveDoc(doc))

    saved should have(
      '_id("new-doc"),
      'toto(4))

    synced(db.getDocById[TestDoc2]("new-doc")) should be(Some(saved))
  }

  "an existing document" should "have a revision" in {
    synced(db.getDocById[TestDoc2]("new-doc")) match {
      case Some(doc) => doc._rev should not be (None)
      case None      => fail("The document with id `new-doc` should exist")
    }
  }

  it should "not be saved if we have an outdated version" in {
    synced(db.getDocById[TestDoc2]("new-doc")) match {
      case Some(doc) =>
        val thrown = the[SohvaException] thrownBy {
          synced(db.saveDoc(doc.copy(toto = 1).withRev(Some("0-0"))))
        }

        val cause = CauseMatchers.findExpectedExceptionRecursively[ConflictException](thrown)

      case None =>
        fail("The document with id `new-doc` should exist")
    }
  }

  it should "be saved if we have the last version and then get a new revision" in {
    synced(db.getDocById[TestDoc2]("new-doc")) match {
      case Some(doc) =>
        val newest = synced(db.saveDoc(doc.copy(toto = 1).withRev(doc._rev)))
        newest.toto should be(1)
        newest._rev.value should not be (doc._rev.get)
      case None =>
        fail("The document with id `new-doc` should exist")
    }
  }

  it should "be patchable" in {
    synced(db.getDocRevision("new-doc")) match {
      case Some(rev) =>
        val patch = JsonPatch.parse("""[{ "op": "replace", "path": "/toto", "value": 453 }]""")
        val newest = synced(db.patchDoc[TestDoc2]("new-doc", rev, patch))
        newest.toto should be(453)
        newest._rev.value should not be (rev)
      case None =>
        fail("The document with id `new-doc` should exist")
    }
  }

  case class StringDoc(_id: String, value: String) extends IdRev

  implicit val stringDocFormat = couchFormat[StringDoc]

  "a document" should "be sent encoded in UTF-8" in {

    val doc = StringDoc("utf8-doc", "éßèüäöàç€ẞÐẞŁª€ªÐŁ")

    val saved = synced(db.saveDoc(doc))

    saved._rev should be('defined)
    saved.value should be(doc.value)

  }

  it should "be created into the database with a new identifier if none is given" in {

    val doc = NoCouchDoc(value = 3)

    implicit val noCouchDoc = jsonFormat1(NoCouchDoc)

    synced(db.createDoc(doc)) match {
      case OkResult(true, id, rev) =>

        val newId = id.value
        val saved = synced(db.getRawDocById(newId))
        saved.value match {
          case JsObject(fields) =>
            fields.get("value").value should be(JsNumber(3))
            fields.get("_rev").value should be(JsString(rev.value))
          case _ =>
            fail("An object was expected")
        }

      case _ =>
        fail("The document should have been saved")
    }

  }

}

case class NoCouchDoc(value: Int)
