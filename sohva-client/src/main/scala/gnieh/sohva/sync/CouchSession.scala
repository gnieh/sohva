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
package gnieh.sohva.sync

import gnieh.sohva.{
  CouchSession => ACouchSession,
  CouchUser,
  UserCtx
}

/** An instance of a Couch session, that allows the user to login and
 *  send request identified with the login credentials.
 *  This performs a cookie based authentication against the couchdb server.
 *  The couchdb client instance retrieved for this session will send request
 *  authenticated by the user that logged in in this session.
 *
 *  @author Lucas Satabin
 *
 */
class CouchSession private[sync] (wrapped: ACouchSession) extends CouchDB(wrapped) {

  /** Performs a login and returns true if login succeeded.
   *  from now on, if login succeeded the couch instance is identified and
   *  all requests will be done with the given credentials.
   *  This performs a cookie authentication.
   */
  @inline
  def login(name: String, password: String): Boolean =
    synced(wrapped.login(name, password))

  /** Logs the session out */
  @inline
  def logout: Boolean =
    synced(wrapped.logout)

  /** Returns the user associated to the current session, if any */
  @inline
  def currentUser: Option[CouchUser] =
    synced(wrapped.currentUser)

  /** Indicates whether the current session is logged in to the couch server */
  @inline
  def isLoggedIn: Boolean =
    synced(wrapped.isLoggedIn)

  /** Indicates whether the current session gives the given role to the user */
  @inline
  def hasRole(role: String): Boolean =
    synced(wrapped.hasRole(role))

  /** Indicates whether the current session is a server admin session */
  @inline
  def isServerAdmin: Boolean =
    synced(wrapped.isServerAdmin)

  /** Returns the current user context */
  @inline
  def userContext: UserCtx =
    synced(wrapped.userContext)

}
