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

import sync._

import org.scalatest._

import strategy._

object SohvaTests {
  val couch = new CouchClient
  val session = couch.startSession
  val db =  session.database("sohva-tests")
}

/** Code to be executed before and after each test */
abstract class SohvaTestSpec extends FlatSpec {

  val couch = SohvaTests.couch
  val session = SohvaTests.session
  val db = SohvaTests.db

}

class SohvaTestSuite extends Suites(TestBasic,
  TestBulkDocs,
  TestSecurity,
  TestSerializer,
  TestPasswordReset,
  TestBarneyStinsonStrategy,
  TestChanges) with BeforeAndAfterAll {

  override def beforeAll() {
    // login
    SohvaTests.session.login("admin", "admin")
    // create database
    SohvaTests.db.create
  }

  override def afterAll() {
    // cleanup database
    SohvaTests.db.delete
    // logout
    SohvaTests.session.logout
    // shutdown client
    SohvaTests.couch.shutdown
  }
}

case class TestDoc(_id: String, toto: Int)(val _rev: Option[String] = None)
