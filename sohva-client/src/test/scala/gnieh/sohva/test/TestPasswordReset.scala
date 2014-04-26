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
import org.scalatest.OptionValues._

import java.util.Date

import scala.compat.Platform

import sync._

class TestPasswordReset extends SohvaTestSpec with ShouldMatchers with BeforeAndAfterEach {

  override def beforeEach() {
    session.users.add("test_user", "test_password")
  }

  override def afterEach() {
    session.users.delete("test_user")
  }

  "an administrator" should "be able to require a password reset for any user" in {
    // one hour validity
    session.users.generateResetToken("test_user", new Date(Platform.currentTime + 3600000l)) should not be(null)
  }

  it should "be able to reset the password if a valid token exists" in {
    // one hour validity
    val token = session.users.generateResetToken("test_user", new Date(Platform.currentTime + 3600000l))

    session.users.resetPassword("test_user", token, "new_password") should be(true)

    couch.startCookieSession.login("test_user", "new_password") should be(true)
  }

  it should "not be able to reset password if no token exists" in {
    session.users.resetPassword("test_user", "some token", "new_password") should be(false)
    couch.startCookieSession.login("test_user", "new_password") should be(false)
  }

  it should "not be able to reset password if the token is not valid anymore" in {
    // one hour before validity
    val token = session.users.generateResetToken("test_user", new Date(Platform.currentTime - 3600000l))

    session.users.resetPassword("test_user", token, "new_password") should be(false)

    couch.startCookieSession.login("test_user", "new_password") should be(false)
  }

  it should "not be able to reset password if a wrong token is given" in {
    // one hour validity
    val token = session.users.generateResetToken("test_user", new Date(Platform.currentTime + 3600000l))

    session.users.resetPassword("test_user", token + "wrong", "new_password") should be(false)

    couch.startCookieSession.login("test_user", "new_password") should be(false)
  }

}
