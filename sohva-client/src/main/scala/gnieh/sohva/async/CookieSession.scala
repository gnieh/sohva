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

import spray.http._
import spray.client.pipelining._

import akka.actor.ActorRef

import scala.concurrent.Future

/** An instance of a Couch session, that allows the user to login and
 *  send request identified with the login credentials.
 *  This performs a cookie based authentication against the couchdb server.
 *  The couchdb client instance retrieved for this session will send request
 *  authenticated by the user that logged in in this session.
 *
 *  @author Lucas Satabin
 *
 */
class CookieSession protected[sohva] (val couch: CouchClient) extends CouchDB with Session with gnieh.sohva.CookieSession[Future] {

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

  def login(name: String, password: String): Future[Boolean] =
    for (
      res <- rawHttp(Post(uri / "_session",
        FormData(Map("name" -> name, "password" -> password))) <:<
        Map("Accept" -> "application/json, text/javascript, */*")) withFailureMessage f"Problem logging in to $uri"
    ) yield res.status.isSuccess

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

