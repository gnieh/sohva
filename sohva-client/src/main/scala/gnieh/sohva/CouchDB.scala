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

import strategy._

import scala.language.higherKinds

/** A CouchDB instance.
 *  Allows users to access the different databases and information.
 *  This is the key class to start with when one wants to work with couchdb.
 *  Through this one you will get access to the databases.
 *
 *  @author Lucas Satabin
 *
 */
trait CouchDB[Result[_]] {

  /** The couchdb instance host name. */
  val host: String

  /** The couchdb instance port. */
  val port: Int

  /** The couchdb instance version. */
  val version: String

  /** Returns the couchdb instance information */
  def info: Result[CouchInfo]

  /** Returns the database on the given couch instance. */
  def database(name: String, credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Database[Result]

  /** Returns the replicator database */
  def replicator(name: String = "_replicator", credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Replicator[Result]

  /** Returns the names of all databases in this couch instance. */
  def _all_dbs: Result[List[String]]

  /** Returns one UUID */
  def _uuid: Result[String]

  /** Returns the requested number of UUIDS (by default 1). */
  def _uuids(count: Int = 1): Result[List[String]]

  /** Returns the configuration object for this CouchDB instance */
  def _config: Result[Configuration]

  /** Returns the configuration section identified by its name
   *  (an empty map is returned if the section does not exist)
   */
  def _config(section: String): Result[Map[String, String]]

  /** Returns the configuration value
   *  Returns `None` if the value does not exist
   */
  def _config(section: String, key: String): Result[Option[String]]

  /** Saves the given key/value association in the specified section
   *  The section and/or the key is created if it does not exist
   */
  def saveConfigValue(section: String, key: String, value: String): Result[Boolean]

  /** Deletes the given configuration key inthe specified section */
  def deleteConfigValue(section: String, key: String): Result[Boolean]

  /** Indicates whether this couchdb instance contains the given database */
  def contains(dbName: String): Result[Boolean]

  /** Exposes the interface for managing couchdb users. */
  val users: Users[Result]

}

// the different object that may be returned by the couchdb server

sealed trait DbResult

final case class OkResult(ok: Boolean, id: Option[String], rev: Option[String]) extends DbResult

final case class ErrorResult(id: Option[String], error: String, reason: String) extends DbResult

final case class CouchInfo(couchdb: String, version: String)

private[sohva] final case class Uuids(uuids: List[String])

