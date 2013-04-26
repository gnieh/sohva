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

import dispatch._
import Defaults._

import com.ning.http.client.Response

/** An instance of a Couch session, that allows the user to login and
 *  send request identified with the login credentials.
 *  This performs a cookie based authentication against the couchdb server.
 *  The couchdb client instance retrieved for this session will send request
 *  authenticated by the user that logged in in this session.
 *
 *  @author Lucas Satabin
 *
 */
class CouchSession private[sohva] (val couch: CouchClient) extends CouchDB {

  val host = couch.host

  val port = couch.port

  val version = couch.version

  val serializer = couch.serializer

  /** Performs a login and returns true if login succeeded.
   *  from now on, if login succeeded the couch instance is identified and
   *  all requests will be done with the given credentials.
   *  This performs a cookie authentication.
   */
  def login(name: String, password: String): Result[Boolean] =
    for(res <- _http(request / "_session" <<
         Map("name" -> name, "password" -> password) <:<
         Map("Accept" -> "application/json, text/javascript, */*",
           "Cookie" -> "AuthSession=") > setCookie _))
             yield Right(res)

  /** Logs the session out */
  def logout: Result[Boolean] =
    for(res <- _http((request / "_session").DELETE > setCookie _))
      yield Right(res)

  /** Returns the user associated to the current session, if any */
  def currentUser: Result[Option[UserInfo]] = userContext.right.flatMap {
    case UserCtx(name, _) if name != null =>
      http(request / "_users" / ("org.couchdb.user:" + name)).right.map(user)
    case _ => Future.successful(Right(None))
  }

  /** Indicates whether the current session is logged in to the couch server */
  def isLoggedIn: Result[Boolean] = userContext.right.map {
    case UserCtx(name, _) if name != null =>
      true
    case _ => false
  }

  /** Indicates whether the current session gives the given role to the user */
  def hasRole(role: String): Result[Boolean] = userContext.right.map {
    case UserCtx(_, roles) => roles.contains(role)
    case _                 => false
  }

  /** Indicates whether the current session is a server admin session */
  def isServerAdmin: Result[Boolean] = hasRole("_admin")

  /** Returns the current user context */
  def userContext: Result[UserCtx] =
    http((request / "_session")).right.map(userCtx)

  // helper methods

  private[sohva] val _http =
    couch._http

  private var _cookie = ""

  private def cookie = _cookie.synchronized {
    _cookie
  }

  private def cookie_=(c: String) = _cookie.synchronized {
    _cookie = c
  }

  private[sohva] def request =
    couch.request <:< Map("Cookie" -> cookie)

  private def userCtx(json: String) =
    serializer.fromJson[AuthResult](json).userCtx

  private def setCookie(response: Response) = {
    response.getHeader("Set-Cookie") match {
      case null | "" =>
        // no cookie to set
        false
      case c =>
        cookie = c
        response.getStatusCode / 100 == 2
    }
  }

  private def user(json: String) =
    serializer.fromJsonOpt[UserInfo](json)

}

/** Result of the authentication request */
case class AuthResult(ok: Boolean, userCtx: UserCtx, info: Option[AuthInfo])

/** The user context giving his name and roles */
case class UserCtx(name: String, roles: List[String])

/** Authentication information indicating the authentication database,
 *  the handler used and the authentication method
 */
case class AuthInfo(authentication_db: String,
                    authentication_handlers: List[String],
                    authenticated: String)

/** A couchdb user has a name, a password and a lit of roles. */
case class UserInfo(val name: String,
                     val roles: List[String])

case class CouchUser(val name: String,
                     val password: String,
                     val roles: List[String],
                     val `type`: String = "user",
                     val _rev: Option[String] = None) {
  val _id = "org.couchdb.user:" + name
}

private case class PasswordResetUser(val name: String,
                                     val salt: String,
                                     val password_sha: String,
                                     val roles: List[String],
                                     val `type`: String = "user",
                                     val _rev: Option[String] = None,
                                     val reset_token_sha: Option[String] = None,
                                     val reset_token_salt: Option[String] = None,
                                     val reset_validity: Option[java.util.Date] = None) {
  val _id = "org.couchdb.user:" + name
}
