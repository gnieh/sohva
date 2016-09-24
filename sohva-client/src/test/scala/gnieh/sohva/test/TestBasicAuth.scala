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

class TestBasicAuth extends SohvaTestSpec with BeforeAndAfterAll {

  val username = "test-basic"
  val password = "test-basic"

  override def beforeAll(): Unit = try {
    super.beforeAll()
  } finally {
    // add a user with OAuth data
    val userDb = session.database("_users")
    synced(userDb.saveDoc(
      CouchUser(
        username,
        password,
        List()
      )
    ))
  }

  override def afterAll(): Unit = try {
    val userDb = session.database("_users")
    synced(userDb.deleteDoc("org.couchdb.user:" + username))
  } finally {
    super.afterAll()
  }

  "A basic session" should "give access to user document" in {

    val basicSession = couch.startBasicSession(username, password)

    val basicUser = synced(basicSession.currentUser)

    basicUser should be('defined)

    val basicUserDb = basicSession.database("_users")
    val basicRev = synced(basicUserDb.getDocRevision("org.couchdb.user:" + username))

    basicRev should be('defined)

  }

}
