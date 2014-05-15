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

import strategy.Strategy

import java.io.{
  File,
  InputStream
}

import net.liftweb.json._

import gnieh.diffson.JsonPatch

/** Gives the user access to the different operations available on a database.
 *  Among other operations this is the key class to get access to the documents
 *  of this database.
 *
 *  It also exposes the change handler interface, that allows people to react to change notifications. This
 *  is a low-level API, that handles raw Json objects
 *
 *  @param credit The credit assigned to the conflict resolver. It represents the number of times the client tries to save the document before giving up.
 *  @param strategy The strategy being used to resolve conflicts
 *
 *  @author Lucas Satabin
 */
trait Database[Result[_]] {

  /** The database name */
  val name: String

  /** The retry credit */
  val credit: Int

  /** The merge strategy */
  val strategy: Strategy

  /** Returns the information about this database */
  def info: Result[Option[InfoResult]]

  /** Indicates whether this database exists */
  def exists: Result[Boolean]

  /** Registers to the change stream of this database with potential filter and
   *  since some revision. If no revision is given changes that occurred before the
   *  connection was established are not sent */
  def changes(since: Option[Int] = None, filter: Option[String] = None): ChangeStream

  /** Creates this database in the couchdb instance if it does not already exist.
   *  Returns <code>true</code> iff the database was actually created.
   */
  def create: Result[Boolean]

  /** Deletes this database in the couchdb instance if it exists.
   *  Returns <code>true</code> iff the database was actually deleted.
   */
  def delete: Result[Boolean]

  /** Returns the list of identifiers of the documents in this database */
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
    inclusive_end: Boolean = true): Result[List[String]]

  /** Returns the document identified by the given id if it exists */
  def getDocById[T: Manifest](id: String, revision: Option[String] = None): Result[Option[T]]

  /** Returns the raw repsentation of the document identified by the given id if it exists */
  def getRawDocById(id: String, revision: Option[String] = None): Result[Option[JValue]]

  /** Returns all the documents with given identifiers and of the given type.
   *  If the document with an identifier exists in the database but has not the
   *  required type, it is not added to the result
   */
  def getDocsById[T: Manifest](ids: List[String]): Result[List[T]]

  /** Returns the current revision of the document if it exists */
  def getDocRevision(id: String): Result[Option[String]]

  /** Returns the current revision of the documents */
  def getDocRevisions(ids: List[String]): Result[List[(String, String)]]

  /** Creates or updates the given object as a document into this database
   *  The given object must have an `_id` and an optional `_rev` fields
   *  to conform to the couchdb document structure.
   *  The saved revision is returned. If something went wrong, an exception is raised
   */
  def saveDoc[T <% IdRev: Manifest](doc: T): Result[T]

  /** Creates or updates a bunch of documents into the database.
   */
  def saveDocs[T <% IdRev](docs: List[T], all_or_nothing: Boolean = false): Result[List[DbResult]]

  /** Copies the origin document to the target document.
   *  If the target does not exist, it is created, otherwise it is updated and the target
   *  revision must be provided
   */
  def copy(origin: String, target: String, originRev: Option[String] = None, targetRev: Option[String] = None): Result[Boolean]

  /** Patches the document identified by the given identifier in the given revision.
   *  This will work if the revision is the last one, or if it is not but the automatic
   *  conflict manager manages to solve the potential conflicts.
   *  The patched revision is returned. If something went wrong, an exception is raised
   */
  def patchDoc[T <: IdRev: Manifest](id: String, rev: String, patch: JsonPatch): Result[T]

  /** Deletes the document from the database.
   *  The document will only be deleted if the caller provided the last revision
   */
  def deleteDoc[T <% IdRev](doc: T): Result[Boolean]

  /** Deletes the document identified by the given id from the database.
   *  If the document exists it is deleted and the method returns `true`,
   *  otherwise returns `false`.
   */
  def deleteDoc(id: String): Result[Boolean]

  /** Deletes a bunch of documents at once returning the results
   *  for each identifier in the document list. One can choose the update strategy
   *  by setting the parameter `all_or_nothing` to `true` or `false`.
   */
  def deleteDocs(ids: List[String], all_or_nothing: Boolean = false): Result[List[DbResult]]

  /** Attaches the given file to the given document id.
   *  This method returns `true` iff the file was attached to the document.
   */
  def attachTo(docId: String, file: File, contentType: String): Result[Boolean]

  /** Attaches the given file (given as an input stream) to the given document id.
   *  If no mime type is given, sohva tries to guess the mime type of the file
   *  itself. It it does not manage to identify the mime type, the file won't be
   *  attached...
   *  This method returns `true` iff the file was attached to the document.
   */
  def attachTo(docId: String,
    attachment: String,
    stream: InputStream,
    contentType: String): Result[Boolean]

  /** Returns the given attachment for the given docId.
   *  It returns the mime type if any given in the response and the input stream
   *  to read the response from the server.
   */
  def getAttachment(docId: String, attachment: String): Result[Option[(String, InputStream)]]

  /** Deletes the given attachment for the given docId */
  def deleteAttachment(docId: String, attachment: String): Result[Boolean]

  /** Returns the security document of this database if any defined */
  def securityDoc: Result[SecurityDoc]

  /** Creates or updates the security document.
   *  Security documents are special documents with no `_id` nor `_rev` fields.
   */
  def saveSecurityDoc(doc: SecurityDoc): Result[Boolean]

  /** Returns a design object that allows user to work with views */
  def design(designName: String, language: String = "javascript"): Design[Result]

  /** Returns a built-in view of this database, identified by its name.
   *  E.g. `_all_docs`.
   */
  def builtInView(view: String): View[Result]

}

final case class InfoResult(
  compact_running: Boolean,
  db_name: String,
  disk_format_version: Int,
  disk_size: Int,
  doc_count: Int,
  doc_del_count: Int,
  instance_start_time: String,
  purge_seq: Int,
  update_seq: Int)

final case class DocUpdate(
  ok: Boolean,
  id: String,
  rev: String)

private[sohva] final case class BulkDocs[T](rows: List[BulkDocRow[T]])

private[sohva] final case class BulkDocRow[T](id: String, rev: String, doc: Option[T])

