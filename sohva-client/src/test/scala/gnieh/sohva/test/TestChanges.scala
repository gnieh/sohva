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
import OptionValues._
import concurrent._
import time.SpanSugar._

import sync._

import net.liftweb.json.DefaultFormats

class TestChanges extends SohvaTestSpec with ShouldMatchers with AsyncAssertions with BeforeAndAfterEach {

  implicit val formats = DefaultFormats

  override def beforeEach {
    if(db.exists)
      db.delete
    db.create
  }

  override def afterEach {
    db.delete
  }

  def withChanges(test: ChangeStream => Any): Unit = {
    val changes = db.changes()
    try test(changes) finally changes.close()
  }

  "a registered change handler" should "be notified if a document is added to the database" in withChanges { changes =>

    val w = new Waiter

    val sub = changes.subscribe { case (id, doc) =>
      w {
        id should be("new-doc")
        val d = doc.map(_.extract[TestDoc])
        d should be('defined)
        d.value should be(TestDoc("new-doc", 17)())
      }
      w.dismiss()
    }

    db.saveDoc(TestDoc("new-doc", 17)())

    w.await(timeout(10.seconds))

    sub.unsubscribe()

  }

  it should "be notified if a document is updated in the database" in withChanges { changes =>

    val w = new Waiter

    val saved = db.saveDoc(TestDoc("new-doc", 17)())

    val sub = changes.subscribe { case (id, doc) =>
      w {
        id should be("new-doc")
        val d = doc.map(_.extract[TestDoc])
        d should be('defined)
        d.value should be(TestDoc("new-doc", 5)())
      }
      w.dismiss()
    }

    db.saveDoc(saved.copy(toto = 5)(saved._rev))

    w.await(timeout(5.seconds))

    sub.unsubscribe()
  }

  it should "be notified if a document is deleted from the database" in withChanges { changes =>

    val w = new Waiter

    val saved = db.saveDoc(TestDoc("new-doc", 17)())

    val sub = changes.subscribe { case (id, doc) =>
      w {
        id should be("new-doc")
        doc should not be('defined)
      }
      w.dismiss()
    }

    db.deleteDoc(saved)

    w.await(timeout(5.seconds))

    sub.unsubscribe()
  }

  it should "be notified for each change in database" in withChanges { changes =>

    val w = new Waiter

    val sub = changes.subscribe { case (id, doc) =>
      w.dismiss()
    }

    val saved = db.saveDoc(TestDoc("new-doc", 17)())

    val changed = db.saveDoc(saved.copy(toto = 5)(saved._rev))

    db.deleteDoc(changed)

    w.await(timeout(5.seconds), dismissals(3))

    sub.unsubscribe()
  }

  it should "not be notified if unregistered" in withChanges { changes =>

    val w = new Waiter

    val sub = changes.subscribe { case (id, doc) =>
      w.dismiss()
    }

    val saved = db.saveDoc(TestDoc("new-doc", 17)())

    val changed = db.saveDoc(saved.copy(toto = 5)(saved._rev))

    sub.unsubscribe()

    db.deleteDoc(changed)

    w.await(timeout(5.seconds), dismissals(2))

  }

  it should "be notified only for filtered documents if a filter was specified" in {

    // register a design with a filter
    val design = db.design("test")

    val ok = design.saveFilter("my_filter", "function(doc, req) { if(doc.toto > 10) { return true; } else { return false; } }")

    val w = new Waiter

    val changes = db.changes(filter = Some("test/my_filter"))

    try {
      val sub = changes.subscribe { case (_, doc) =>
        doc should be('defined)
        doc.value.extract[TestDoc].toto should be > (10)
        w.dismiss()
      }

      val d1 = db.saveDoc(TestDoc("doc1", 8)())

      val d2 = db.saveDoc(TestDoc("doc2", 17)())

      val d3 = db.saveDoc(d1.copy(toto = 14)(_rev = d1._rev))

      val deleted = db.deleteDoc("doc1")

      deleted should be(true)

      w.await(timeout(5.seconds), dismissals(2))

    } finally changes.close()

  }

  "all registered handlers" should "be notified" in withChanges { changes =>

    val w = new Waiter

    val sub1 = changes.subscribe { case (id, doc) =>
      w.dismiss()
    }

    val sub2 = changes.subscribe { case (id, doc) =>
      w.dismiss()
    }

    val sub3 = changes.subscribe { case (id, doc) =>
      w.dismiss()
    }

    val sub4 = changes.subscribe { case (id, doc) =>
      w.dismiss()
    }

    db.saveDoc(TestDoc("new-doc", 17)())

    w.await(timeout(10.seconds), dismissals(4))

    sub1.unsubscribe()
    sub2.unsubscribe()
    sub3.unsubscribe()
    sub4.unsubscribe()

  }

  "a client filter" should "filter out some results" in withChanges { changes =>

    val w = new Waiter

    val sub1 = changes.subscribe { case (id, doc) =>
      w.dismiss()
    }

    val filtered =
      for((id, Some(doc)) <- changes.stream)
        yield (id, doc)

    val sub2 = filtered.subscribe { id_doc =>
      w.dismiss()
    }

    val saved = db.saveDoc(TestDoc("doc", 23)())

    val ok = db.deleteDoc(saved)

    ok should be(true)

    w.await(timeout(5.seconds), dismissals(3))

    sub1.unsubscribe()
    sub2.unsubscribe()

  }

}

