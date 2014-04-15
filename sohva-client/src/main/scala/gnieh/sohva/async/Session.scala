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

import net.liftweb.json._

import spray.client.pipelining._

/** Methods that must be implemented by a session.
 *
 *  @author Lucas Satabin
 */
trait Session extends CouchDB with gnieh.sohva.Session[Future] {

  def currentUser: Future[Option[UserInfo]] = userContext.flatMap {
    case UserCtx(name, _) if name != null =>
      http(Get(uri / "_users" / (s"org.couchdb.user:$name"))).map(user)
    case _ => Future.successful(None)
  }

  def isAuthenticated: Future[Boolean] = userContext.map {
    case UserCtx(name, _) if name != null =>
      true
    case _ => false
  }

  def hasRole(role: String): Future[Boolean] = userContext.map {
    case UserCtx(_, roles) => roles.contains(role)
    case _                 => false
  }

  def isServerAdmin: Future[Boolean] = hasRole("_admin")

  def userContext: Future[UserCtx] =
    http(Get(uri / "_session")).map(userCtx)

  private def user(json: JValue) =
    serializer.fromJsonOpt[UserInfo](json)

  private def userCtx(json: JValue) =
    serializer.fromJson[AuthResult](json).userCtx

}

