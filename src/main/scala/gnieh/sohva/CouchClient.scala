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

import spray.json._

import scala.concurrent.{
  Future,
  ExecutionContext
}

import akka.actor._
import akka.util.Timeout

import akka.stream.{
  ActorMaterializer,
  Materializer
}

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._

/** A CouchDB instance.
 *  Allows users to access the different databases and instance information.
 *  This is the key class to start with when one wants to work with couchdb.
 *  Through this one you will get access to the sessions and anonymous access
 *  to databases.
 *
 *  @author Lucas Satabin
 *
 */
class CouchClient(
    val host: String = "localhost",
    val port: Int = 5984,
    val ssl: Boolean = false)(
    implicit
    val system: ActorSystem,
    val timeout: Timeout) extends CouchDB {

  implicit def ec: ExecutionContext =
    system.dispatcher

  implicit val materializer: Materializer =
    ActorMaterializer()

  /** Starts a new OAuth session */
  def startOAuthSession(consumerKey: String, consumerSecret: String, token: String, secret: String): OAuthSession =
    new OAuthSession(consumerKey, consumerSecret, token, secret, this)

  /** Starts a new HTTP Basic authentication session */
  def startBasicSession(username: String, password: String) =
    new BasicSession(username, password, this)

  /** Starts a new session with the given credential */
  def withCredentials(credentials: CouchCredentials): Session = credentials match {
    case LoginPasswordCredentials(username, password) =>
      startBasicSession(username, password)
    case OAuthCredentials(consumerKey, consumerSecret, token, secret) =>
      startOAuthSession(consumerKey, consumerSecret, token, secret)
  }

  // ========== internals ==========

  protected[sohva] def prepare(req: HttpRequest) =
    req

  // the base uri to this couch instance
  protected[sohva] val uri =
    if (ssl)
      Uri("https", Uri.Authority(Uri.Host(host), port))
    else
      Uri("http", Uri.Authority(Uri.Host(host), port))

}

private case class CouchVersion(raw: String) {

  val Array(major, minor, rest) = raw.split("\\.", 3)

  override def equals(other: Any): Boolean = other match {
    case that: CouchVersion =>
      this.major == that.major && this.minor == that.minor
    case _ =>
      false
  }

}
