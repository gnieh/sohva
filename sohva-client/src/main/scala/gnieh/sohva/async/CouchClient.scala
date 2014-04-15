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

import net.liftweb.json._

import spray.http._
import spray.client.pipelining._
import spray.can._

import scala.concurrent.{
  Future,
  ExecutionContext
}

import akka.actor._
import akka.util.Timeout
import akka.io.IO

import org.slf4j.LoggerFactory

/** A CouchDB instance.
 *  Allows users to access the different databases and instance information.
 *  This is the key class to start with when one wants to work with couchdb.
 *  Through this one you will get access to the sessions and anonymous access
 *  to databases.
 *
 *  @author Lucas Satabin
 *
 */
class CouchClient(val host: String = "localhost",
  val port: Int = 5984,
  val ssl: Boolean = false,
  val version: String = "1.4",
  val custom: List[SohvaSerializer[_]] = Nil)(
    implicit val system: ActorSystem,
    val timeout: Timeout)
    extends CouchDB with gnieh.sohva.CouchClient[Future] {

  val serializer =
    new JsonSerializer(this.version, custom)

  implicit def ec: ExecutionContext =
    system.dispatcher

  implicit def formats =
    serializer.formats

  // check that the version matches the one of the server
  for {
    i <- info
    if CouchVersion(i.version) != CouchVersion(version)
  } {
    LoggerFactory.getLogger(classOf[CouchClient]).warn(s"Warning Expected version is $version but actual server version is ${i.version}")
  }

  def startCookieSession =
    new CookieSession(this)

  def startOAuthSession(consumerKey: String, consumerSecret: String, token: String, secret: String) =
    new OAuthSession(consumerKey, consumerSecret, token, secret, this)

  def withCredentials(credentials: CouchCredentials): Future[Session] = credentials match {
    case LoginPasswordCredentials(username, password) =>
      val session = startCookieSession
      for(true <- session.login(username, password))
        yield session
    case OAuthCredentials(consumerKey, consumerSecret, token, secret) =>
      Future.successful(startOAuthSession(consumerKey, consumerSecret, token, secret))
  }

  def shutdown() =
    IO(Http) ! Http.CloseAll

  // ========== internals ==========

  lazy val pipeline: HttpRequest => Future[HttpResponse] =
    sendReceive

  protected[sohva] def prepare(req: HttpRequest) =
    req

  // the base uri to this couch instance
  protected[sohva] def uri =
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

