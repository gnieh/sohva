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

import spray.http._
import spray.client.pipelining._

import akka.actor.ActorRef

import scala.concurrent.Future

/** An instance of a Couch session that allows the user to login and
 *  send request identified with the login credentials.
 *  This performs a cookie based authentication against the couchdb server.
 *  The couchdb client instance retrieved for this session will send request
 *  authenticated by the user that logged in in this session.
 *
 *  @author Lucas Satabin
 *
 */
class CookieSession protected[sohva] (val couch: CouchClient) extends CouchDB with Session {

  val host =
    couch.host

  val port =
    couch.port

  val ssl =
    couch.ssl

  val version =
    couch.version

  val system =
    couch.system

  implicit def ec =
    couch.ec

  /** Performs a login and returns true if login succeeded.
   *  from now on, if login succeeded the couch instance is identified and
   *  all requests will be done with the given credentials.
   *  This performs a cookie authentication.
   */
  def login(name: String, password: String): Future[Boolean] =
    for (
      res <- rawHttp(Post(uri / "_session",
        FormData(Map("name" -> name, "password" -> password))) <:<
        Map("Accept" -> "application/json, text/javascript, */*")) withFailureMessage f"Problem logging in to $uri"
    ) yield res.status.isSuccess

  /** Logs the session out */
  def logout: Future[Boolean] =
    for (res <- rawHttp(Delete(uri / "_session")) withFailureMessage f"Problem logging out from $uri")
      yield res.status.isSuccess

  def isLoggedIn: Future[Boolean] =
    isAuthenticated

  // helper methods

  protected[sohva] val pipeline =
    couch.pipeline.andThen(_.map(withCookie))

  protected[sohva] def prepare(req: HttpRequest) =
    req <:< Map("Cookie" -> cookie)

  private var _cookie = "AuthSession="

  private def cookie = _cookie.synchronized {
    _cookie
  }

  private def cookie_=(c: String) = _cookie.synchronized {
    _cookie = c
  }

  protected[sohva] def uri =
    couch.uri

  private def withCookie[T](resp: HttpResponse): HttpResponse = {
    for (
      HttpHeaders.`Set-Cookie`(cookie) <- resp.headers.find {
        case HttpHeaders.`Set-Cookie`(_) =>
          true
        case _ =>
          false
      }
    ) this.cookie = cookie.toString
    resp
  }

}

/** Future of the authentication request */
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

