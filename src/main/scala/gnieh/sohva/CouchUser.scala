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

import java.security.MessageDigest

import scala.util.Random
import scala.reflect.BeanInfo
import scala.annotation.target._

import dispatch._

import com.ning.http.client.Response

import net.liftweb.json._

/** provides the API to work with couchdb users:
 *  - add a new user into the database,
 *  - delete a user from the database
 *
 *  @author Lucas Satabin
 */
class Users(private val couch: CouchDB,
            adminName: String,
            adminPassword: String,
            defaultRoles: List[String] = Nil) {

  /** Adds a new user to the user database, and returns the new instance */
  def add(name: String, password: String) = {

    val user = new CouchUser(name, Some(password), defaultRoles)()
    // create the doc on the server
    _couch
      .database("_users")
      .saveDoc(user)

  }

  /** Deletes the given user from the database. */
  def delete(name: String) = {
    val db = _couch
      .database("_users")
    db.getDocById[CouchUser]("org.couchdb.user:" + name).flatMap {
      case Some(user) => db.deleteDoc(user)
      case _ => Promise(false)
    }

  }

  /** Shutdowns the user management client */
  def shutdown = _couch.shutdown

  private[this] val _couch = couch.copy().as_!(adminName, adminPassword)

}

object CouchSession {
  def create(couch: CouchDB) =
    new CouchSession(couch.copy(cookie = None, admin = None))
}

/** An instance of a Couch session, that allows the user to login and
 *  send request identified with the login credentials.
 *  This performs a cookie based authentication against the couchdb server
 *
 *  @author Lucas Satabin
 *
 */
class CouchSession private (private val couch: CouchDB) {

  /** Performs a login and returns true if login succeeded.
   *  from now on, if login succeeded the couch instance is identified and
   *  all requests will be done with the given credentials.
   *  This performs a cookie authentication.
   */
  def login(name: String, password: String) = {
    couch.http(couch.request / "_session" <<
      Map("name" -> name, "password" -> password) <:<
      Map("Accept" -> "application/json, text/javascript, */*",
        "Cookie" -> "AuthSession=") > setCookie _)
  }

  /** Logs the session out */
  def logout =
    couch.http((couch.request / "_session").DELETE).map(json => OkResult(json) match {
      case OkResult(true, _, _) =>
        couch.as_!("")
        true
      case _ =>
        false
    })

  /** Returns the user associated to the current session, if any */
  def currentUser = loggedContext.map {
    case UserCtx(name, _) if name != null =>
      couch.http(couch.request / "_users" / ("org.couchdb.user:" + name)).map(user)
    case _ => Promise(None)
  }

  /** Indicates whether the current session is logged in to the couch server */
  def isLoggedIn = loggedContext.map {
    case UserCtx(name, _) if name != null => true
    case _ => false
  }

  /** Indicates whether the current session gives the given role to the user */
  def hasRole(role: String) = loggedContext.map {
    case UserCtx(_, roles) => roles.contains(role)
    case _ => false
  }

  /** Indicates whether the current session is a server admin session */
  def isServerAdmin = hasRole("_admin")

  /** Discards this session, closing all used resources. */
  def discard = couch.shutdown

  // helper methods

  private def loggedContext =
    couch.http((couch.request / "_session")).map(userCtx)

  private def userCtx(json: JValue) =
    json.extract[AuthResult] match {
      case AuthResult(_, userCtx, _) => userCtx
    }

  private def setCookie(response: Response) = {
    response.getHeader("Set-Cookie") match {
      case null | "" =>
        // no cookie to set
        false
      case cookie =>
        couch.as_!(cookie)
        response.getStatusCode / 100 == 2
    }
  }

  private def user(json: JValue) =
    json.extractOpt[CouchUser]

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
class CouchUser(val name: String,
                val password: Option[String],
                val roles: List[String])(
                    val _rev: Option[String] = None) {
  val _id = "org.couchdb.user:" + name
}
