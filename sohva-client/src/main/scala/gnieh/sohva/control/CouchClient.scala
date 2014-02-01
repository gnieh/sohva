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
  CouchClient => ACouchClient
}

import scala.util.Try

/** A CouchDB instance.
 *  Allows users to access the different databases and instance information.
 *  This is the key class to start with when one wants to work with couchdb.
 *  Through this one you will get access to the sessions and anonymous access
 *  to databases.
 *
 *  @author Lucas Satabin
 *
 */
class CouchClient private[control] (wrapped: ACouchClient) extends CouchDB(wrapped) with gnieh.sohva.CouchClient[Try] {

  def this(host: String = "localhost",
    port: Int = 5984,
    ssl: Boolean = false,
    version: String = "1.4") =
    this(new ACouchClient(host, port, ssl, version))

  def startCookieSession =
    new CookieSession(wrapped.startCookieSession)

  @deprecated(message = "This method has been deprecated and will be removed in the next version. Please use startCookieSession instead", since = "0.5")
  def startSession =
    startCookieSession

  def startOAuthSession(consumerKey: String, consumerSecret: String, token: String, secret: String) =
    new OAuthSession(consumerKey, consumerSecret, token, secret, wrapped)

  def shutdown =
    wrapped.shutdown

}
