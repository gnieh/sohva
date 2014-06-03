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
package control

import gnieh.sohva.async.{
  OAuthSession => AOAuthSession,
  CouchClient => ACouchClient
}

import scala.util.Try

/** An instance of a Couch session that allows the user to perform authenticated
 *  operations using OAuth.
 *
 *  @author Lucas Satabin
 */
class OAuthSession private[control] (override val wrapped: AOAuthSession)
    extends Session(wrapped) with gnieh.sohva.OAuthSession[Try] {

  def this(
    consumerKey: String,
    consumerSecret: String,
    token: String,
    secret: String,
    couch: ACouchClient) =
    this(new AOAuthSession(consumerKey, consumerSecret, token, secret, couch))

  val consumerKey =
    wrapped.consumerKey

  val token =
    wrapped.token

}
