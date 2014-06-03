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
  Session => ASession
}

import scala.util.Try

abstract class Session private[control] (override val wrapped: ASession)
    extends CouchDB(wrapped) with gnieh.sohva.Session[Try] {

  @inline
  def currentUser: Try[Option[UserInfo]] =
    synced(wrapped.currentUser)

  @inline
  def isAuthenticated: Try[Boolean] =
    synced(wrapped.isAuthenticated)

  @inline
  def hasRole(role: String): Try[Boolean] =
    synced(wrapped.hasRole(role))

  @inline
  def isServerAdmin: Try[Boolean] =
    synced(wrapped.isServerAdmin)

  @inline
  def userContext: Try[UserCtx] =
    synced(wrapped.userContext)

}

