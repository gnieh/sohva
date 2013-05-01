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
  Database => ADatabase,
  Doc,
  SecurityDoc,
  InfoResult,
  ChangeStream,
  OriginalChangeStream
}

import java.io.{
  File,
  InputStream
}

import net.liftweb.json.JObject

/** Gives the user access to the different operations available on a database.
 *  Among other operation this is the key class to get access to the documents
 *  of this database.
 *
 *  @author Lucas Satabin
 */
case class Database(wrapped: ADatabase) {

  val name = wrapped.name

  /** Returns the information about this database */
  @inline
  def info: Option[InfoResult] =
    synced(wrapped.info)

  /** Indicates whether this database exists */
  @inline
  def exists: Boolean =
    synced(wrapped.exists)

  /** Registers to the change stream of this database with potential filter */
  def changes(filter: Option[String] = None): ChangeStream =
    wrapped.changes(filter)

  /** Creates this database in the couchdb instance if it does not already exist.
   *  Returns <code>true</code> iff the database was actually created.
   */
  @inline
  def create: Boolean =
    synced(wrapped.create)

  /** Deletes this database in the couchdb instance if it exists.
   *  Returns <code>true</code> iff the database was actually deleted.
   */
  @inline
  def delete: Boolean =
    synced(wrapped.delete)

  /** Returns the document identified by the given id if it exists */
  @inline
  def getDocById[T: Manifest](id: String, revision: Option[String] = None): Option[T] =
    synced(wrapped.getDocById(id, revision))

  /** Returns the current revision of the document if it exists */
  @inline
  def getDocRevision(id: String): Option[String] =
    synced(wrapped.getDocRevision(id))

  /** Creates or updates the given object as a document into this database
   *  The given object must have an `_id` and an optional `_rev` fields
   *  to conform to the couchdb document structure.
   */
  @inline
  def saveDoc[T: Manifest](doc: T with Doc): Option[T] =
    synced(wrapped.saveDoc(doc))

  /** Deletes the document from the database.
   *  The document will only be deleted if the caller provided the last revision
   */
  @inline
  def deleteDoc[T: Manifest](doc: T with Doc): Boolean =
    synced(wrapped.deleteDoc(doc))

  /** Deletes the document identified by the given id from the database.
   *  If the document exists it is deleted and the method returns `true`,
   *  otherwise returns `false`.
   */
  @inline
  def deleteDoc(id: String): Boolean =
    synced(wrapped.deleteDoc(id))

  /** Attaches the given file to the given document id.
   *  If no mime type is given, sohva tries to guess the mime type of the file
   *  itself. It it does not manage to identify the mime type, the file won't be
   *  attached...
   *  This method returns `true` iff the file was attached to the document.
   */
  @inline
  def attachTo(docId: String, file: File, contentType: Option[String]): Boolean =
    synced(wrapped.attachTo(docId, file, contentType))

  /** Attaches the given file (given as an input stream) to the given document id.
   *  If no mime type is given, sohva tries to guess the mime type of the file
   *  itself. It it does not manage to identify the mime type, the file won't be
   *  attached...
   *  This method returns `true` iff the file was attached to the document.
   */
  @inline
  def attachTo(docId: String,
               attachment: String,
               stream: InputStream,
               contentType: Option[String]): Boolean =
    synced(wrapped.attachTo(docId, attachment, stream, contentType))

  /** Returns the given attachment for the given docId.
   *  It returns the mime type if any given in the response and the input stream
   *  to read the response from the server.
   */
  @inline
  def getAttachment(docId: String, attachment: String): Option[(String, InputStream)] =
    synced(wrapped.getAttachment(docId, attachment))

  /** Deletes the given attachment for the given docId */
  @inline
  def deleteAttachment(docId: String, attachment: String): Boolean =
    synced(wrapped.deleteAttachment(docId, attachment))

  /** Returns the security document of this database if any defined */
  @inline
  def securityDoc: SecurityDoc =
    synced(wrapped.securityDoc)

  /** Creates or updates the security document.
   *  Security documents are special documents with no `_id` nor `_rev` fields.
   */
  @inline
  def saveSecurityDoc(doc: SecurityDoc): Boolean =
    synced(wrapped.saveSecurityDoc(doc))

  /** Returns a design object that allows user to work with views */
  def design(designName: String, language: String = "javascript"): Design =
    Design(wrapped.design(designName, language))

}

