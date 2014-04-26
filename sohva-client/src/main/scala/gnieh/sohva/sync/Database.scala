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
  Database => ADatabase
}

import java.io.{
  File,
  InputStream
}

import net.liftweb.json._

import scala.concurrent._
import duration._

import gnieh.diffson.JsonPatch

/** Gives the user access to the different operations available on a database.
 *  Among other operation this is the key class to get access to the documents
 *  of this database.
 *
 *  @author Lucas Satabin
 */
class Database private[sohva] (wrapped: ADatabase) extends gnieh.sohva.Database[Identity] {

  @inline
  val name = wrapped.name

  @inline
  val credit = wrapped.credit

  @inline
  val strategy = wrapped.strategy

  @inline
  def info: Option[InfoResult] =
    synced(wrapped.info)

  @inline
  def exists: Boolean =
    synced(wrapped.exists)

  @inline
  def changes(since: Option[Int] = None, filter: Option[String] = None): ChangeStream =
    wrapped.changes(since, filter)

  @inline
  def create: Boolean =
    synced(wrapped.create)

  @inline
  def delete: Boolean =
    synced(wrapped.delete)

  @inline
  def _all_docs(key: Option[String] = None,
    keys: List[String] = Nil,
    startkey: Option[String] = None,
    startkey_docid: Option[String] = None,
    endkey: Option[String] = None,
    endkey_docid: Option[String] = None,
    limit: Int = -1,
    stale: Option[String] = None,
    descending: Boolean = false,
    skip: Int = 0,
    inclusive_end: Boolean = true): List[String] =
    synced(
      wrapped._all_docs(
        key = key,
        keys = keys,
        startkey = startkey,
        startkey_docid = startkey_docid,
        endkey = endkey,
        endkey_docid = endkey_docid,
        limit = limit,
        stale = stale,
        descending = descending,
        skip = skip,
        inclusive_end = inclusive_end
      )
    )

  @inline
  def getDocById[T: Manifest](id: String, revision: Option[String] = None): Option[T] =
    synced(wrapped.getDocById(id, revision))

  @inline
  def getRawDocById(id: String, revision: Option[String] = None): Option[JValue] =
    synced(wrapped.getRawDocById(id, revision))

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
  def saveDoc[T <% IdRev: Manifest](doc: T): T =
    synced(wrapped.saveDoc(doc))

  @inline
  def saveDocs[T <% IdRev](docs: List[T], all_or_nothing: Boolean = false): List[DbResult] =
    synced(wrapped.saveDocs(docs, all_or_nothing))

  @inline
  def copy(origin: String, target: String, originRev: Option[String] = None, targetRev: Option[String] = None): Boolean =
    synced(wrapped.copy(origin, target, originRev, targetRev))

  @inline
  def patchDoc[T <: IdRev: Manifest](id: String, rev: String, patch: JsonPatch): T =
    synced(wrapped.patchDoc(id, rev, patch))

  @inline
  def deleteDoc[T <% IdRev](doc: T): Boolean =
    synced(wrapped.deleteDoc(doc))

  @inline
  def deleteDocs(ids: List[String], all_or_nothing: Boolean = false): List[DbResult] =
    synced(wrapped.deleteDocs(ids, all_or_nothing))

  @inline
  def deleteDoc(id: String): Boolean =
    synced(wrapped.deleteDoc(id))

  @inline
  def attachTo(docId: String, file: File, contentType: String): Boolean =
    synced(wrapped.attachTo(docId, file, contentType))

  @inline
  def attachTo(docId: String,
    attachment: String,
    stream: InputStream,
    contentType: String): Boolean =
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
    new Design(wrapped.design(designName, language))

  def builtInView(view: String): View =
    new View(wrapped.builtInView(name))

}

