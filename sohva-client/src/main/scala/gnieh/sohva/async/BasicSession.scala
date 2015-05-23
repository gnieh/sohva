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

import scala.concurrent.Future

import spray.http._
import spray.client.pipelining._

/** An instance of a Couch session that allows the user to perform authenticated
 *  operations using HTTP basic authentication.
 *
 *  @author Lucas Satabin
 */
class BasicSession protected[sohva] (
  val username: String,
  val password: String,
  val couch: CouchClient)
    extends CouchDB
    with Session
    with gnieh.sohva.BasicSession[Future] {

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
    addCredentials(BasicHttpCredentials(username, password)) ~> couch.pipeline

  protected[sohva] def prepare(req: HttpRequest) =
    req

  protected[sohva] def uri =
    couch.uri

}

