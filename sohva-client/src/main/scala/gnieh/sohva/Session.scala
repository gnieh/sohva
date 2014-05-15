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

/** Methods that must be implemented by a session.
 *
 *  @author Lucas Satabin
 */
trait Session[Result[_]] extends CouchDB[Result] {

  /** Returns the user associated to the current session, if any */
  def currentUser: Result[Option[UserInfo]]

  /** Indicates whether the current session is authenticated with the couch server */
  def isAuthenticated: Result[Boolean]

  /** Indicates whether the current session gives the given role to the user */
  def hasRole(role: String): Result[Boolean]

  /** Indicates whether the current session is a server admin session */
  def isServerAdmin: Result[Boolean]

  /** Returns the current user context */
  def userContext: Result[UserCtx]

}

