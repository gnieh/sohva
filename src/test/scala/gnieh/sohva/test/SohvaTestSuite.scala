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

import gnieh.sohva.strategy._

import org.scalatest._

import akka.actor.ActorSystem
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import spray.json._

trait AbstractSohvaTestSpec extends BeforeAndAfterAll with Matchers with SohvaProtocol {
  this: Suite =>

  val retry: Int
  val strategy: Strategy

  implicit val system = ActorSystem()
  implicit val timeout = Timeout(5.seconds)

  implicit val testDocFormat = couchFormat[TestDoc]
  implicit val testDoc2Format = couchFormat[TestDoc2]

  val couch = new CouchClient
  val session = couch.startBasicSession("admin", "admin")
  val db = session.database("sohva-tests", credit = retry, strategy = strategy)

  override def beforeAll() {
    // create database
    synced(db.create)
  }

  override def afterAll() {
    // cleanup database
    synced(db.delete)
    system.terminate()
  }

}

abstract class SohvaTestSpec(val retry: Int = 0, val strategy: Strategy = BarneyStinsonStrategy) extends FlatSpec with AbstractSohvaTestSpec

abstract class AsyncSohvaTestSpec(val retry: Int = 0, val strategy: Strategy = BarneyStinsonStrategy) extends AsyncFlatSpec with AbstractSohvaTestSpec

case class TestDoc(_id: String, toto: Int) extends IdRev
case class TestDoc2(_id: String, toto: Int) extends IdRev
