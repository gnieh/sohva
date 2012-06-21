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
import dispatch.liftjson.Js._

import net.liftweb.json._

/** provides the API to work with couchdb users:
 *  - add a new user into the database,
 *  - delete a user from the database
 *
 *  @author Lucas Satabin
 */
class Users(private var couch: CouchDB,
            adminName: String,
            adminPassword: String,
            defaultRoles: List[String] = Nil) {

  import Users._

  /** Adds a new user to the user database, and returns the new instance */
  def add(name: String, password: String) = {

    def bytes2string(bytes: Array[Byte]) =
      bytes.foldLeft(new StringBuilder) {
        (res, byte) =>
          res.append(Integer.toHexString(byte & 0xff))
      }.toString

    val saltArray = new Array[Byte](16)
    Random.nextBytes(saltArray)
    val salt = bytes2string(saltArray)

    // compute the password hash
    val md = MessageDigest.getInstance("SHA-1")

    // the password string is concatenated with the generated salt
    // and the result is hashed using SHA-1
    val password_sha =
      bytes2string(md.digest((password + salt).getBytes("ASCII")))

    val user = CouchUser(name, password_sha, salt, defaultRoles)()

    // create the doc on the server
    couch
      .as(adminName, adminPassword)
      .database("_users")
      .saveDoc(user)

  }

  /** Deletes the given user from the database. */
  def delete(name: String) = {
    val db = couch
      .as(adminName, adminPassword)
      .database("_users")

    db.getDocById[CouchUser]("org.couchdb.user:" + name) match {
      case Some(user) => db.deleteDoc(user)
      case _ => false
    }

  }

}

/** An instance of a Couch session, that allows the user to login and
 *  send request identified with the login credentials.
 *  This performs a cookie based authentication against the couchdb server
 *
 *  @author Lucas Satabin
 *
 */
class CouchSession[User: Manifest](private var _couch: CouchDB) {

  /** Returns the couchdb instance for this session.
   *  It is <b>HIGHLY RECOMMENDED</b> to retrieve the couchdb instance
   *  through this method whenever one wants to send a request during this session
   */
  def couch = _couch

  /** Performs a login and returns true if login succeeded.
   *  from now on, if login succeeded the couch instance is identified and
   *  all requests will be done with the given credentials.
   *  This performs a cookie authentication.
   */
  def login(name: String, password: String) = {
    http((_couch.request / "_session" <<
      Map("name" -> name, "password" -> password)) <:<
      Map("Accept" -> "application/json, text/javascript, */*",
        "Content-Type" -> "application/x-www-form-urlencoded",
        "Cache-Control" -> "no-cache",
        "Pragma" -> "no-cache",
        "Cookie" -> "AuthSession=") >:> setCookie _) {
      case (401, result) => false
    }
  }

  /** Logs the session out */
  def logout = {
    http((_couch.request / "_session").DELETE ># OkResult)() match {
      case OkResult(true, _, _) =>
        _couch = _couch.as("")
        true
      case _ =>
        false
    }
  }

  /** Returns the user associated to the current session, if any */
  def currentUser = loggedContext match {
    case UserCtx(name, _) if name != null =>
      http(couch.request / "_users" / ("org.couchdb.user:" + name) ># user)()
    case _ => None
  }

  /** Indicates whether the current session is logged in to the couch server */
  def isLoggedIn = loggedContext match {
    case UserCtx(name, _) if name != null => true
    case _ => false
  }

  /** Indicates whether the current session gives the given role to the user */
  def hasRole(role: String) = loggedContext match {
    case UserCtx(_, roles) => roles.contains(role)
    case _ => false
  }

  /** Indicates whether the current session is a server admin session */
  def isServerAdmin = hasRole("_admin")

  // helper methods

  private def loggedContext =
    http((couch.request / "_session") ># userCtx _)()

  private def userCtx(json: JValue) =
    json.extract[AuthResult] match {
      case AuthResult(_, userCtx, _) => userCtx
    }

  private def setCookie(map: Map[String, Set[String]]) = {
    map.get("Set-Cookie") match {
      case Some(cookies) =>
        _couch = _couch.as(cookies.head)
        true
      case _ =>
        // no cookie to set
        false
    }
  }

  private def user(json: JValue) =
    json.extractOpt[User]

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

/** A couchdb user has a name, a password hash, a salt and a lit of roles.
 *  the second argument list shouldn't be changed and contains by default
 *  the values couchdb is expected for a user document.
 */
case class CouchUser(val name: String,
                     val password_sha: String,
                     val salt: String,
                     val roles: List[String])(
                       val _id: String = "org.couchdb.user:" + name,
                       val _rev: Option[String] = None,
                       val `type`: String = "user")
