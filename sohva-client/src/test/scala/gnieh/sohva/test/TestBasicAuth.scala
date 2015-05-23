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

import sync._

class TestBasicAuth extends SohvaTestSpec with BeforeAndAfterAll {

  val username = "test-basic"
  val password = "test-basic"

  override def beforeAll(): Unit = try {
    super.beforeAll()
  } finally {
    // add a user with OAuth data
    val userDb = session.database("_users")
    userDb.saveDoc(
      CouchUser(
        username,
        password,
        List()
      )
    )
  }

  override def afterAll(): Unit = try {
    val userDb = session.database("_users")
    userDb.deleteDoc("org.couchdb.user:" + username)
  } finally {
    super.afterAll()
  }

  "A basic session" should "give access to same rights as the cookie authenticated user" in {

    val basicSession = couch.startBasicSession(username, password)
    val cookieSession = couch.startCookieSession

    val basicUser = basicSession.currentUser

    val anonUser = cookieSession.currentUser

    basicUser should not be (anonUser)

    val loggedin = cookieSession.login(username, password)

    loggedin should be(true)

    val cookieUser = cookieSession.currentUser

    basicUser should be(cookieUser)

    val cookieUserDb = cookieSession.database("_users")
    val cookieRev = cookieUserDb.getDocRevision("org.couchdb.user:" + username)

    cookieRev should be('defined)

    val basicUserDb = basicSession.database("_users")
    val basicRev = basicUserDb.getDocRevision("org.couchdb.user:" + username)

    basicRev should be('defined)
    basicRev should be(cookieRev)

  }

}
