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

import util._

import dispatch._

import com.ning.http.client.{
  Request,
  RequestBuilder,
  Response
}

import scala.util.DynamicVariable

import java.io.{
  File,
  InputStream,
  FileOutputStream,
  BufferedInputStream
}
import java.text.SimpleDateFormat

import net.liftweb.json._

import eu.medsea.util.MimeUtil

/** A CouchDB instance.
 *  Allows users to access the different databases and information.
 *  This is the key class to start with when one wants to work with couchdb.
 *  Through this one you will get access to the databases.
 *
 *  @author Lucas Satabin
 *
 */
abstract class CouchDB {

  private[sohva] implicit val formats =
    standardFormats + new UserSerializer(version)

  /** The couchdb instance host name. */
  val host: String

  /** The couchdb instance port. */
  val port: Int

  /** The couchdb instance version. */
  val version: String

  /** Shuts down this instance of couchdb client. */
  def shutdown =
    _http.shutdown

  /** Returns the database on the given couch instance. */
  def database(name: String) =
    new Database(name, this)

  /** Returns the names of all databases in this couch instance. */
  def _all_dbs =
    http(request / "_all_dbs").map(asNameList _)

  /** Indicates whether this couchdb instance contains the given database */
  def contains(dbName: String) =
    http(request / "_all_dbs").map(containsName(dbName))

  // user management section

  /** Exposes the interface for managing couchdb users. */
  object users {

    /** The user database name. By default `_users`. */
    var dbName = "_users"

    /** Adds a new user with the given role list to the user database,
     *  and returns the new instance.
     */
    def add(name: String,
            password: String,
            roles: List[String] = Nil) = {
      val user = new CouchUser(name, Some(password), roles)()
      http((request / dbName / user._id <<
        pretty(render(Extraction.decompose(user)(formats)))).PUT).map(OkResult(_).ok)
    }

    /** Deletes the given user from the database. */
    def delete(name: String) =
      database(dbName).deleteDoc("org.couchdb.user:" + name)

  }

  // helper methods

  private[sohva] def request: RequestBuilder

  private[sohva] def _http: Http

  private[sohva] def http(request: RequestBuilder): Promise[JValue] =
    _http(request > handleCouchResponse _)

  private[sohva] def optHttp(request: RequestBuilder): Promise[Option[JValue]] =
    _http(request > handleOptionalCouchResponse _)

  private[sohva] def http[T](pair: (Request, FunctionHandler[T])): Promise[T] =
    _http(pair)

  private def handleCouchResponse(response: Response) = {
    val json = as.lift.Json(response)
    val code = response.getStatusCode
    if (code / 100 != 2) {
      // something went wrong...
      val error = json.extractOpt[ErrorResult]
      if (code == 409)
        throw new ConflictException(error)
      else
        throw new CouchException(code, error)
    }
    json
  }

  private def handleOptionalCouchResponse(response: Response) = {
    val json = as.lift.Json(response)
    val code = response.getStatusCode
    if (code == 404) {
      None
    } else if (code / 100 != 2) {
      // something went wrong...
      val error = json.extractOpt[ErrorResult]
      if (code == 409)
        throw new ConflictException(error)
      else
        throw new CouchException(code, error)
    } else {
      Some(json)
    }
  }

  private def asNameList(json: JValue) =
    json.extract[List[String]]

  private def containsName(name: String)(json: JValue) =
    json.extract[List[String]].contains(name)

}

/** Gives the user access to the different operations available on a database.
 *  Among other operation this is the key class to get access to the documents
 *  of this database.
 *
 *  @author Lucas Satabin
 */
case class Database(val name: String,
                    private[sohva] val couch: CouchDB) {

  import couch.formats

  /** Returns the information about this database */
  def info = couch.optHttp(request).map(_.map(infoResult))

  /** Indicates whether this database exists */
  @inline
  def exists_? = couch.contains(name)

  /** Creates this database in the couchdb instance if it does not already exist.
   *  Returns <code>true</code> iff the database was actually created.
   */
  def create = exists_?.flatMap(ex => if (ex) {
    Promise(false)
  } else {
    couch.http(request.PUT).map(OkResult(_).ok)
  })

  /** Deletes this database in the couchdb instance if it exists.
   *  Returns <code>true</code> iff the database was actually deleted.
   */
  def delete = exists_?.flatMap(ex => if (ex) {
    couch.http(request.DELETE).map(OkResult(_).ok)
  } else {
    Promise(false)
  })

  /** Returns the document identified by the given id if it exists */
  def getDocById[T: Manifest](id: String): Promise[Option[T]] =
    couch.optHttp(request / id).map(_.flatMap(docResult[T]))

  /** Creates or updates the given object as a document into this database
   *  The given object must have an `_id` and an optional `_rev` fields
   *  to conform to the couchdb document structure.
   */
  def saveDoc[T: Manifest](doc: T with Doc) =
    couch.http((request / doc._id << pretty(render(Extraction.decompose(doc)))).PUT)
      .map(docUpdateResult _)
      .flatMap(res => res match {
        case DocUpdate(true, id, _) =>
          getDocById[T](id)
        case DocUpdate(false, _, _) =>
          Promise(None)
      })

  /** Deletes the document from the database.
   *  The document will only be deleted if the caller provided the last revision
   */
  def deleteDoc[T: Manifest](doc: T with Doc) =
    couch.http((request / doc._id).DELETE <<? Map("rev" -> doc._rev.getOrElse("")))
      .map(OkResult(_).ok)

  /** Deletes the document identified by the given id from the database.
   *  If the document exists it is deleted and the method returns `true`,
   *  otherwise returns `false`.
   */
  def deleteDoc(id: String) =
    couch.http((request / id) > extractRev _).flatMap {
      case Some(rev) =>
        couch.http((request / id).DELETE <<? Map("rev" -> rev))
          .map(OkResult(_).ok)
      case None => Promise(false)
    }

  /** Attaches the given file to the given document id.
   *  If no mime type is given, sohva tries to guess the mime type of the file
   *  itself. It it does not manage to identify the mime type, the file won't be
   *  attached...
   *  This method returns `true` iff the file was attached to the document.
   */
  def attachTo(docId: String, file: File, contentType: Option[String]) = {
    // first get the last revision of the document (if it exists)
    val rev = couch.http((request / docId).HEAD > extractRev _)
    val mime = contentType match {
      case Some(mime) => mime
      case None => MimeUtil.getMimeType(file)
    }

    if (mime == MimeUtil.UNKNOWN_MIME_TYPE) {
      Promise(false) // unknown mime type, cannot attach the file
    } else {
      rev.flatMap { r =>
        val params = r match {
          case Some(r) =>
            List("rev" -> r)
          case None =>
            // doc does not exist? well... good... does it matter? no! 
            // couchdb will create it for us, don't worry
            Nil
        }
        couch.http(request / docId / file.getName
          <<? params <<< file <:< Map("Content-Type" -> mime)).map(OkResult(_).ok)
      }
    }
  }

  /** Attaches the given file (given as an input stream) to the given document id.
   *  If no mime type is given, sohva tries to guess the mime type of the file
   *  itself. It it does not manage to identify the mime type, the file won't be
   *  attached...
   *  This method returns `true` iff the file was attached to the document.
   */
  def attachTo(docId: String,
               attachment: String,
               stream: InputStream,
               contentType: Option[String]): Promise[Boolean] = {
    // create a temporary file with the content of the input stream
    val file = File.createTempFile(attachment, null)
    import Arm._
    using(new FileOutputStream(file)) { fos =>
      using(new BufferedInputStream(stream)) { bis =>
        val array = new Array[Byte](bis.available)
        bis.read(array)
        fos.write(array)
      }
    }
    attachTo(docId, file, contentType)
  }

  /** Returns the given attachment for the given docId.
   *  It returns the mime type if any given in the response and the input stream
   *  to read the response from the server.
   */
  def getAttachment(docId: String, attachment: String) =
    couch.http(request / docId / attachment OK readFile)

  /** Deletes the given attachment for the given docId */
  def deleteAttachment(docId: String, attachment: String) = {
    // first get the last revision of the document (if it exists)
    val rev = couch.http((request / docId).HEAD > extractRev _)
    rev.flatMap { r =>
      r match {
        case Some(r) =>
          couch.http((request / docId / attachment <<?
            List("rev" -> r)).DELETE).map(OkResult(_).ok)
        case None =>
          // doc does not exist? well... good... just do nothing
          Promise(false)
      }
    }
  }

  /** Returns the security document of this database if any defined */
  def securityDoc =
    couch.http(request / "_security").map(SecurityDoc)

  /** Creates or updates the security document.
   *  Security documents are special documents with no `_id` nor `_rev` fields.
   */
  def saveSecurityDoc(doc: SecurityDoc) = {
    couch.http(request / "_security" <:< Map("Content-Type" -> "application/json") <<
      compact(render(Extraction.decompose(doc)))).flatMap { json =>
      docUpdateResult(json) match {
        case DocUpdate(true, id, _) =>
          getDocById[SecurityDoc](id)
        case DocUpdate(false, _, _) =>
          Promise(None)
      }
    }
  }

  /** Returns a design object that allows user to work with views */
  def design(designName: String) =
    Design(this, designName)

  // helper methods

  private[sohva] def request =
    couch.request / name

  private def readFile(response: Response) =
    (response.getContentType, response.getResponseBodyAsStream)

  private def infoResult(json: JValue) =
    json.extract[InfoResult]

  private def docResult[T: Manifest](json: JValue) =
    json.extractOpt[T]

  private def docUpdateResult(json: JValue) =
    json.extract[DocUpdate]

  private def extractRev(response: Response) = {
    response.getHeader("Etag") match {
      case null | "" => None
      case etags =>
        Some(etags.stripPrefix("\"").stripSuffix("\""))
    }
  }

}

/** A security document is a special document for couchdb. It has no `_id` or
 *  `_rev` field.
 *
 *  @author Lucas Satabin
 */
case class SecurityDoc(admins: SecurityList, readers: SecurityList)
object SecurityDoc extends (JValue => Option[SecurityDoc]) {
  def apply(json: JValue) = json.extractOpt[SecurityDoc]
}
case class SecurityList(names: List[String], roles: List[String])

/** A design gives access to the different views.
 *  Use this class to get or create new views.
 *
 *  @author Lucas Satabin
 */
case class Design(db: Database,
                  val name: String,
                  val language: String = "javascript") {

  import db.couch.formats

  private[sohva] def request = db.request / "_design" / name.trim

  /** Returns the design document from the couchdb instance.
   *  Returns `None` if the design document does not exist.
   */
  def getDesignDocument =
    db.couch.optHttp(request).map(_.map(designDoc))

  /** Deletes this design document from the couchdb instance */
  def delete =
    db.deleteDoc("_design/" + name.trim)

  /** Creates or updates the view in this design
   *  with the given name, map function and reduce function.
   *  If the design does not exist yet, it is created.
   */
  def saveView(viewName: String, mapFun: String, reduceFun: Option[String] = None) = {
    val view = ViewDoc(mapFun, reduceFun)
    getDesignDocument.map {
      case Some(design) =>
        // the updated design
        design.copy(views = design.views + (viewName -> view))()
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(viewName -> view))()
    }.flatMap(db.saveDoc(_).map(_.isDefined))
  }

  /** Deletes the view with the given name from the design */
  def deleteView(viewName: String) = {
    getDesignDocument.flatMap {
      case Some(design) =>
        db.saveDoc(design.copy(views = design.views - viewName)()).map(_.isDefined)
      case None => Promise(false)
    }
  }

  /** Returns the (typed) view in this design document.
   *  The different types are:
   *  - Key: type of the key for this view
   *  - Value: Type of the value returned in the result
   *  - Doc: Type of the full document in the case where the view is queried with `include_docs` set to `true`
   */
  def view[Key: Manifest, Value: Manifest, Doc: Manifest](viewName: String) =
    View[Key, Value, Doc](this, viewName)

  // helper methods

  private def designDoc(json: JValue) =
    json.extract[DesignDoc]

}

/** A view can be queried to get the result.
 *
 *  @author Lucas Satabin
 */
case class View[Key: Manifest, Value: Manifest, Doc: Manifest](design: Design,
                                                               view: String) {

  import design.db.couch.formats

  private def request = design.request / "_view" / view

  /** Queries the view on the server and returned the typed result.
   *  BE CAREFUL: If the types given to the constructor are not correct,
   *  strange things may happen! By 'strange', I mean exceptions
   */
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
            update_seq: Boolean = false) = {

    def toJsonString(a: Any) =
      compact(render(Extraction.decompose(a)))

    // build options
    val options = List(
      key.map(k => "key" -> toJsonString(k)),
      if (keys.nonEmpty) Some("keys" -> toJsonString(keys)) else None,
      startkey.map(k => "startkey" -> toJsonString(k)),
      startkey_docid.map("startkey_docid" -> _),
      endkey.map(k => "endkey" -> toJsonString(k)),
      endkey_docid.map("endkey_docid" -> _),
      if (limit > 0) Some("limit" -> limit) else None,
      stale.map("stale" -> _),
      if (descending) Some("descending" -> true) else None,
      if (skip > 0) Some("skip" -> skip) else None,
      if (group) Some("group" -> true) else None,
      if (group_level >= 0) Some("group_level" -> group_level) else None,
      if (reduce) None else Some("reduce" -> false),
      if (include_docs) Some("include_docs" -> true) else None,
      if (inclusive_end) None else Some("inclusive_end" -> false),
      if (update_seq) Some("update_seq" -> true) else None)
      .flatten
      .filter(_ != null) // just in case somebody gave Some(null)...
      .map {
        case (name, value) => (name, value.toString)
      }

    design.db.couch.http(request <<? options).map(viewResult).map { raw =>
      ViewResult(raw.total_rows, raw.offset,
        raw.rows.map { raw =>
          Row(raw.id,
            raw.key.extract[Key],
            raw.value.extract[Value],
            raw.doc.map(_.extract[Doc]))
        })
    }

  }

  // helper methods

  private def viewResult(json: JValue) =
    json.extract[RawViewResult]

}

// the different object that may be returned by the couchdb server

private[sohva] case class DesignDoc(_id: String,
                                    language: String,
                                    views: Map[String, ViewDoc])(
                                      val _rev: Option[String] = None)

private[sohva] case class ViewDoc(map: String,
                                  reduce: Option[String])

final case class OkResult(ok: Boolean, id: Option[String], rev: Option[String])
object OkResult extends (JValue => OkResult) {
  def apply(json: JValue) = json.extract[OkResult]
}

final case class InfoResult(compact_running: Boolean,
                            db_name: String,
                            disk_format_version: Int,
                            disk_size: Int,
                            doc_count: Int,
                            doc_del_count: Int,
                            instance_start_time: String,
                            purge_seq: Int,
                            update_seq: Int)
object InfoResult {
  def apply(json: JValue) = json.extract[InfoResult]
}

final case class DocUpdate(ok: Boolean,
                           id: String,
                           rev: String)
object DocUpdate {
  def apply(json: JValue) = json.extract[DocUpdate]
}

private[sohva] case class RawViewResult(total_rows: Int,
                                        offset: Int,
                                        rows: List[RawRow])

final case class ViewResult[Key: Manifest, Value: Manifest, Doc: Manifest](total_rows: Int,
                                                                           offset: Int,
                                                                           rows: List[Row[Key, Value, Doc]]) {

  def values =
    rows.map(row => (row.key, row.value)).toMap

  def docs =
    rows.map(row => row.doc.map(_ => (row.key, row.doc))).flatten.toMap

  def foreach(f: Row[Key, Value, Doc] => Unit) =
    rows.foreach(f)

}

private[sohva] case class RawRow(id: String,
                                 key: JValue,
                                 value: JValue,
                                 doc: Option[JValue] = None)

case class Row[Key: Manifest, Value: Manifest, Doc: Manifest](id: String,
                                                              key: Key,
                                                              value: Value,
                                                              doc: Option[Doc] = None)

final case class ErrorResult(error: String, reason: String)
object ErrorResult {
  def apply(json: JValue) = json.extract[ErrorResult]
}

final case class Attachment(content_type: String,
                            revpos: Int,
                            digest: String,
                            length: Int,
                            stub: Boolean)

trait WithAttachments {
  var _attachments: Option[Map[String, Attachment]] = None
}

class CouchException(val status: Int, val detail: Option[ErrorResult])
  extends Exception("status: " + status + "\nbecause: " + detail)
class ConflictException(detail: Option[ErrorResult]) extends CouchException(409, detail)