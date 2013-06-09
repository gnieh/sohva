/*
* This file is part of the sohva project.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://wwwrapped.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package gnieh.sohva
package testing

import strategy._

import scala.collection.mutable.ListBuffer

import java.util.Date
import java.io.{
  File,
  InputStream
}

private class InstrumentedClient(createdDbs: ListBuffer[Database], override val wrapped: CouchClient)
  extends InstrumentedCouchDB(createdDbs, wrapped) with CouchClient {

  private val sessions = ListBuffer.empty[CouchSession]

  def startSession = {
    val session = new InstrumentedSession(createdDbs, wrapped.startSession)
    sessions += session
    session
  }

  def shutdown = {
    // delete all created databases
    for(db <- createdDbs)
      db.delete
    // close all sessions
    for(session <- sessions)
      session.logout
    createdDbs.clear
    sessions.clear
    wrapped.shutdown
  }

}

private abstract class InstrumentedCouchDB(createdDbs: ListBuffer[Database], val wrapped: CouchDB) extends CouchDB {

  type Result[T] = wrapped.Result[T]

  @inline
  val host = wrapped.host

  @inline
  val port = wrapped.port

  @inline
  val version = wrapped.version

  @inline
  val serializer = wrapped.serializer

  @inline
  def database(name: String, credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Database =
    new InstrumentedDatabase(createdDbs, wrapped.database(name, credit, strategy))

  @inline
  def replicator(name: String = "_replicator", credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Replicator =
    wrapped.replicator(name, credit, strategy)

  @inline
  def _all_dbs: Result[List[String]] =
    wrapped._all_dbs

  @inline
  def _uuid: Result[String] =
    wrapped._uuid

  @inline
  def _uuids(count: Int = 1): Result[List[String]] =
    wrapped._uuids(count)

  @inline
  def _config: Result[Configuration] =
    wrapped._config

  @inline
  def _config(section: String): Result[Map[String, String]] =
    wrapped._config(section)

  @inline
  def _config(section: String, key: String): Result[Option[String]] =
    wrapped._config(section, key)

  @inline
  def saveConfigValue(section: String, key: String, value: String): Result[Boolean] =
    wrapped.saveConfigValue(section, key, value)

  @inline
  def deleteConfigValue(section: String, key: String): Result[Boolean] =
    wrapped.deleteConfigValue(section, key)

  @inline
  def contains(dbName: String): Result[Boolean] =
    wrapped.contains(dbName)

  // user management section

  object users extends Users {

    type Result[T] = wrapped.users.Result[T]

    @inline
    def dbName: String =
      wrapped.users.dbName

    @inline
    def dbName_=(n: String) =
      wrapped.users.dbName = n

    @inline
    def add(name: String,
            password: String,
            roles: List[String] = Nil): Result[Boolean] =
      wrapped.users.add(name, password, roles)

    @inline
    def delete(name: String): Result[Boolean] =
      wrapped.users.delete(name)

    @inline
    def generateResetToken(name: String, until: Date): Result[Option[String]] =
      wrapped.users.generateResetToken(name, until)

    @inline
    def resetPassword(name: String, token: String, password: String): Result[Boolean] =
      wrapped.users.resetPassword(name, token, password)

  }

  @inline
  protected[sohva] def passwordSha(password: String): (String, String) =
    wrapped.passwordSha(password)

}

private class InstrumentedDatabase private[sohva](val createdDbs: ListBuffer[Database], val wrapped: Database) extends Database {

  type Result[T] = wrapped.Result[T]

  @inline
  val name = wrapped.name

  @inline
  val credit = wrapped.credit

  @inline
  val strategy = wrapped.strategy

  @inline
  def info: Result[Option[InfoResult]] =
    wrapped.info

  @inline
  def exists: Result[Boolean] =
    wrapped.exists

  @inline
  def changes(filter: Option[String] = None): ChangeStream =
    wrapped.changes(filter)

  def create: Result[Boolean] = {
    val created = wrapped.create
    createdDbs += this
    created
  }

  @inline
  def delete: Result[Boolean] =
    wrapped.delete

  @inline
  def getDocById[T: Manifest](id: String, revision: Option[String] = None): Result[Option[T]] =
    wrapped.getDocById(id, revision)

  @inline
  def getDocsById[T: Manifest](ids: List[String]): Result[List[T]] =
    wrapped.getDocsById(ids)

  @inline
  def getDocRevision(id: String): Result[Option[String]] =
    wrapped.getDocRevision(id)

  @inline
  def getDocRevisions(ids: List[String]): Result[List[(String, String)]] =
    wrapped.getDocRevisions(ids)

  @inline
  def saveDoc[T: Manifest](doc: T with Doc): Result[Option[T]] =
    wrapped.saveDoc(doc)

  @inline
  def saveDocs[T](docs: List[T with Doc], all_or_nothing: Boolean = false): Result[List[DbResult]] =
    wrapped.saveDocs(docs, all_or_nothing)

  @inline
  def deleteDoc[T: Manifest](doc: T with Doc): Result[Boolean] =
    wrapped.deleteDoc(doc)

  @inline
  def deleteDocs(ids: List[String], all_or_nothing: Boolean = false): Result[List[DbResult]] =
    wrapped.deleteDocs(ids, all_or_nothing)

  @inline
  def deleteDoc(id: String): Result[Boolean] =
    wrapped.deleteDoc(id)

  @inline
  def attachTo(docId: String, file: File, contentType: Option[String]): Result[Boolean] =
    wrapped.attachTo(docId, file, contentType)

  @inline
  def attachTo(docId: String,
               attachment: String,
               stream: InputStream,
               contentType: Option[String]): Result[Boolean] =
    wrapped.attachTo(docId, attachment, stream, contentType)

  @inline
  def getAttachment(docId: String, attachment: String): Result[Option[(String, InputStream)]] =
    wrapped.getAttachment(docId, attachment)

  @inline
  def deleteAttachment(docId: String, attachment: String): Result[Boolean] =
    wrapped.deleteAttachment(docId, attachment)

  @inline
  def securityDoc: Result[SecurityDoc] =
    wrapped.securityDoc

  @inline
  def saveSecurityDoc(doc: SecurityDoc): Result[Boolean] =
    wrapped.saveSecurityDoc(doc)

  @inline
  def design(designName: String, language: String = "javascript"): Design =
    wrapped.design(designName, language)

}

private class InstrumentedSession(createdDbs: ListBuffer[Database], override val wrapped: CouchSession)
  extends InstrumentedCouchDB(createdDbs, wrapped) with CouchSession {

  @inline
  def login(name: String, password: String): Result[Boolean] =
    wrapped.login(name, password)

  @inline
  def logout: Result[Boolean] =
    wrapped.logout

  @inline
  def currentUser: Result[Option[UserInfo]] =
    wrapped.currentUser

  @inline
  def isLoggedIn: Result[Boolean] =
    wrapped.isLoggedIn

  @inline
  def hasRole(role: String): Result[Boolean] =
    wrapped.hasRole(role)

  @inline
  def isServerAdmin: Result[Boolean] =
    wrapped.isServerAdmin

  @inline
  def userContext: Result[UserCtx] =
    wrapped.userContext

}

