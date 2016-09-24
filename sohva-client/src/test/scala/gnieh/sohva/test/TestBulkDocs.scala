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

class TestBulkDocs extends SohvaTestSpec with Matchers {

  val docs: List[TestDoc] =
    (for (i <- 1 to 10)
      yield TestDoc("doc" + i, i)).toList

  "saving several documents at once" should "result in all the documents being saved in the db" in {
    val result = synced(db.saveDocs(docs))

    result.filter {
      case OkResult(_, _, _)    => false
      case ErrorResult(_, _, _) => true
    }.size should be(0)

    val saved = synced(db.getDocsById[TestDoc](docs.map(_._id)))

    saved should be(docs)

    val revisions = synced(db.getDocRevisions(docs.map(_._id)))

    revisions should have size (saved.size)
    revisions.map(_._2) should not contain ("")

  }

  "saving several document with lists at once" should "result in all the documents being saved in the db and the list elements serialized correctly" in {

    implicit val docWithListFormat = couchFormat[DocWithList]

    def strings(id: Int) =
      (for (i <- 1 to 3)
        yield "element:" + id + ":" + i).toList

    val docsString =
      (for (i <- 1 to 5)
        yield DocWithList("doc_string_list:" + i, strings(i))).toList

    val result1 = synced(db.saveDocs(docsString))

    result1.filter {
      case OkResult(_, _, _)    => false
      case ErrorResult(_, _, _) => true
    }.size should be(0)

    val saved1 = synced(db.getDocsById[DocWithList](docsString.map(_._id)))

    saved1 should be(docsString)

  }

  "deleting several documents at once" should "result in all documents being deleted in the db" in {

    synced(db.saveDocs(docs))

    val ids = docs.map(_._id)

    val saved = synced(db.getDocsById[TestDoc](ids))

    saved should be(docs)

    val deleted = synced(db.deleteDocs(ids))

    deleted.filter {
      case OkResult(_, _, _)    => false
      case ErrorResult(_, _, _) => true
    }.size should be(0)

    synced(db.getDocsById[TestDoc](docs.map(_._id))) should be(Nil)

  }

  "deleting several documents at once" should "delete only documents for which an id was provided" in {

    synced(db.saveDocs(docs))

    val ids = docs.map(_._id)

    val saved = synced(db.getDocsById[TestDoc](ids))

    saved should be(docs)

    val deleted = synced(db.deleteDocs(ids.take(5)))

    deleted.filter {
      case OkResult(_, _, _)    => false
      case ErrorResult(_, _, _) => true
    }.size should be(0)

    synced(db.getDocsById[TestDoc](docs.map(_._id))) should be(docs.drop(5))

  }

}

case class DocWithList(_id: String, list: List[String]) extends IdRev
