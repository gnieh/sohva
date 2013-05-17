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

object TestBulkDocs extends SohvaTestSpec with ShouldMatchers {

  val docs: List[TestDoc] =
    (for(i <- 1 to 10)
      yield TestDoc("doc" + i, i)()).toList

  "saving several documents at once" should "result in all the documents being saved in the db" in {
    val result = db.saveDocs(docs)

    result.filter {
      case OkResult(_, _, _) => false
      case ErrorResult(_, _, _) => true
    }.size should be(0)

    val saved = db.getDocsById[TestDoc](docs.map(_._id))

    saved should be(docs)

  }

  "deleting several documents at once" should "result in all documents being deleted in the db" in {

    db.saveDocs(docs)

    val ids = docs.map(_._id)

    val saved = db.getDocsById[TestDoc](ids)

    saved should be(docs)

    val deleted = db.deleteDocs(ids)

    deleted.filter {
      case OkResult(_, _, _) => false
      case ErrorResult(_, _, _) => true
    }.size should be(0)

    db.getDocsById[TestDoc](docs.map(_._id)) should be(Nil)

  }

}
