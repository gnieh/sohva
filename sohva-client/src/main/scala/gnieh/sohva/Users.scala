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

import scala.concurrent.Future

import scala.util.Try

import java.util.Date

import spray.json._

import spray.client.pipelining._

/** The users database, exposing the interface for managing couchdb users.
 *
 *  @author Lucas Satabin
 */
class Users(couch: CouchDB) {

  import couch._

  import SohvaProtocol._

  var dbName: String = "_users"

  private def userDb = couch.database(dbName)

  /** Adds a new user with the given role list to the user database,
   *  and returns the new instance.
   */
  def add(name: String,
    password: String,
    roles: List[String] = Nil): Future[Boolean] = {

    val user = CouchUser(name, password, roles)

    for (res <- http(Put(uri / dbName / user._id, user.toJson)))
      yield ok(res)

  }

  /** Deletes the given user from the database. */
  def delete(name: String): Future[Boolean] =
    database(dbName).deleteDoc("org.couchdb.user:" + name)

  override def toString =
    userDb.toString

}
