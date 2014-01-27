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
class OAuthSession private[control] (val wrapped: AOAuthSession) extends CouchDB(wrapped) with gnieh.sohva.OAuthSession[Try] {

  def this(
    consumerKey: String,
    consumerSecret: String,
    token: String,
    secret: String,
    couch: ACouchClient) =
    this(new AOAuthSession(consumerKey, consumerSecret, token, secret, couch))

  val consumerKey =
    wrapped.consumerKey

  val consumerSecret =
    wrapped.consumerSecret

  val token =
    wrapped.token

  val secret =
    wrapped.secret

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

