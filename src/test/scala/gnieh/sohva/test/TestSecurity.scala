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

/** @author satabin
 *
 */
class TestSecurity extends SohvaTestSpec with Matchers with BeforeAndAfterEach {

  var secDb: Database = couch.database("sohva_test_security")
  var adminSecDb: Database = _

  val secDoc1 = SecurityDoc(admins = SecurityList(names = List("secUser2")))
  val secDoc2 = SecurityDoc(admins = SecurityList(roles = List("role1")))
  val secDoc3 = SecurityDoc(members = SecurityList(roles = List("role2")))
  val secDoc4 = SecurityDoc(members = SecurityList(names = List("secUser1")))

  override def beforeEach() {
    // create the database for tests
    adminSecDb = session.database("sohva_test_security")
    synced(adminSecDb.create)
    // add the test users
    synced(session.users.add("secUser1", "secUser1", List("role1", "role2")))
    synced(session.users.add("secUser2", "secUser2", List("role2")))
  }

  override def afterEach() {
    // delete the database
    synced(adminSecDb.delete)
    // delete the test user
    synced(session.users.delete("secUser1"))
    synced(session.users.delete("secUser2"))
  }

  "a database with no security document" should "be readable by everybody" in {

    synced(secDb.info) should be('defined)

  }

  it should "be writtable to anybody" in {

    val saved = synced(secDb.saveDoc(TestDoc("some_doc", 17)))

    saved should have(
      '_id("some_doc"),
      'toto(17))

  }

  "server admin" should "be able to add a security document" in {

    synced(adminSecDb.saveSecurityDoc(secDoc1)) should be(true)

  }

  "database admin" should "be able to update the security document" in {
    synced(adminSecDb.saveSecurityDoc(secDoc1)) should be(true)

    val session2 = couch.startBasicSession("secUser2", "secUser2")

    synced(session2.database("sohva_test_security").saveSecurityDoc(secDoc2)) should be(true)
  }

  "anonymous user" should "not be able to read a database with a members list" in {

    synced(secDb.saveDoc(TestDoc("some_doc", 13)))
    synced(adminSecDb.saveSecurityDoc(secDoc3)) should be(true)

    val thrown = the[SohvaException] thrownBy {
      synced(secDb.getDocById[TestDoc]("some_doc"))
    }

    val ce = CauseMatchers.findExpectedExceptionRecursively[CouchException](thrown)
    withClue("CouchException should be present in the stack trace: ") { ce should not be ('empty) }
    ce.get.status should be(401)

  }

  it should "not be able to write into a database with a member list" in {

    synced(adminSecDb.saveSecurityDoc(secDoc3)) should be(true)

    val thrown = the[SohvaException] thrownBy {
      synced(secDb.saveDoc(TestDoc("some_doc", 13)))
    }

    val ce = CauseMatchers.findExpectedExceptionRecursively[CouchException](thrown)
    withClue("CouchException should be present in the stack trace: ") { ce should not be ('empty) }
    ce.get.status should be(401)

  }

}
