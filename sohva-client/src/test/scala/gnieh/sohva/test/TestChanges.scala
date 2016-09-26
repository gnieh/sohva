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

import java.util.concurrent.atomic.AtomicBoolean
import rx.lang.scala.Subscription

class TestChanges extends SohvaTestSpec with Matchers with Waiters with BeforeAndAfterEach {

  override def beforeEach {
    if (synced(db.exists))
      synced(db.delete)
    synced(db.create)
  }

  override def afterEach {
    synced(db.delete)
  }

  def withChanges(test: ChangeStream => Any): Unit = {
    val changes = db.changes()
    try test(changes) finally changes.close()
  }

  "a registered change handler" should "be notified if a document is added to the database" ignore withChanges { changes =>

    val w = new Waiter

    val sub = changes.subscribe {
      case (id, doc) =>
        w {
          id should be("new-doc")
          val d = doc.map(_.convertTo[TestDoc])
          d should be('defined)
          d.value should be(TestDoc("new-doc", 17))
        }
        w.dismiss()
    }

    synced(db.saveDoc(TestDoc("new-doc", 17)))

    w.await(timeout(10.seconds))

    sub.unsubscribe()

  }

  it should "be notified if a document is updated in the database" ignore withChanges { changes =>

    val w = new Waiter

    val saved = synced(db.saveDoc(TestDoc("new-doc", 17)))

    val sub = changes.subscribe {
      case (id, doc) =>
        w {
          id should be("new-doc")
          val d = doc.map(_.convertTo[TestDoc])
          d should be('defined)
          d.value should be(TestDoc("new-doc", 5))
        }
        w.dismiss()
    }

    synced(db.saveDoc(saved.copy(toto = 5).withRev(saved._rev)))

    w.await(timeout(5.seconds))

    sub.unsubscribe()
  }

  it should "be notified if a document is deleted from the database" ignore withChanges { changes =>

    val w = new Waiter

    val saved = synced(db.saveDoc(TestDoc("new-doc", 17)))

    val sub = changes.subscribe {
      case (id, doc) =>
        w {
          id should be("new-doc")
          doc should not be ('defined)
        }
        w.dismiss()
    }

    synced(db.deleteDoc(saved))

    w.await(timeout(5.seconds))

    sub.unsubscribe()
  }

  it should "be notified for each change in database" ignore withChanges { changes =>

    val w = new Waiter

    val sub = changes.subscribe {
      case (id, doc) =>
        w.dismiss()
    }

    val saved = synced(db.saveDoc(TestDoc("new-doc", 17)))

    val changed = synced(db.saveDoc(saved.copy(toto = 5).withRev(saved._rev)))

    synced(db.deleteDoc(changed))

    w.await(timeout(5.seconds))

    sub.unsubscribe()
  }

  it should "not be notified if unregistered" ignore withChanges { changes =>

    val w = new Waiter
    val unsub = new AtomicBoolean(false)

    lazy val sub: Subscription = changes.subscribe {
      case (id, doc) =>
        println(f"Dismissing: ${unsub.get()}")
        w {
          withClue(f"Unsubscription status did not match expected state: ") {
            sub.isUnsubscribed should equal(unsub.get())
          }
        }
        w.dismiss()
    }
    println(f"sub: $sub")

    println("saveDoc")
    val saved = synced(db.saveDoc(TestDoc("new-doc", 17)))

    println("changeDoc")
    val changed = synced(db.saveDoc(saved.copy(toto = 5).withRev(saved._rev)))

    println(f"Is unsubscribed: ${sub.isUnsubscribed}")

    sub.unsubscribe()
    unsub.set(true)

    println(f"Is unsubscribed: ${sub.isUnsubscribed}")

    println("deleteDoc")
    synced(db.deleteDoc(changed))

    w.await(timeout(5.seconds), dismissals(2))

  }

  it should "be notified only for filtered documents if a filter was specified" ignore {

    // register a design with a filter
    val design = db.design("test")

    val ok = synced(design.saveFilter("my_filter", "function(doc, req) { if(doc.toto > 10) { return true; } else { return false; } }"))

    val w = new Waiter

    val changes = db.changes(filter = Some("test/my_filter"))

    try {
      val sub = changes.subscribe {
        case (_, doc) =>
          doc should be('defined)
          doc.value.convertTo[TestDoc].toto should be > (10)
          w.dismiss()
      }

      val d1 = synced(db.saveDoc(TestDoc("doc1", 8)))

      val d2 = synced(db.saveDoc(TestDoc("doc2", 17)))

      val d3 = synced(db.saveDoc(d1.copy(toto = 14).withRev(d1._rev)))

      val deleted = synced(db.deleteDoc("doc1"))

      deleted should be(true)

      w.await(timeout(5.seconds), dismissals(2))

    } finally changes.close()

  }

  "all registered handlers" should "be notified" ignore withChanges { changes =>

    val w = new Waiter

    val sub1 = changes.subscribe {
      case (id, doc) =>
        w.dismiss()
    }

    val sub2 = changes.subscribe {
      case (id, doc) =>
        w.dismiss()
    }

    val sub3 = changes.subscribe {
      case (id, doc) =>
        w.dismiss()
    }

    val sub4 = changes.subscribe {
      case (id, doc) =>
        w.dismiss()
    }

    synced(db.saveDoc(TestDoc("new-doc", 17)))

    w.await(timeout(15.seconds), dismissals(4))

    sub1.unsubscribe()
    sub2.unsubscribe()
    sub3.unsubscribe()
    sub4.unsubscribe()

  }

  "a client filter" should "filter out some results" ignore withChanges { changes =>

    val w = new Waiter

    val sub1 = changes.subscribe {
      case (id, doc) =>
        w.dismiss()
    }

    val filtered =
      for ((id, Some(doc)) <- changes.stream)
        yield (id, doc)

    val sub2 = filtered.subscribe { id_doc =>
      w.dismiss()
    }

    val saved = synced(db.saveDoc(TestDoc("doc", 23)))

    val ok = synced(db.deleteDoc(saved))

    ok should be(true)

    w.await(timeout(5.seconds), dismissals(3))

    sub1.unsubscribe()
    sub2.unsubscribe()

  }

}
