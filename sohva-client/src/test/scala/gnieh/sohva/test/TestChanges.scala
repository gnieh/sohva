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

object TestChanges extends SohvaTestSpec with ShouldMatchers with AsyncAssertions with BeforeAndAfterEach {

  implicit val formats = DefaultFormats

  override def beforeEach {
    db.delete
    db.create
  }

  override def afterEach {
    db.delete
  }

  "a registered change handler" should "be notified if a document is added to the database" in {

    val w = new Waiter

    val changes = db.changes()

    val hid = changes.foreach { (id, doc) =>
      w {
        id should be("new-doc")
        val d = doc.map(_.extract[TestDoc])
        d should be('defined)
        d.value should be(TestDoc("new-doc", 17)())
      }
      w.dismiss()
    }

    db.saveDoc(TestDoc("new-doc", 17)())

    w.await(timeout(1 second))

    changes.unregister(hid)

  }

  it should "be notified if a document is updated in the database" in {

    val w = new Waiter

    val saved = db.saveDoc(TestDoc("new-doc", 17)())

    val changes = db.changes()

    saved should be('defined)

    val hid = changes.foreach { (id, doc) =>
      w {
        id should be("new-doc")
        val d = doc.map(_.extract[TestDoc])
        d should be('defined)
        d.value should be(TestDoc("new-doc", 5)())
      }
      w.dismiss()
    }

    saved map { saved =>
      db.saveDoc(saved.copy(toto = 5)(saved._rev))
    }

    w.await(timeout(1 second))

    changes.unregister(hid)
  }

  it should "be notified if a document is deleted from the database" in {

    val w = new Waiter

    val saved = db.saveDoc(TestDoc("new-doc", 17)())

    saved should be('defined)

    val changes = db.changes()

    val hid = changes.foreach { (id, doc) =>
      w {
        id should be("new-doc")
        doc should not be('defined)
      }
      w.dismiss()
    }

    saved map { saved =>
      db.deleteDoc(saved)
    }

    w.await(timeout(1 second))

    changes.unregister(hid)
  }

  it should "be notified for each change in database" in {

    val w = new Waiter

    val changes = db.changes()

    val hid = changes.foreach { (id, doc) =>
      w.dismiss()
    }

    val saved = db.saveDoc(TestDoc("new-doc", 17)())

    saved should be('defined)

    val changed = saved flatMap { saved =>
      db.saveDoc(saved.copy(toto = 5)(saved._rev))
    }

    changed should be('defined)

    changed map { saved =>
      db.deleteDoc(saved)
    }

    w.await(timeout(3 seconds), dismissals(3))

    changes.unregister(hid)
  }

  it should "not be notified if unregistered" in {

    val w = new Waiter

    val changes = db.changes()

    val hid = changes.foreach { (id, doc) =>
      w.dismiss()
    }

    val saved = db.saveDoc(TestDoc("new-doc", 17)())

    saved should be('defined)

    val changed = saved flatMap { saved =>
      db.saveDoc(saved.copy(toto = 5)(saved._rev))
    }

    changed should be('defined)

    changes.unregister(hid)

    changed map { saved =>
      db.deleteDoc(saved)
    }

    w.await(timeout(3 seconds), dismissals(2))

  }

  "all registered handlers" should "be notified" in {

    val w = new Waiter

    val changes = db.changes()

    val hid1 = changes.foreach { (id, doc) =>
      w.dismiss()
    }

    val hid2 = changes.foreach { (id, doc) =>
      w.dismiss()
    }

    val hid3 = changes.foreach { (id, doc) =>
      w.dismiss()
    }

    val hid4 = changes.foreach { (id, doc) =>
      w.dismiss()
    }

    db.saveDoc(TestDoc("new-doc", 17)())

    w.await(timeout(3 seconds), dismissals(4))

    changes.unregister(hid1)
    changes.unregister(hid2)
    changes.unregister(hid3)
    changes.unregister(hid4)

  }

}
