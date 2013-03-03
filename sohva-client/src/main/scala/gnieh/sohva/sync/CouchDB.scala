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
  CouchDB => ACouchDB,
  Database => ADatabase,
  Design => ADesign,
  View => AView,
  JsonSerializer,
  Doc,
  SecurityDoc,
  Uuids,
  InfoResult,
  DesignDoc,
  ViewResult
}

import java.io.{
  File,
  InputStream
}
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
  def database(name: String): Database =
    new Database(wrapped.database(name))

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

/** A design gives access to the different views.
 *  Use this class to get or create new views.
 *
 *  @author Lucas Satabin
 */
case class Design(wrapped: ADesign) {

  val name = wrapped.name

  val language = wrapped.language

  /** Returns the design document from the couchdb instance.
   *  Returns `None` if the design document does not exist.
   */
  @inline
  def getDesignDocument: Option[DesignDoc] =
    synced(wrapped.getDesignDocument)

  /** Deletes this design document from the couchdb instance */
   @inline
  def delete: Boolean =
    synced(wrapped.delete)

  /** Creates or updates the view in this design
   *  with the given name, map function and reduce function.
   *  If the design does not exist yet, it is created.
   */
  @inline
  def saveView(viewName: String,
               mapFun: String,
               reduceFun: Option[String] = None): Boolean =
    synced(wrapped.saveView(viewName, mapFun, reduceFun))

  /** Deletes the view with the given name from the design */
  @inline
  def deleteView(viewName: String): Boolean =
    synced(wrapped.deleteView(viewName))

  /** Returns the (typed) view in this design document.
   *  The different types are:
   *  - Key: type of the key for this view
   *  - Value: Type of the value returned in the result
   *  - Doc: Type of the full document in the case where the view is queried with `include_docs` set to `true`
   */
  def view[Key: Manifest, Value: Manifest, Doc: Manifest](viewName: String): View[Key, Value, Doc] =
    View[Key, Value, Doc](wrapped.view(viewName))

  /** Creates or updates the document validation function.
   *  If the design does not exist yet, it is created.
   */
  @inline
  def saveValidateFunction(validateFun: String): Boolean =
    synced(wrapped.saveValidateFunction(validateFun))

  /** Deletes the document validation function from the design */
  @inline
  def deleteValidateFunction: Boolean =
    synced(wrapped.deleteValidateFunction)

}

/** A view can be queried to get the result.
 *
 *  @author Lucas Satabin
 */
case class View[Key: Manifest, Value: Manifest, Doc: Manifest](wrapped: AView[Key, Value, Doc]) {

  /** Queries the view on the server and returned the typed result.
   *  BE CAREFUL: If the types given to the constructor are not correct,
   *  strange things may happen! By 'strange', I mean exceptions
   */
  @inline
  def query(key: Option[Key] = None,
            keys: List[Key] = Nil,
            startkey: Option[Key] = None,
            startkey_docid: Option[String] = None,
            endkey: Option[Key] = None,
            endkey_docid: Option[String] = None,
            limit: Int = -1,
            stale: Option[String] = None,
            descending: Boolean = false,
            skip: Int = 0,
            group: Boolean = false,
            group_level: Int = -1,
            reduce: Boolean = true,
            include_docs: Boolean = false,
            inclusive_end: Boolean = true,
            update_seq: Boolean = false): ViewResult[Key, Value, Doc] =

    synced(wrapped.query(key = key,
      keys = keys,
      startkey = startkey,
      startkey_docid = startkey_docid,
      endkey = endkey,
      endkey_docid = endkey_docid,
      limit = limit,
      stale = stale,
      descending = descending,
      skip = skip,
      group = group,
      group_level = group_level,
      reduce = reduce,
      include_docs = include_docs,
      inclusive_end = inclusive_end,
      update_seq = update_seq))

}
