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
package sync

import gnieh.sohva.async.{
  CouchDB => ACouchDB
}
import gnieh.sohva.strategy._

import java.util.Date

import scala.concurrent._
import duration._

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
abstract class CouchDB private[sync] (wrapped: ACouchDB) extends gnieh.sohva.CouchDB[Identity] {

  @inline
  val host = wrapped.host

  @inline
  val port = wrapped.port

  @inline
  val version = wrapped.version

  @inline
  val serializer = wrapped.serializer

  @inline
  def info: CouchInfo =
    synced(wrapped.info)

  @inline
  def database(name: String, credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Database =
    new Database(wrapped.database(name, credit, strategy))

  @inline
  def replicator(name: String = "_replicator", credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Replicator =
    new Replicator(wrapped.replicator(name, credit, strategy))

  @inline
  def _all_dbs: List[String] =
    synced(wrapped._all_dbs)

  @inline
  def _uuid: String =
    synced(wrapped._uuid)

  @inline
  def _uuids(count: Int = 1): List[String] =
    synced(wrapped._uuids(count))

  @inline
  def _config: Configuration =
    synced(wrapped._config)

  @inline
  def _config(section: String): Map[String, String] =
    synced(wrapped._config(section))

  @inline
  def _config(section: String, key: String): Option[String] =
    synced(wrapped._config(section, key))

  @inline
  def saveConfigValue(section: String, key: String, value: String): Boolean =
    synced(wrapped.saveConfigValue(section, key, value))

  @inline
  def deleteConfigValue(section: String, key: String): Boolean =
    synced(wrapped.deleteConfigValue(section, key))

  @inline
  def contains(dbName: String): Boolean =
    synced(wrapped.contains(dbName))

  // user management section

  object users extends Users[Identity] {

    @inline
    def dbName: String =
      wrapped.users.dbName

    @inline
    def dbName_=(n: String) =
      wrapped.users.dbName = n

    @inline
    def add(name: String,
      password: String,
      roles: List[String] = Nil): Boolean =
      synced(wrapped.users.add(name, password, roles))

    @inline
    def delete(name: String): Boolean =
      synced(wrapped.users.delete(name))

    @inline
    def generateResetToken(name: String, until: Date): String =
      synced(wrapped.users.generateResetToken(name, until))

    @inline
    def resetPassword(name: String, token: String, password: String): Boolean =
      synced(wrapped.users.resetPassword(name, token, password))

  }

}

