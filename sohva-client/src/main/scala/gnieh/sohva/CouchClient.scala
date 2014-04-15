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

/** A CouchDB instance.
 *  Allows users to access the different databases and instance information.
 *  This is the key class to start with when one wants to work with couchdb.
 *  Through this one you will get access to the sessions and anonymous access
 *  to databases.
 *
 *  @author Lucas Satabin
 *
 */
trait CouchClient[Result[_]] extends CouchDB[Result] {

  /** Starts a new cookie based session */
  def startCookieSession: CookieSession[Result]

  /** Starts a new OAuth session */
  def startOAuthSession(consumerKey: String, consumerSecret: String, token: String, secret: String): OAuthSession[Result]

  /** Starts a new session with the given credential */
  def withCredentials(credentials: CouchCredentials): Result[Session[Result]]

  /** Shuts down this instance of couchdb client. */
  def shutdown()

}
