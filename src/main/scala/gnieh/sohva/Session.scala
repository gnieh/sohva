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

import scala.concurrent.Future

import scala.util.Try

import spray.json._

import akka.http.scaladsl.model._

/**
 * Methods that must be implemented by a session.
 *
 *  @author Lucas Satabin
 */
trait Session extends CouchDB {

  import SohvaProtocol._

  /** Returns the user associated to the current session, if any */
  def currentUser: Future[Option[UserInfo]] = userContext.flatMap {
    case UserCtx(Some(name), _) =>
      http(HttpRequest(uri = uri / "_users" / (s"org.couchdb.user:$name"))).map(user)
    case _ => Future.successful(None)
  }

  /** Indicates whether the current session is authenticated with the couch server */
  def isAuthenticated: Future[Boolean] = userContext.map {
    case UserCtx(Some(name), _) => true
    case _ => false
  }

  /** Indicates whether the current session gives the given role to the user */
  def hasRole(role: String): Future[Boolean] = userContext.map {
    case UserCtx(_, roles) => roles.contains(role)
    case _ => false
  }

  /** Indicates whether the current session is a server admin session */
  def isServerAdmin: Future[Boolean] = hasRole("_admin")

  /** Returns the current user context */
  def userContext: Future[UserCtx] =
    http(HttpRequest(uri = uri / "_session")).map(userCtx)

  private def user(json: JsValue) =
    Try(json.convertTo[UserInfo]).toOption

  private def userCtx(json: JsValue) =
    json.convertTo[AuthResult].userCtx

}

/** Result of the authentication request */
case class AuthResult(ok: Boolean, userCtx: UserCtx, info: Option[AuthInfo])

/** The user context giving his name and roles */
case class UserCtx(name: Option[String], roles: List[String])

/**
 * Authentication information indicating the authentication database,
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
