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

class TestViews extends SohvaTestSpec with ShouldMatchers with BeforeAndAfterEach {

  val docs: List[TestDoc] =
    (for {
      i <- 1 to 10
      j <- i to 10
    } yield TestDoc("view_doc" + i + j, j)()).toList

  override def beforeEach(): Unit = {
    try {
      super.beforeEach()
    } finally {
      db.saveDocs(docs)
    }
  }

  override def afterEach(): Unit = {
    try {
      super.afterEach()
    } finally {
      db.deleteDocs(docs.map(_._id))
    }
  }

  "saving a new view" should "result in a new design document being added with the given view" in {

    val design = db.design("test_design")

    design.saveView("test_view", "function(doc) { if(doc._id.indexOf('view_doc') == 0) emit(doc._id, null); }")

    val updated = design.getDesignDocument
    updated should be('defined)

    updated.value.views.contains("test_view") should be(true)

  }

  "querying a view with no parameter" should "result in all emitted key/values to be returned" in {

    val view = db.design("test_design").view("test_view")

    val viewResult = view.query[String, Null, TestDoc]()

    viewResult.total_rows should be(docs.size)
    viewResult.offset should be(0)
    viewResult.rows should be(docs.map(doc => Row(Some(doc._id), doc._id, null)).sortBy { case Row (Some(id), _, _, _) => id })

  }

  "querying a view with start and end key parameters" should "result in filtered emitted key/values to be returned" in {

    val view = db.design("test_design").view("test_view")

    val viewResult = view.query[String, Null, TestDoc](startkey = Some("view_doc7"), endkey = Some("view_doc9"))

    val filtered =
      for {
        i <- 7 to 8
        j <- i to 10
      } yield Row(Some("view_doc" + i + j), "view_doc" + i + j, null)

    viewResult.total_rows should be(docs.size)
    viewResult.offset should be(46)
    viewResult.rows should be(filtered.sortBy { case Row (id, _, _, _) => id })

  }

  "querying a built-in view" should "be similar to querying user defined view" in {

    val all = db._all_docs(startkey = Some("view_doc"), endkey = Some("view_docZ"))

    all.size should be(docs.size)
    all should be(docs.map(doc => doc._id).sorted)

  }

  "querying a view with more complex key" should "also be possible" in {

    val design = db.design("test_design")

    val saved =
      design.saveView("test_complex_key", "function(doc) { if(doc._id.indexOf('view_doc') == 0) emit([ doc.toto - 1, doc.toto] , null); }")

    val view = design.view("test_complex_key")

    val filtered =
      for {
        i <- 1 to 10
        j <- math.max(5, i) to 10
      } yield Row(Some("view_doc" + i + j), List(j - 1, j), null)

    val viewResult = view.query[List[Int], Null, TestDoc](startkey = Some(List(3, 5)))

    viewResult.total_rows should be(docs.size)
    viewResult.offset should be(10)
    val viewSet = viewResult.rows.toSet
    val filteredSet = filtered.toSet

    viewSet should be(filteredSet)

  }

  "reduced views" should "be queryable as well" in {

    case class TestReduce(_id: String, name: String, count: Int) extends IdRev

    db.saveDoc(TestReduce("A1", "A", 1))
    db.saveDoc(TestReduce("A2", "A", 2))
    db.saveDoc(TestReduce("A3", "A", 3))

    db.saveDoc(TestReduce("B4", "B", 4))
    db.saveDoc(TestReduce("B5", "B", 5))

    val design = db.design("reduce_design")
    design.saveView("counts", "function(doc) { if(doc.name && doc.count) emit(doc.name, doc.count); }", Some("_sum"))
    val view = design.view("counts")

    val result = view.query[String, Int, TestReduce](group_level = 2)

    result.rows should be(List(Row(None, "A", 6), Row(None, "B", 9)))

  }

}

