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

class TestBasic extends SohvaTestSuite with ShouldMatchers {

  "an unknown document" should "not be retrieved" in {
    db.getDocById[TestDoc]("unknown-doc") should be(None)
  }

  it should "be added correctly and can then be retrieved" in {
    val doc = TestDoc("new-doc", 4)()
    val saved = db.saveDoc(doc)

    saved.value should have(
      '_id("new-doc"),
      'toto(4))
    db.getDocById[TestDoc]("new-doc") should be(saved)
  }

  "an existing document" should "have a revision" in {
    db.getDocById[TestDoc]("new-doc") match {
      case Some(doc) => doc._rev should not be (None)
      case None      => fail("The document with id `new-doc` should exist")
    }
  }

  it should "not be saved if we have an outdated version" in {
    db.getDocById[TestDoc]("new-doc") match {
      case Some(doc) =>
        evaluating {
          db.saveDoc(doc.copy(toto = 1)(Some("0-0")))
        } should produce[ConflictException]
      case None =>
        fail("The document with id `new-doc` should exist")
    }
  }

  it should "be saved if we have the last version and then get a new revision" in {
    db.getDocById[TestDoc]("new-doc") match {
      case Some(doc) =>
        val newest = db.saveDoc(doc.copy(toto = 1)(doc._rev))
        newest should be('defined)
        newest.map(_.toto).value should be(1)
        newest.map(_._rev).value should not be (doc._rev)
      case None =>
        fail("The document with id `new-doc` should exist")
    }
  }

}