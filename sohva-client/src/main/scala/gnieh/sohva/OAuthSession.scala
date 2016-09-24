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

import akka.actor.ActorRef

import scala.concurrent.Future
import spray.http._

/** An instance of a Couch session that allows the user to perform authenticated
 *  operations using OAuth.
 *
 *  @author Lucas Satabin
 */
class OAuthSession protected[sohva] (
  val consumerKey: String,
  consumerSecret: String,
  val token: String,
  secret: String,
  val couch: CouchClient)
    extends CouchDB
    with Session {

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

  implicit def ec = couch.ec

  // helper methods

  protected[sohva] val pipeline =
    couch.pipeline

  private val oauth = OAuth.oAuthAuthorizer(consumerKey, consumerSecret, token, secret)

  // sign all requests sent to CouchDB
  protected[sohva] def prepare(req: HttpRequest) =
    oauth(req)

  protected[sohva] def uri =
    couch.uri

}
