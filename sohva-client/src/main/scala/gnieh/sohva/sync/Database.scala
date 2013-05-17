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
package gnieh.sohva
package sync

import gnieh.sohva.async.{
  Database => ADatabase
}

import java.io.{
  File,
  InputStream
}

import net.liftweb.json.JObject

import scala.concurrent._
import duration._

/** Gives the user access to the different operations available on a database.
 *  Among other operation this is the key class to get access to the documents
 *  of this database.
 *
 *  @author Lucas Satabin
 */
case class Database(wrapped: ADatabase) extends gnieh.sohva.Database {

  type Result[T] = T

  def synced[T](result: wrapped.Result[T]): T = Await.result(result, Duration.Inf) match {
    case Right(t) => t
    case Left((409, error)) =>
      throw new ConflictException(error)
    case Left((code, error)) =>
      throw new CouchException(code, error)
  }

  val name = wrapped.name

  val credit = wrapped.credit

  val strategy = wrapped.strategy

  @inline
  def info: Option[InfoResult] =
    synced(wrapped.info)

  @inline
  def exists: Boolean =
    synced(wrapped.exists)

  def changes(filter: Option[String] = None): ChangeStream =
    wrapped.changes(filter)

  @inline
  def create: Boolean =
    synced(wrapped.create)

  @inline
  def delete: Boolean =
    synced(wrapped.delete)

  @inline
  def getDocById[T: Manifest](id: String, revision: Option[String] = None): Option[T] =
    synced(wrapped.getDocById(id, revision))

  @inline
  def getDocsById[T: Manifest](ids: List[String]): List[T] =
    synced(wrapped.getDocsById(ids))

  @inline
  def getDocRevision(id: String): Option[String] =
    synced(wrapped.getDocRevision(id))

  @inline
  def getDocRevisions(ids: List[String]): List[(String, String)] =
    synced(wrapped.getDocRevisions(ids))

  @inline
  def saveDoc[T: Manifest](doc: T with Doc): Option[T] =
    synced(wrapped.saveDoc(doc))

  @inline
  def saveDocs[T](docs: List[T with Doc], all_or_nothing: Boolean = false): List[DbResult] =
    synced(wrapped.saveDocs(docs, all_or_nothing))

  @inline
  def deleteDoc[T: Manifest](doc: T with Doc): Boolean =
    synced(wrapped.deleteDoc(doc))

  @inline
  def deleteDocs(ids: List[String], all_or_nothing: Boolean = false): List[DbResult] =
    synced(wrapped.deleteDocs(ids, all_or_nothing))

  @inline
  def deleteDoc(id: String): Boolean =
    synced(wrapped.deleteDoc(id))

  @inline
  def attachTo(docId: String, file: File, contentType: Option[String]): Boolean =
    synced(wrapped.attachTo(docId, file, contentType))

  @inline
  def attachTo(docId: String,
               attachment: String,
               stream: InputStream,
               contentType: Option[String]): Boolean =
    synced(wrapped.attachTo(docId, attachment, stream, contentType))

  @inline
  def getAttachment(docId: String, attachment: String): Option[(String, InputStream)] =
    synced(wrapped.getAttachment(docId, attachment))

  @inline
  def deleteAttachment(docId: String, attachment: String): Boolean =
    synced(wrapped.deleteAttachment(docId, attachment))

  @inline
  def securityDoc: SecurityDoc =
    synced(wrapped.securityDoc)

  @inline
  def saveSecurityDoc(doc: SecurityDoc): Boolean =
    synced(wrapped.saveSecurityDoc(doc))

  def design(designName: String, language: String = "javascript"): Design =
    Design(wrapped.design(designName, language))

}

