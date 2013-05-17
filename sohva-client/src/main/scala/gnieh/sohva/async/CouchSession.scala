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
package async

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
class CouchSession protected[sohva] (val couch: CouchClient) extends CouchDB with gnieh.sohva.CouchSession {

  val host = couch.host

  val port = couch.port

  val version = couch.version

  val serializer = couch.serializer

  def login(name: String, password: String): Result[Boolean] =
    for(res <- _http(request / "_session" <<
         Map("name" -> name, "password" -> password) <:<
         Map("Accept" -> "application/json, text/javascript, */*",
           "Cookie" -> "AuthSession=") > setCookie _))
             yield Right(res)

  def logout: Result[Boolean] =
    for(res <- _http((request / "_session").DELETE > setCookie _))
      yield Right(res)

  def currentUser: Result[Option[UserInfo]] = userContext.right.flatMap {
    case UserCtx(name, _) if name != null =>
      http(request / "_users" / ("org.couchdb.user:" + name)).right.map(user)
    case _ => Future.successful(Right(None))
  }

  def isLoggedIn: Result[Boolean] = userContext.right.map {
    case UserCtx(name, _) if name != null =>
      true
    case _ => false
  }

  def hasRole(role: String): Result[Boolean] = userContext.right.map {
    case UserCtx(_, roles) => roles.contains(role)
    case _                 => false
  }

  def isServerAdmin: Result[Boolean] = hasRole("_admin")

  def userContext: Result[UserCtx] =
    http((request / "_session")).right.map(userCtx)

  // helper methods

  protected[sohva] val _http =
    couch._http

  private var _cookie = ""

  private def cookie = _cookie.synchronized {
    _cookie
  }

  private def cookie_=(c: String) = _cookie.synchronized {
    _cookie = c
  }

  protected[sohva] def request =
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
