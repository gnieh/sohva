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
import java.security.MessageDigest
import java.util.Date

import scala.util.Random

import eu.medsea.util.MimeUtil

import net.liftweb.json._

/** A CouchDB instance.
 *  Allows users to access the different databases and information.
 *  This is the key class to start with when one wants to work with couchdb.
 *  Through this one you will get access to the databases.
 *
 *  @author Lucas Satabin
 *
 */
abstract class CouchDB {

  self =>

  /** The couchdb instance host name. */
  val host: String

  /** The couchdb instance port. */
  val port: Int

  /** The couchdb instance version. */
  val version: String

  /** The Json (de)serializer */
  val serializer: JsonSerializer

  /** Returns the database on the given couch instance. */
  def database(name: String) =
    new Database(name, this)

  /** Returns the names of all databases in this couch instance. */
  def _all_dbs =
    http(request / "_all_dbs").map(asStringList _)

  /** Returns the requested number of UUIDS (by default 1). */
  def _uuids(count: Int = 1) =
    http(request / "_uuids" <<? Map("count" -> count.toString))
      .map(asUuidsList _)

  /** Indicates whether this couchdb instance contains the given database */
  def contains(dbName: String) =
    http(request / "_all_dbs").map(containsName(dbName))

  // user management section

  /** Exposes the interface for managing couchdb users. */
  object users {

    /** The user database name. By default `_users`. */
    var dbName = "_users"

    private def userDb = self.database(dbName)

    /** Adds a new user with the given role list to the user database,
     *  and returns the new instance.
     */
    def add(name: String,
            password: String,
            roles: List[String] = Nil) = {

      if (version >= "1.2") {
        val user = NewCouchUser(name, password, roles)

        http((request / dbName / user._id <<
          serializer.toJson(user)).PUT).map(ok _)
      } else {
        val (salt, password_sha) = passwordSha(password)
        val user = LegacyCouchUser(name, salt, password_sha, roles)

        http((request / dbName / user._id <<
          serializer.toJson(user)).PUT).map(ok _)
      }

    }

    /** Deletes the given user from the database. */
    def delete(name: String) =
      database(dbName).deleteDoc("org.couchdb.user:" + name)

    /** Generates a password reset token for the given user with the given validity and returns it */
    def generateResetToken(name: String, until: Date): Promise[Option[String]] = {
      _uuids().flatMap {
        case List(token) =>
          userDb.getDocById[PasswordResetUser]("org.couchdb.user:" + name).flatMap { user =>
            // get the user document
            user match {
              case Some(user) =>
                // enrich the user document with password reset information
                val (token_salt, token_sha) = passwordSha(token)
                val u =
                  user.copy(
                    reset_token_sha = Some(token_sha),
                    reset_token_salt = Some(token_salt),
                    reset_validity = Some(until))
                // save back the enriched user document
                userDb.saveDoc(u).map(_.map(_ => token))
              case None =>
                Http.promise(None)
            }
          }
        case _ => throw new Exception("querying _uuids should always return an element")
      }
    }

    /** Resets the user password to the given one if:
     *   - a password reset token exists in the database
     *   - the token is still valid
     *   - the saved token matches the one given as parameter
     */
    def resetPassword(name: String, token: String, password: String): Promise[Boolean] = {
      userDb.getDocById[PasswordResetUser]("org.couchdb.user:" + name).flatMap { user =>
        user match {
          case Some(user) =>
            // check the token with the one in the database (if still valid)
            (user.reset_token_sha, user.reset_token_salt, user.reset_validity) match {
              case (Some(savedToken), Some(savedSalt), Some(validity)) =>
                val saltedToken = hash(token + savedSalt)
                if(new Date().before(validity) && savedToken == saltedToken) {
                  // save the user with the new password
                  val newUser = new NewCouchUser(user.name, password, roles = user.roles, _rev = user._rev)
                  http((request / dbName / user._id << serializer.toJson(newUser)).PUT).map(ok _)
                } else {
                  Http.promise(false)
                }
              case _ =>
                Http.promise(false)
            }
          case None =>
            Http.promise(false)
        }
      }
    }

  }

  // helper methods

  private[sohva] def request: RequestBuilder

  private[sohva] def _http: Http

  private[this] def bytes2string(bytes: Array[Byte]) =
    bytes.foldLeft(new StringBuilder) {
      (res, byte) =>
        res.append(Integer.toHexString(byte & 0xff))
    }.toString

  private[this] def hash(s: String) = {
    val md = MessageDigest.getInstance("SHA-1")
    bytes2string(md.digest(s.getBytes("UTF-8")))
  }

  private[this] def passwordSha(password: String) = {

    // compute the password hash
    // the password string is concatenated with the generated salt
    // and the result is hashed using SHA-1
    val saltArray = new Array[Byte](16)
    Random.nextBytes(saltArray)
    val salt = bytes2string(saltArray)

    (salt, hash(password + salt))
  }


  private[sohva] def http(request: RequestBuilder): Promise[String] =
    _http(request > handleCouchResponse _).map {
      case Left(exc) => throw exc
      case Right(v)  => v
    }

  private[sohva] def optHttp(request: RequestBuilder): Promise[Option[String]] =
    _http(request > handleOptionalCouchResponse _).map {
      case Left(exc) => throw exc
      case Right(v)  => v
    }

  private[sohva] def http[T](pair: (Request, FunctionHandler[T])): Promise[T] =
    _http(pair)

  private def handleCouchResponse(response: Response) = {
    val json = as.String(response)
    val code = response.getStatusCode
    if (code / 100 != 2) {
      // something went wrong...
      val error = serializer.fromJsonOpt[ErrorResult](json)
      if (code == 409)
        Left(new ConflictException(error))
      else
        Left(new CouchException(code, error))
    } else {
      Right(json)
    }
  }

  private def handleOptionalCouchResponse(response: Response) = {
    val json = as.String(response)
    val code = response.getStatusCode
    if (code == 404) {
      Right(None)
    } else if (code / 100 != 2) {
      // something went wrong...
      val error = serializer.fromJsonOpt[ErrorResult](json)
      if (code == 409)
        Left(new ConflictException(error))
      else
        Left(new CouchException(code, error))
    } else {
      Right(Some(json))
    }
  }

  private[sohva] def ok(json: String) =
    serializer.fromJson[OkResult](json).ok

  private def asStringList(json: String) =
    serializer.fromJson[List[String]](json)

  private def asUuidsList(json: String) =
    serializer.fromJson[Uuids](json).uuids

  private def containsName(name: String)(json: String) =
    serializer.fromJson[List[String]](json).contains(name)

}

/** Gives the user access to the different operations available on a database.
 *  Among other operation this is the key class to get access to the documents
 *  of this database.
 *
 *  @author Lucas Satabin
 */
case class Database(val name: String,
                    private[sohva] val couch: CouchDB) {

  import couch.serializer

  /** Returns the information about this database */
  def info = couch.optHttp(request).map(_.map(infoResult))

  /** Indicates whether this database exists */
  @inline
  def exists = couch.contains(name)

  /** Creates this database in the couchdb instance if it does not already exist.
   *  Returns <code>true</code> iff the database was actually created.
   */
  def create = exists.flatMap(ex => if (ex) {
    Http.promise(false)
  } else {
    couch.http(request.PUT).map(couch.ok)
  })

  /** Deletes this database in the couchdb instance if it exists.
   *  Returns <code>true</code> iff the database was actually deleted.
   */
  def delete = exists.flatMap(ex => if (ex) {
    couch.http(request.DELETE).map(couch.ok)
  } else {
    Http.promise(false)
  })

  /** Returns the document identified by the given id if it exists */
  def getDocById[T: Manifest](id: String, revision: Option[String] = None): Promise[Option[T]] =
    couch.optHttp(request / id <<? revision.map("rev" -> _).toList).map(_.map(docResult[T]))

  /** Returns the current revision of the document if it exists */
  def getDocRevision(id: String): Promise[Option[String]] =
    couch.http((request / id).HEAD > extractRev)

  /** Creates or updates the given object as a document into this database
   *  The given object must have an `_id` and an optional `_rev` fields
   *  to conform to the couchdb document structure.
   */
  def saveDoc[T: Manifest](doc: T with Doc) =
    couch.http((request / doc._id << serializer.toJson(doc)).PUT)
      .map(docUpdateResult _)
      .flatMap(res => res match {
        case DocUpdate(true, id, _) =>
          getDocById[T](id)
        case DocUpdate(false, _, _) =>
          Http.promise(None)
      })

  /** Deletes the document from the database.
   *  The document will only be deleted if the caller provided the last revision
   */
  def deleteDoc[T: Manifest](doc: T with Doc) =
    couch.http((request / doc._id).DELETE <<? Map("rev" -> doc._rev.getOrElse("")))
      .map(couch.ok)

  /** Deletes the document identified by the given id from the database.
   *  If the document exists it is deleted and the method returns `true`,
   *  otherwise returns `false`.
   */
  def deleteDoc(id: String) =
    getDocRevision(id).flatMap {
      case Some(rev) =>
        couch.http((request / id).DELETE <<? Map("rev" -> rev))
          .map(couch.ok)
      case None => Http.promise(false)
    }

  /** Attaches the given file to the given document id.
   *  If no mime type is given, sohva tries to guess the mime type of the file
   *  itself. It it does not manage to identify the mime type, the file won't be
   *  attached...
   *  This method returns `true` iff the file was attached to the document.
   */
  def attachTo(docId: String, file: File, contentType: Option[String]) = {
    // first get the last revision of the document (if it exists)
    val rev = getDocRevision(docId)
    val mime = contentType match {
      case Some(mime) => mime
      case None       => MimeUtil.getMimeType(file)
    }

    if (mime == MimeUtil.UNKNOWN_MIME_TYPE) {
      Http.promise(false) // unknown mime type, cannot attach the file
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
          <<? params <<< file <:< Map("Content-Type" -> mime)).map(couch.ok)
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
    couch.http(request / docId / attachment > readFile _)

  /** Deletes the given attachment for the given docId */
  def deleteAttachment(docId: String, attachment: String) = {
    // first get the last revision of the document (if it exists)
    val rev = getDocRevision(docId)
    rev.flatMap { r =>
      r match {
        case Some(r) =>
          couch.http((request / docId / attachment <<?
            List("rev" -> r)).DELETE).map(couch.ok)
        case None =>
          // doc does not exist? well... good... just do nothing
          Http.promise(false)
      }
    }
  }

  /** Returns the security document of this database if any defined */
  def securityDoc =
    couch.http(request / "_security").map(extractSecurityDoc _)

  /** Creates or updates the security document.
   *  Security documents are special documents with no `_id` nor `_rev` fields.
   */
  def saveSecurityDoc(doc: SecurityDoc) = {
    couch.http((request / "_security" << serializer.toJson(doc)).PUT).map(couch.ok)
  }

  /** Returns a design object that allows user to work with views */
  def design(designName: String, language: String = "javascript") =
    Design(this, designName, language)

  // helper methods

  private[sohva] def request =
    couch.request / name

  private def readFile(response: Response) = {
    if (response.getStatusCode == 404) {
      None
    } else {
      Some(response.getContentType, response.getResponseBodyAsStream)
    }
  }

  private def okResult(json: String) =
    serializer.fromJson[OkResult](json)

  private def infoResult(json: String) =
    serializer.fromJson[InfoResult](json)

  private def docResult[T: Manifest](json: String) =
    serializer.fromJson[T](json)

  private def docUpdateResult(json: String) =
    serializer.fromJson[DocUpdate](json)

  private def extractRev(response: Response) = {
    response.getHeader("Etag") match {
      case null | "" => None
      case etags =>
        Some(etags.stripPrefix("\"").stripSuffix("\""))
    }
  }

  private def extractSecurityDoc(json: String) =
    serializer.fromJson[SecurityDoc](json)

}

/** A security document is a special document for couchdb. It has no `_id` or
 *  `_rev` field.
 *
 *  @author Lucas Satabin
 */
case class SecurityDoc(admins: SecurityList = EmptySecurityList, members: SecurityList = EmptySecurityList)

case class SecurityList(names: List[String] = Nil, roles: List[String] = Nil)
object EmptySecurityList extends SecurityList()

/** A design gives access to the different views.
 *  Use this class to get or create new views.
 *
 *  @author Lucas Satabin
 */
case class Design(db: Database,
                  name: String,
                  language: String) {

  import db.couch.serializer

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
  def saveView(viewName: String,
               mapFun: String,
               reduceFun: Option[String] = None) = {
    val view = ViewDoc(mapFun, reduceFun)
    getDesignDocument.map {
      case Some(design) =>
        // the updated design
        design.copy(views = design.views + (viewName -> view))
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(viewName -> view), None)
    }.flatMap(db.saveDoc(_).map(_.isDefined))
  }

  /** Deletes the view with the given name from the design */
  def deleteView(viewName: String) = {
    getDesignDocument.flatMap {
      case Some(design) =>
        db.saveDoc(design.copy(views = design.views - viewName)).map(_.isDefined)
      case None => Http.promise(false)
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

  /** Creates or updates the document validation function.
   *  If the design does not exist yet, it is created.
   */
  def saveValidateFunction(validateFun: String) = {
    getDesignDocument.map {
      case Some(design) =>
        // the updated design
        design.copy(validate_doc_update = Some(validateFun))
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(), Some(validateFun))
    }.flatMap(db.saveDoc(_).map(_.isDefined))
  }

  /** Deletes the document validation function from the design */
  def deleteValidateFunction = {
    getDesignDocument.flatMap {
      case Some(design) =>
        db.saveDoc(design.copy(validate_doc_update = None)).map(_.isDefined)
      case None => Http.promise(false)
    }
  }

  // helper methods

  private def designDoc(json: String) =
    serializer.fromJson[DesignDoc](json)

}

/** A view can be queried to get the result.
 *
 *  @author Lucas Satabin
 */
case class View[Key: Manifest, Value: Manifest, Doc: Manifest](design: Design,
                                                               view: String) {

  import design.db.couch.serializer

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

    // build options
    val options = List(
      key.map(k => "key" -> serializer.toJson(k)),
      if (keys.nonEmpty) Some("keys" -> serializer.toJson(keys)) else None,
      startkey.map(k => "startkey" -> serializer.toJson(k)),
      startkey_docid.map("startkey_docid" -> _),
      endkey.map(k => "endkey" -> serializer.toJson(k)),
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

    design.db.couch.http(request <<? options).map(viewResult[Key,Value,Doc])/*.map { raw =>
      ViewResult(raw.total_rows, raw.offset,
        raw.rows.map { raw =>
          Row(raw.id,
            serializer.fromJson[Key](raw.key),
            serializer.fromJson[Value](raw.value),
            raw.doc.map(serializer.fromJson[Doc]))
        })
    }*/

  }

  // helper methods

  private def viewResult[Key: Manifest, Value: Manifest, Doc: Manifest](json: String) = {
    import LiftJsonSerializer.formats
    val ast = parse(json)
    val res = for {
      total_rows <- (ast \ "total_rows").extractOpt[Int]
      offset <- (ast \ "offset").extractOpt[Int]
      JArray(rows) = (ast \ "rows")
    } yield ViewResult(total_rows, offset, rows.flatMap { row =>
        for {
          id <- (row \ "id").extractOpt[String]
          key <- (row \ "key").extractOpt[Key]
          value <- (row \ "value").extractOpt[Value]
          doc = (row \ "doc").extractOpt[Doc]
        } yield Row(id, key, value, doc)
    })
    res.getOrElse(ViewResult(0, 0, Nil))
  }

}

// the different object that may be returned by the couchdb server

private[sohva] case class DesignDoc(_id: String,
                                    language: String,
                                    views: Map[String, ViewDoc],
                                    validate_doc_update: Option[String],
                                    val _rev: Option[String] = None)

private[sohva] case class ViewDoc(map: String,
                                  reduce: Option[String])

final case class OkResult(ok: Boolean, id: Option[String], rev: Option[String])

final case class InfoResult(compact_running: Boolean,
                            db_name: String,
                            disk_format_version: Int,
                            disk_size: Int,
                            doc_count: Int,
                            doc_del_count: Int,
                            instance_start_time: String,
                            purge_seq: Int,
                            update_seq: Int)

final case class DocUpdate(ok: Boolean,
                           id: String,
                           rev: String)

final case class ViewResult[Key, Value, Doc](total_rows: Int,
                                             offset: Int,
                                             rows: List[Row[Key, Value, Doc]]) {

  def values =
    rows.map(row => (row.key, row.value)).toMap

  def docs =
    rows.map(row => row.doc.map(_ => (row.key, row.doc))).flatten.toMap

  def foreach(f: Row[Key, Value, Doc] => Unit) =
    rows.foreach(f)

}

case class Row[Key, Value, Doc](id: String,
                                key: Key,
                                value: Value,
                                doc: Option[Doc] = None)

final case class ErrorResult(error: String, reason: String)

final case class Attachment(content_type: String,
                            revpos: Int,
                            digest: String,
                            length: Int,
                            stub: Boolean)

private[sohva] final case class Uuids(uuids: List[String])

class CouchException(val status: Int, val detail: Option[ErrorResult])
  extends Exception("status: " + status + "\nbecause: " + detail)
class ConflictException(detail: Option[ErrorResult]) extends CouchException(409, detail)
