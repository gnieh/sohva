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

import com.ning.http.client._

/** An instance of a Couch session, that allows the user to login and
 *  send request identified with the login credentials.
 *  This performs a cookie based authentication against the couchdb server.
 *  The couchdb client instance retrieved for this session will send request
 *  authenticated by the user that logged in in this session.
 *
 *  @author Lucas Satabin
 *
 */
class CookieSession protected[sohva] (val couch: CouchClient) extends CouchDB with Session with gnieh.sohva.CookieSession[AsyncResult] {

  val host = couch.host

  val port = couch.port

  val version = couch.version

  val serializer = couch.serializer

  def login(name: String, password: String): AsyncResult[Boolean] =
    for (
      res <- _http(request / "_session" <<
        Map("name" -> name, "password" -> password) <:<
        Map("Accept" -> "application/json, text/javascript, */*"), noError)
    ) yield Right(res)

  def logout: AsyncResult[Boolean] =
    for (res <- _http((request / "_session").DELETE, noError))
      yield Right(res)

  def isLoggedIn: AsyncResult[Boolean] =
    isAuthenticated

  // helper methods

  protected[sohva] def _http[T](req: Req, handler: AsyncHandler[T]) =
    couch._http(req <:< Map("Cookie" -> cookie), new CookieProxyHandler(handler))

  private var _cookie = "AuthSession="

  private def cookie = _cookie.synchronized {
    _cookie
  }

  private def cookie_=(c: String) = _cookie.synchronized {
    _cookie = c
  }

  protected[sohva] def request =
    couch.request

  private val noError = new FunctionHandler(r => r.getStatusCode / 100 == 2)

  private class CookieProxyHandler[T](underlying: AsyncHandler[T]) extends AsyncHandler[T] {

    def onBodyPartReceived(bodyPart: HttpResponseBodyPart) =
      underlying.onBodyPartReceived(bodyPart)

    def onCompleted() =
      underlying.onCompleted()

    def onHeadersReceived(headers: HttpResponseHeaders) =
      if (headers.getHeaders.containsKey("Set-Cookie")) {
        cookie = headers.getHeaders.getFirstValue("Set-Cookie")
        underlying.onHeadersReceived(headers)
      } else {
        underlying.onHeadersReceived(headers)
      }

    def onStatusReceived(status: HttpResponseStatus) =
      underlying.onStatusReceived(status)

    def onThrowable(t: Throwable) =
      underlying.onThrowable(t)

  }

}
