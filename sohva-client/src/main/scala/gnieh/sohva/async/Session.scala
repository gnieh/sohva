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
import Defaults._

/** Methods that must be implemented by a session.
 *
 *  @author Lucas Satabin
 */
trait Session extends gnieh.sohva.Session[AsyncResult] {
  this: CouchDB =>

  def currentUser: AsyncResult[Option[UserInfo]] = userContext.right.flatMap {
    case UserCtx(name, _) if name != null =>
      http(request / "_users" / ("org.couchdb.user:" + name)).right.map(user)
    case _ => Future.successful(Right(None))
  }

  def isAuthenticated: AsyncResult[Boolean] = userContext.right.map {
    case UserCtx(name, _) if name != null =>
      true
    case _ => false
  }

  def hasRole(role: String): AsyncResult[Boolean] = userContext.right.map {
    case UserCtx(_, roles) => roles.contains(role)
    case _                 => false
  }

  def isServerAdmin: AsyncResult[Boolean] = hasRole("_admin")

  def userContext: AsyncResult[UserCtx] =
    http((request / "_session")).right.map(userCtx)

  private def user(json: String) =
    serializer.fromJsonOpt[UserInfo](json)

  private def userCtx(json: String) =
    serializer.fromJson[AuthResult](json).userCtx

}

