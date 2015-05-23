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

import scala.language.higherKinds

/** An instance of a Couch session that allows the user to login and
 *  send request identified with the login credentials.
 *  This performs a cookie based authentication against the couchdb server.
 *  The couchdb client instance retrieved for this session will send request
 *  authenticated by the user that logged in in this session.
 *
 *  @author Lucas Satabin
 *
 */
trait CookieSession[Result[_]] extends CouchDB[Result] with Session[Result] {

  /** Performs a login and returns true if login succeeded.
   *  from now on, if login succeeded the couch instance is identified and
   *  all requests will be done with the given credentials.
   *  This performs a cookie authentication.
   */
  def login(name: String, password: String): Result[Boolean]

  /** Logs the session out */
  def logout: Result[Boolean]

  /** Returns the user associated to the current session, if any */
  def currentUser: Result[Option[UserInfo]]

}

/** Result of the authentication request */
case class AuthResult(ok: Boolean, userCtx: UserCtx, info: Option[AuthInfo])

/** The user context giving his name and roles */
case class UserCtx(name: Option[String], roles: List[String])

/** Authentication information indicating the authentication database,
 *  the handler used and the authentication method
 */
case class AuthInfo(authentication_db: String,
  authentication_handlers: List[String],
  authenticated: Option[String])

/** A couchdb user has a name, a password and a lit of roles. */
case class UserInfo(val name: String,
  val roles: List[String])

case class CouchUser(
    val name: String,
    val password: String,
    val roles: List[String],
    val oauth: Option[OAuthData] = None,
    val `type`: String = "user") extends IdRev {

  val _id = "org.couchdb.user:" + name

}

case class OAuthData(val consumer_keys: Map[String, String], val tokens: Map[String, String])

