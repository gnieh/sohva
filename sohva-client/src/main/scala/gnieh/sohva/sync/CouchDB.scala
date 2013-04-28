/*
* This file is part of the sohva project.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*couch.http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package gnieh.sohva.sync

import gnieh.sohva.{
  CouchDB => ACouchDB
}
import gnieh.sohva.strategy._

import java.util.Date

/** A CouchDB instance.
 *  Allows users to access the different databases and information.
 *  This is the key class to start with when one wants to work with couchdb.
 *  Through this one you will get access to the databases.
 *
 *  All calls are performed synchronously!
 *
 *  @author Lucas Satabin
 *
 */
abstract class CouchDB private[sync] (wrapped: ACouchDB) {

  /** The couchdb instance host name. */
  val host = wrapped.host

  /** The couchdb instance port. */
  val port = wrapped.port

  /** The couchdb instance version. */
  val version = wrapped.version

  /** The Json (de)serializer */
  val serializer = wrapped.serializer

  /** Returns the database on the given couch instance. */
  def database(name: String, credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Database =
    new Database(wrapped.database(name, credit, strategy))

  /** Returns the names of all databases in this couch instance. */
  def _all_dbs: List[String] =
    synced(wrapped._all_dbs)

  /** Returns the requested number of UUIDS (by default 1). */
  def _uuids(count: Int = 1): List[String] =
    synced(wrapped._uuids(count))

  /** Indicates whether this couchdb instance contains the given database */
  def contains(dbName: String): Boolean =
    synced(wrapped.contains(dbName))

  // user management section

  /** Exposes the interface for managing couchdb users. */
  object users {

    /** The user database name. By default `_users`. */
    def dbName: String =
      wrapped.users.dbName

    def dbName_=(n: String) =
      wrapped.users.dbName = n

    /** Adds a new user with the given role list to the user database,
     *  and returns the new instance.
     */
    def add(name: String,
            password: String,
            roles: List[String] = Nil): Boolean =
      synced(wrapped.users.add(name, password, roles))

    /** Deletes the given user from the database. */
    def delete(name: String): Boolean =
      synced(wrapped.users.delete(name))

    /** Generates a password reset token for the given user with the given validity and returns it */
    def generateResetToken(name: String, until: Date): Option[String] =
      synced(wrapped.users.generateResetToken(name, until))

    /** Resets the user password to the given one if:
     *   - a password reset token exists in the database
     *   - the token is still valid
     *   - the saved token matches the one given as parameter
     */
    def resetPassword(name: String, token: String, password: String): Boolean =
      synced(wrapped.users.resetPassword(name, token, password))

  }

}


