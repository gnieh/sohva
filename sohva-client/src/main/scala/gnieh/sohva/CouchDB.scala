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

import strategy._

import dispatch._
import Defaults._
import retry.{
  CountingRetry,
  Success
}
import stream.Strings

import resource._

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
  def database(name: String, credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Database =
    new Database(name, this, credit, strategy)

  /** Returns the names of all databases in this couch instance. */
  def _all_dbs: Result[List[String]] =
    for(dbs <- http(request / "_all_dbs").right)
      yield asStringList(dbs)

  /** Returns the requested number of UUIDS (by default 1). */
  def _uuids(count: Int = 1): Result[List[String]] =
    for(uuids <- http(request / "_uuids" <<? Map("count" -> count.toString)).right)
      yield asUuidsList(uuids)

  /** Indicates whether this couchdb instance contains the given database */
  def contains(dbName: String): Result[Boolean] =
    for(dbs <- _all_dbs.right)
      yield dbs.contains(dbName)

  // user management section

  /** Exposes the interface for managing couchdb users. */
  object users {

    /** The user database name. By default `_users`. */
    var dbName: String = "_users"

    private def userDb = self.database(dbName)

    /** Adds a new user with the given role list to the user database,
     *  and returns the new instance.
     */
    def add(name: String,
            password: String,
            roles: List[String] = Nil): Result[Boolean] = {

      val user = CouchUser(name, password, roles)

      for(res <- http((request / dbName / user._id << serializer.toJson(user)).PUT).right)
        yield ok(res)

    }

    /** Deletes the given user from the database. */
    def delete(name: String): Result[Boolean] =
      database(dbName).deleteDoc("org.couchdb.user:" + name)

    /** Generates a password reset token for the given user with the given validity and returns it */
    def generateResetToken(name: String, until: Date): Result[Option[String]] =
      for {
        tokens <- _uuids().right
        user <- userDb.getDocById[PasswordResetUser]("org.couchdb.user:" + name).right
        token <- generate(user, tokens, until)
      } yield token

    private[this] def generate(user: Option[PasswordResetUser], tokens: List[String], until: Date) =
      user match {
        case Some(user) =>
          val List(token) = tokens
          // enrich the user document with password reset information
          val (token_salt, token_sha) = passwordSha(token)
          val u =
            user.copy(
              reset_token_sha = Some(token_sha),
              reset_token_salt = Some(token_salt),
              reset_validity = Some(until))
          // save back the enriched user document
          for (user <- userDb.saveDoc(u).right)
            yield user.map(_ => token)
        case None =>
          Future.successful(Right(None))
      }

    /** Resets the user password to the given one if:
     *   - a password reset token exists in the database
     *   - the token is still valid
     *   - the saved token matches the one given as parameter
     */
    def resetPassword(name: String, token: String, password: String): Result[Boolean] =
      for {
        user <- userDb.getDocById[PasswordResetUser]("org.couchdb.user:" + name).right
        ok <- reset(user, token, password)
      } yield ok

    private[this] def reset(user: Option[PasswordResetUser], token: String, password: String) =
      user match {
        case Some(user) =>
          // check the token with the one in the database (if still valid)
          (user.reset_token_sha, user.reset_token_salt, user.reset_validity) match {
            case (Some(savedToken), Some(savedSalt), Some(validity)) =>
              val saltedToken = hash(token + savedSalt)
              if(new Date().before(validity) && savedToken == saltedToken) {
                // save the user with the new password
                val newUser = new CouchUser(user.name, password, roles = user.roles, _rev = user._rev)
                http((request / dbName / user._id << serializer.toJson(newUser)).PUT).right.map(ok _)
              } else {
                Future.successful(Right(false))
              }
            case _ =>
              Future.successful(Right(false))
          }
        case None =>
          Future.successful(Right(false))
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

  private[sohva] def passwordSha(password: String) = {

    // compute the password hash
    // the password string is concatenated with the generated salt
    // and the result is hashed using SHA-1
    val saltArray = new Array[Byte](16)
    Random.nextBytes(saltArray)
    val salt = bytes2string(saltArray)

    (salt, hash(password + salt))
  }


  private[sohva] def http(request: RequestBuilder): Result[String] =
    _http(request > handleCouchResponse _)

  private[sohva] def optHttp(request: RequestBuilder): Result[Option[String]] =
    _http(request > handleOptionalCouchResponse _)

  private def handleCouchResponse(response: Response): Either[(Int, Option[ErrorResult]), String] = {
    val json = as.String(response)
    val code = response.getStatusCode
    if (code / 100 != 2) {
      // something went wrong...
      val error = serializer.fromJsonOpt[ErrorResult](json)
      Left((code, error))
    } else {
      Right(json)
    }
  }

  private def handleOptionalCouchResponse(response: Response): Either[(Int, Option[ErrorResult]), Option[String]] =
    handleCouchResponse(response) match {
      case Right(v) => Right(Some(v))
      case Left((404, _)) => Right(None)
      case Left(err) => Left(err)
    }

  private[sohva] def ok(json: String) =
    serializer.fromJson[OkResult](json).ok

  private def asStringList(json: String) =
    serializer.fromJson[List[String]](json)

  private def asUuidsList(json: String) =
    serializer.fromJson[Uuids](json).uuids

}

/** Gives the user access to the different operations available on a database.
 *  Among other operations this is the key class to get access to the documents
 *  of this database.
 *
 *  It also exposes the change handler interface, that allows people to react to change notifications. This
 *  is a low-level API, that handles raw Json objects
 *
 * @param credit The credit assigned to the conflict resolver. It represents the number of times the client tries to save the document before giving up.
 *  @param strategy The strategy being used to resolve conflicts
 *
 *  @author Lucas Satabin
 */
class Database(val name: String,
               private[sohva] val couch: CouchDB,
               val credit: Int,
               val strategy: Strategy) {

  self =>

  import couch.serializer

  /* the resolver is responsible for applying the merging strategy on conflict and retrying
   * to save the document after resolution process */
  private object resolver extends CountingRetry {
    // only retry if a conflict occurred, other errors are considered as 'Success'
    val saveOk = new Success[Either[(Int, Option[ErrorResult]), String]] ({
      case Left((409, _)) => false
      case _              => true
    })
    def apply(credit: Int, docId: String, baseRev: Option[String], doc: String): Result[String] = {
      def doit(count: Int): Result[String] =
        // get the base document if any
        getRawDocById(docId, baseRev) flatMap {
          case Right(base) =>
            // get the last document from database
            getRawDocById(docId) flatMap {
              case Right(last) =>
                // apply the merge strategy between base, last and current revision of the document
                val baseDoc = base map (parse _)
                val lastDoc = last map (parse _)
                val lastRev = lastDoc map (d => (d \ "_rev").toString)
                val currentDoc = parse(doc)
                val resolvedDoc = strategy(baseDoc, lastDoc, currentDoc)
                val resolved = compact(render(resolvedDoc))
                resolver(count, docId, lastRev, resolved)
              case Left(res) =>
                // some other error occurred
                Future.successful(Left(res))
            }
          case Left(res) =>
            // some other error occurred
            Future.successful(Left(res))
        }

      retry(credit,
        () => couch.http((request / docId << doc).PUT),
        saveOk,
        doit)
    }

  }

  /* the change notifier is responsible for managing the change handlers
   * and also for managing the change request and response stream */
  private object changeNotifier {

    import scala.collection.mutable.Map

    private def request = self.request / "_changes"

    // own instance of http that can be shutted down when no more listeners
    private var http: Option[Strings[Unit]] = None

    // holds all change handlers that are currently registered for this database
    private val handlers = Map.empty[Int, (String, Option[JObject]) => Unit]

    // this is called with each new received line. if the line is empty, this is
    // just a heartbeat, otherwise, notify all registered handlers
    private def onChange(json: String) = synchronized {
      if(json != null & json.nonEmpty) {
        serializer.fromJsonOpt[Change](json) map { change =>
          handlers.foreach {
            case (_, h) =>
              h(change.id, change.doc)
          }
        }
      }
    }

    // starts the background task
    private def start {
      if(http.isEmpty) {
        // send a continuous feed request started from current update sequence
        // thus we do not get all the changes since the beginning of the times
        // but only new changes up from now
        // create a new stream handler
        lazy val handler = as.stream.Lines(onChange)
        for {
          info <- couch._http(self.request OK as.String).map(infoResult)
          _ <- couch._http(request <<? Map(
              "feed" -> "continuous",
              "since" -> info.update_seq.toString,
              "include_docs" -> "true",
              "heartbeat" -> "15000"
            ) > handler
          )
        } {}
        http = Some(handler)
      }
    }

    // stops the background task
    private def stop {
      http map { h =>
        h.stop
        http = None
      }
    }

    /* add a new handler and return its identifier */
    def addHandler(handler: (String, Option[JObject]) => Unit) = synchronized {
      val id = handler.hashCode
      handlers(id) = handler
      if(http.isEmpty)
        start
      id
    }

    /* remove a handler by id */
   def removeHandler(id: Int) = synchronized {
     handlers -= id
     // no more handlers registered, we do not need to get notified
     // about changes anymore
     if(handlers.isEmpty)
       stop
   }

  }

  /** Returns the information about this database */
  def info: Result[Option[InfoResult]] =
    for(info <- couch.optHttp(request).right)
      yield info.map(infoResult)

  /** Indicates whether this database exists */
  @inline
  def exists: Result[Boolean] =
    couch.contains(name)

  /** Registers a handler that is executed everytime an update is done on the database.
   *  This handler is executed synchronously on received change,
   *  thus any potentially blocking task must be done asynchronously in the handler to avoid
   *  blocking the update mechanism.
   *  The identifier of this update handler is immediately returned. It can then be used
   *  to dynamically unregister the handler.
   *  Registering a new change handler does not result in a new request being sent. At most one
   *  request is sent per database, no matter how many handlers there are.
   */
  def onChange(action: (String, Option[JObject]) => Unit): Int =
    changeNotifier.addHandler(action)

  def unregisterHandler(id: Int) {
    changeNotifier.removeHandler(id)
  }

  /** Creates this database in the couchdb instance if it does not already exist.
   *  Returns <code>true</code> iff the database was actually created.
   */
  def create: Result[Boolean] =
    for {
      exist <- exists.right
      ok <- create(exist)
    } yield ok

  private[this] def create(exist: Boolean) =
    if(exist) {
      Future.successful(Right(false))
    } else {
      for(result <- couch.http(request.PUT).right)
        yield couch.ok(result)
    }

  /** Deletes this database in the couchdb instance if it exists.
   *  Returns <code>true</code> iff the database was actually deleted.
   */
  def delete: Result[Boolean] =
    for {
      exist <- exists.right
      ok <- delete(exist)
      } yield ok

  private[this] def delete(exist: Boolean) =
    if(exist) {
      for(result <- couch.http(request.DELETE).right)
        yield couch.ok(result)
    } else {
      Future.successful(Right(false))
    }

  /** Returns the document identified by the given id if it exists */
  def getDocById[T: Manifest](id: String, revision: Option[String] = None): Result[Option[T]] =
    for(raw <- getRawDocById(id, revision).right)
      yield raw.map(docResult[T])

  /** Returns the raw document (as a string representing a json object) identified by the given id if it exists */
  def getRawDocById(id: String, revision: Option[String] = None): Result[Option[String]] =
    couch.optHttp(request / id <<? revision.map("rev" -> _).toList)

  /** Returns the current revision of the document if it exists */
  def getDocRevision(id: String): Result[Option[String]] =
    couch._http((request / id).HEAD > extractRev _)

  /** Creates or updates the given object as a document into this database
   *  The given object must have an `_id` and an optional `_rev` fields
   *  to conform to the couchdb document structure.
   */
  def saveDoc[T: Manifest](doc: T with Doc): Result[Option[T]] =
    for {
      upd <- resolver(credit, doc._id, doc._rev, serializer.toJson(doc)).right
      res <- update(docUpdateResult(upd))
    } yield res

  private[this] def update[T: Manifest](res: DocUpdate) = res match {
    case DocUpdate(true, id, _) =>
      getDocById[T](id)
    case DocUpdate(false, _, _) =>
      Future.successful(Right(None))
  }

  /** Deletes the document from the database.
   *  The document will only be deleted if the caller provided the last revision
   */
  def deleteDoc[T: Manifest](doc: T with Doc): Result[Boolean] =
    for(res <- couch.http((request / doc._id).DELETE <<? Map("rev" -> doc._rev.getOrElse(""))).right)
      yield couch.ok(res)

  /** Deletes the document identified by the given id from the database.
   *  If the document exists it is deleted and the method returns `true`,
   *  otherwise returns `false`.
   */
  def deleteDoc(id: String): Result[Boolean] =
    for {
      rev <- getDocRevision(id).right
      res <- delete(rev, id)
    } yield res

  private[this] def delete(rev: Option[String], id: String) =
    rev match {
      case Some(rev) =>
        for(res <- couch.http((request / id).DELETE <<? Map("rev" -> rev)).right)
          yield couch.ok(res)
      case None =>
        Future.successful(Right(false))
    }

  /** Attaches the given file to the given document id.
   *  If no mime type is given, sohva tries to guess the mime type of the file
   *  itself. It it does not manage to identify the mime type, the file won't be
   *  attached...
   *  This method returns `true` iff the file was attached to the document.
   */
  def attachTo(docId: String, file: File, contentType: Option[String]): Result[Boolean] = {
    val mime = contentType match {
      case Some(mime) => mime
      case None       => MimeUtil.getMimeType(file)
    }

    if (mime == MimeUtil.UNKNOWN_MIME_TYPE) {
      Future.successful(Right(false)) // unknown mime type, cannot attach the file
    } else {
      // first get the last revision of the document (if it exists)
      for {
        rev <- getDocRevision(docId).right
        res <- couch.http(request / docId / file.getName <<? attachParams(rev) <<< file <:< Map("Content-Type" -> mime)).right
      } yield couch.ok(res)
    }
  }

  private[this] def attachParams(rev: Option[String]) =
    rev match {
      case Some(r) =>
        List("rev" -> r)
      case None =>
        // doc does not exist? well... good... does it matter? no!
        // couchdb will create it for us, don't worry
        Nil
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
               contentType: Option[String]): Result[Boolean] = {
    // create a temporary file with the content of the input stream
    val file = File.createTempFile(attachment, null)
    for(fos <- managed(new FileOutputStream(file))) {
      for(bis <- managed(new BufferedInputStream(stream))) {
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
  def getAttachment(docId: String, attachment: String): Result[Option[(String, InputStream)]] =
    couch._http(request / docId / attachment > readFile _)

  /** Deletes the given attachment for the given docId */
  def deleteAttachment(docId: String, attachment: String): Result[Boolean] =
    for {
      // first get the last revision of the document (if it exists)
      rev <- getDocRevision(docId).right
      res <- deleteAttachment(docId, attachment, rev)
    } yield res

  private[this] def deleteAttachment(docId: String, attachment: String, rev: Option[String]) =
    rev match {
      case Some(r) =>
        for(res <- couch.http((request / docId / attachment <<?
          List("rev" -> r)).DELETE).right)
            yield couch.ok(res)
      case None =>
        // doc does not exist? well... good... just do nothing
        Future.successful(Right(false))
    }

  /** Returns the security document of this database if any defined */
  def securityDoc: Result[SecurityDoc] =
    for(doc <- couch.http(request / "_security").right)
      yield extractSecurityDoc(doc)

  /** Creates or updates the security document.
   *  Security documents are special documents with no `_id` nor `_rev` fields.
   */
  def saveSecurityDoc(doc: SecurityDoc): Result[Boolean] =
    for(res <- couch.http((request / "_security" << serializer.toJson(doc)).PUT).right)
      yield couch.ok(res)

  /** Returns a design object that allows user to work with views */
  def design(designName: String, language: String = "javascript"): Design =
    Design(this, designName, language)

  // helper methods

  private[sohva] def request =
    couch.request / name

  private def readFile(response: Response) = {
    val code = response.getStatusCode
    if (code == 404) {
      Right(None)
    } else if (code / 100 != 2) {
      // something went wrong...
      val error = serializer.fromJsonOpt[ErrorResult](as.String(response))
      Left((code, error))
    } else {
      Right(Some(response.getContentType, response.getResponseBodyAsStream))
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
    val code = response.getStatusCode
    if (code == 404) {
      Right(None)
    } else if (code / 100 != 2) {
      // something went wrong...
      val error = serializer.fromJsonOpt[ErrorResult](as.String(response))
      Left((code, error))
    } else {
      Right(response.getHeader("Etag") match {
        case null | "" => None
        case etags =>
          Some(etags.stripPrefix("\"").stripSuffix("\""))
      })
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
  def getDesignDocument: Result[Option[DesignDoc]] =
    for(design <- db.couch.optHttp(request).right)
      yield design.map(designDoc)

  /** Deletes this design document from the couchdb instance */
  def delete: Result[Boolean] =
    db.deleteDoc("_design/" + name.trim)

  /** Creates or updates the view in this design
   *  with the given name, map function and reduce function.
   *  If the design does not exist yet, it is created.
   */
  def saveView(viewName: String,
               mapFun: String,
               reduceFun: Option[String] = None): Result[Boolean] = {
    val view = ViewDoc(mapFun, reduceFun)
    for {
      design <- getDesignDocument.right
      doc <- db.saveDoc(newDoc(design, viewName, view)).right
    } yield doc.isDefined
  }

  private[this] def newDoc(design: Option[DesignDoc], viewName: String, view: ViewDoc) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(views = design.views + (viewName -> view))
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(viewName -> view), None)
    }

  /** Deletes the view with the given name from the design */
  def deleteView(viewName: String): Result[Boolean] =
   for {
     design <- getDesignDocument.right
     res <- deleteView(design, viewName)
   } yield res

  private[this] def deleteView(design: Option[DesignDoc], viewName: String) =
    design match {
      case Some(design) =>
        db.saveDoc(design.copy(views = design.views - viewName)).right.map(_.isDefined)
      case None => Future.successful(Right(false))
    }

  /** Returns the (typed) view in this design document.
   *  The different types are:
   *  - Key: type of the key for this view
   *  - Value: Type of the value returned in the result
   *  - Doc: Type of the full document in the case where the view is queried with `include_docs` set to `true`
   */
  def view[Key: Manifest, Value: Manifest, Doc: Manifest](viewName: String): View[Key, Value, Doc] =
    View[Key, Value, Doc](this, viewName)

  /** Creates or updates the document validation function.
   *  If the design does not exist yet, it is created.
   */
  def saveValidateFunction(validateFun: String): Result[Boolean] =
    for {
      design <- getDesignDocument.right
      res <- db.saveDoc(newDoc(design, validateFun)).right
    } yield res.isDefined

  private[this] def newDoc(design: Option[DesignDoc], validateFun: String) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(validate_doc_update = Some(validateFun))
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(), Some(validateFun))
    }

  /** Deletes the document validation function from the design */
  def deleteValidateFunction: Result[Boolean] =
    for {
      design <- getDesignDocument.right
      res <- deleteValidateFunction(design)
    } yield res

  private[this] def deleteValidateFunction(design: Option[DesignDoc]) =
    design match {
      case Some(design) =>
        for(doc <- db.saveDoc(design.copy(validate_doc_update = None)).right)
          yield doc.isDefined
      case None => Future.successful(Right(false))
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
  import serializer.formats

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
            update_seq: Boolean = false): Result[ViewResult[Key, Value, Doc]] = {

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

    for(res <- design.db.couch.http(request <<? options).right)
      yield viewResult[Key,Value,Doc](res)

  }

  // helper methods

  private def viewResult[Key: Manifest, Value: Manifest, Doc: Manifest](json: String) = {
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
