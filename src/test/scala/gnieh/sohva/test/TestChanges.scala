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

import akka.pattern.pipe

import akka.stream.scaladsl._

import scala.concurrent._
import scala.concurrent.duration._

class TestChanges extends AsyncSohvaTestSpec with Matchers {

  import couch.materializer
  import system.dispatcher

  "registering to a database since now" should "not return previous events" in {

    val stream = db.changes.stream(since = now)

    val (kill, res) = stream.toMat(Sink.fold(0) { case (c, _) => c + 1 })(Keep.both).run()

    kill.shutdown

    for (s <- res)
      yield s should be(0)

  }

  it should "be notified about new events" taggedAs (NoTravis) in {

    val stream = db.changes.stream(since = now, style = Some("all_docs"))

    val res = stream.takeWithin(5.seconds).toMat(Sink.fold(0) { case (c, _) => c + 1 })(Keep.right).run()

    for {
      d1 <- db.saveDoc(TestDoc("doc1", 0))
      d2 <- db.saveDoc(TestDoc("doc2", 0))
      _ <- db.deleteDoc(d1)
      _ <- db.deleteDoc(d2)
      s <- res
    } yield s should be(4)
  }

  it should "not be notified anymore when closed" in {

    val stream = db.changes.stream(since = now, style = Some("all_docs"))

    val (kill, res) = stream.toMat(Sink.fold(0) { case (c, _) => c + 1 })(Keep.both).run()

    for {
      d1 <- db.saveDoc(TestDoc("doc1", 0))
      d2 <- db.saveDoc(TestDoc("doc2", 0))
      _ <- db.deleteDoc(d1)
      () <- Future.successful(Thread.sleep(5000))
      () = kill.shutdown
      _ <- db.deleteDoc(d2)
      s <- res
    } yield s should be(3)
  }

  "several registered stream" should "all be notified" taggedAs (NoTravis) in {

    val stream = db.changes.stream(since = now, style = Some("all_docs")).takeWithin(1.seconds).toMat(Sink.fold(0) { case (c, _) => c + 1 })(Keep.right)

    val res1 = stream.run()
    val res2 = stream.run()

    for {
      d1 <- db.saveDoc(TestDoc("doc1", 0))
      d2 <- db.saveDoc(TestDoc("doc2", 0))
      _ <- db.deleteDoc(d1)
      _ <- db.deleteDoc(d2)
      s1 <- res1
      s2 <- res2
    } yield {
      s1 should be(4)
      s2 should be(4)
    }

  }

}
