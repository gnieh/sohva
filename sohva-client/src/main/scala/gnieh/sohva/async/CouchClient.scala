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

import net.liftweb.json._

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
    val custom: List[SohvaSerializer[_]] = Nil) extends CouchDB with gnieh.sohva.CouchClient[AsyncResult] {

  val serializer = new JsonSerializer(this.version, custom)

  // check that the version matches the one of the server
  for {
    Right(i) <- info
    if CouchVersion(i.version) != CouchVersion(version)
  } {
    LoggerFactory.getLogger(classOf[CouchClient]).warn("Warning Expected version is " + version + " but actual server version is " + i.version)
  }

  def startCookieSession =
    new CookieSession(this)

  @deprecated(message = "This method has been deprecated and will be removed in the next version. Please use startCookieSession instead", since = "0.5")
  def startSession =
    startCookieSession

  def startOAuthSession(consumerKey: String, consumerSecret: String, token: String, secret: String) =
    new OAuthSession(consumerKey, consumerSecret, token, secret, this)

  def shutdown =
    sohvaHttp.shutdown

  // ========== internals ==========

  private lazy val sohvaHttp = Http.configure { builder =>
    builder.setFollowRedirects(true)
  }

  protected[sohva] def _http[T](req: Req, handler: AsyncHandler[T]) =
    sohvaHttp(req > handler)

  // the base request to this couch instance
  protected[sohva] def request =
    if (ssl)
      :/(host, port).secure
    else
      :/(host, port)

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

