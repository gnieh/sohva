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
import oauth._

import com.ning.http.client.oauth._
import com.ning.http.client.AsyncHandler

/** An instance of a Couch session that allows the user to perform authenticated
 *  operations using OAuth.
 *
 *  @author Lucas Satabin
 */
class OAuthSession protected[sohva] (
  val consumerKey: String,
  val consumerSecret: String,
  val token: String,
  val secret: String,
  val couch: CouchClient)
    extends CouchDB
    with Session
    with gnieh.sohva.OAuthSession[AsyncResult] {

  val host =
    couch.host

  val port =
    couch.port

  val version =
    couch.version

  val serializer =
    couch.serializer

  // helper methods

  private val consumer = new ConsumerKey(consumerKey, consumerSecret)
  private val user = new RequestToken(token, secret)

  protected[sohva] def _http[T](req: Req, handler: AsyncHandler[T]) =
    couch._http(req.sign(consumer, user), handler)

  // sign all requests sent to CouchDB
  protected[sohva] def request =
    couch.request

}

