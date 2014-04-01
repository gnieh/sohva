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

class TestOAuth extends SohvaTestSpec with BeforeAndAfterAll {

  val consumerKey1 = "consumer1"
  val consumerSecret1 = "comsumer1_secret"

  val token1 = "token1"
  val secret1 = "token1_secret"

  val user = "oauth_user"

  override def beforeAll(): Unit = try {
    super.beforeAll()
  } finally {
    // add a user with OAuth data
    val userDb = session.database("_users")
    userDb.saveDoc(
      CouchUser(
        user,
        user,
        List(),
        Some(
          OAuthData(
            Map(consumerKey1 -> consumerSecret1),
            Map(token1 -> secret1)
          )
        )
      )
    )
  }

  override def afterAll(): Unit = try {
    val userDb = session.database("_users")
    userDb.deleteDoc("org.couchdb.user:" + user)
  } finally {
    super.afterAll()
  }

  "An OAuth session" should "give access to same rights as the cookie authenticated user" in {

    val oauthSession = couch.startOAuthSession(consumerKey1, consumerSecret1, token1, secret1)
    val cookieSession = couch.startCookieSession

    val oauthUser = oauthSession.currentUser

    val anonUser = cookieSession.currentUser

    oauthUser should not be(anonUser)

    val loggedin = cookieSession.login(user, user)

    loggedin should be(true)

    val cookieUser = cookieSession.currentUser

    oauthUser should be(cookieUser)

    val cookieUserDb = cookieSession.database("_users")
    val cookieRev = cookieUserDb.getDocRevision("org.couchdb.user:" + user)

    cookieRev should be('defined)

    val oauthUserDb = oauthSession.database("_users")
    val oauthRev = oauthUserDb.getDocRevision("org.couchdb.user:" + user)

    oauthRev should be('defined)
    oauthRev should be(cookieRev)


  }

}

