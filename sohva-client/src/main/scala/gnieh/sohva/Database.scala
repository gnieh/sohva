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

import resource._

import strategy.Strategy

import java.io.{
  File,
  InputStream,
  FileOutputStream,
  ByteArrayInputStream,
  BufferedInputStream
}

import org.slf4j.LoggerFactory

import spray.json._

import gnieh.diffson.JsonPatch

import scala.concurrent.Future

import scala.util.Try

import spray.http._
import spray.client.pipelining._
import spray.httpx.marshalling._

import akka.actor._

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
class Database private[sohva] (
    val name: String,
    val couch: CouchDB,
    val credit: Int,
    val strategy: Strategy) extends SprayJsonSupport {

  implicit def ec =
    couch.ec

  import SohvaProtocol._

  /* the resolver is responsible for applying the merging strategy on conflict and retrying
   * to save the document after resolution process */
  private def resolver(credit: Int, docId: String, baseRev: Option[String], current: JsValue): Future[JsValue] = current match {
    case JsNull =>
      LoggerFactory.getLogger(getClass).info("No document to save")
      Future.successful(DocUpdate(true, docId, baseRev.getOrElse("")).toJson)
    case _ =>
      couch.http(Put(uri / docId, current)).recoverWith {
        case exn @ ConflictException(_) if credit > 0 =>
          LoggerFactory.getLogger(getClass).info("Conflict occurred, try to resolve it")
          // try to resolve the conflict and save again
          for {
            // get the base document if any
            base <- getRawDocById(docId, baseRev)
            // get the last document
            last <- getRawDocById(docId)
            // apply the merge strategy between base, last and current revision of the document
            lastRev = last collect {
              case JsObject(fs) if fs.contains("_rev") => fs("_rev").convertTo[String]
            }
            resolved = strategy(base, last, current)
            res <- resolved match {
              case Some(resolved) => resolver(credit - 1, docId, lastRev, resolved)
              case None           => Future.failed(exn)
            }
          } yield res
      } withFailureMessage f"Unable to resolve document with ID $docId at revision $baseRev"
  }

  /** Returns the information about this database */
  def info: Future[Option[InfoResult]] =
    for (info <- couch.optHttp(Get(uri)) withFailureMessage f"info failed for $uri")
      yield info.map(infoResult)

  /** Indicates whether this database exists */
  def exists: Future[Boolean] =
    for (r <- couch.rawHttp(Head(uri)) withFailureMessage f"exists failed for $uri")
      yield r.status == StatusCodes.OK

  /** Registers to the change stream of this database with potential filter and
   *  since some revision. If no revision is given changes that occurred before the
   *  connection was established are not sent
   */
  def changes(since: Option[Int] = None, filter: Option[String] = None): ChangeStream =
    new ChangeStream(this, since, filter)

  /** Creates this database in the couchdb instance if it does not already exist.
   *  Returns <code>true</code> iff the database was actually created.
   */
  def create: Future[Boolean] =
    (for {
      exist <- exists
      ok <- create(exist)
    } yield ok) withFailureMessage f"Failed while creating database at $uri"

  private[this] def create(exist: Boolean) =
    if (exist) {
      Future.successful(false)
    } else {
      for (result <- couch.http(Put(uri)) withFailureMessage f"Failed while creating database at $uri")
        yield couch.ok(result)
    }

  /** Deletes this database in the couchdb instance if it exists.
   *  Returns <code>true</code> iff the database was actually deleted.
   */
  def delete: Future[Boolean] =
    (for {
      exist <- exists
      ok <- delete(exist)
    } yield ok) withFailureMessage "Failed to delete database"

  private[this] def delete(exist: Boolean) =
    if (exist) {
      for (result <- couch.http(Delete(uri)) withFailureMessage f"Failed to delete database at $uri")
        yield couch.ok(result)
    } else {
      Future.successful(false)
    }

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
    inclusive_end: Boolean = true): Future[List[String]] =
    for {
      res <- builtInView("_all_docs").query[String, Map[String, String], JsObject](
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
      ) withFailureMessage f"Failed to access _all_docs view for $uri"
    } yield for (Row(Some(id), _, _, _) <- res.rows) yield id

  /** Returns the document identified by the given id if it exists */
  def getDocById[T: JsonReader](id: String, revision: Option[String] = None): Future[Option[T]] =
    (for (raw <- getRawDocById(id, revision))
      yield raw.map(docResult[T])
    ) withFailureMessage f"Failed to fetch document by ID $id and revision $revision"

  /** Returns the raw repsentation of the document identified by the given id if it exists */
  def getRawDocById(id: String, revision: Option[String] = None): Future[Option[JsValue]] =
    couch.optHttp(Get(uri / id <<? revision.flatMap(r => if (r.nonEmpty) Some("rev" -> r) else None))) withFailureMessage
      f"Failed to fetch the raw document by ID $id at revision $revision from $uri"

  /** Returns all the documents with given identifiers and of the given type.
   *  If the document with an identifier exists in the database but has not the
   *  required type, it is not added to the result
   */
  def getDocsById[T: JsonReader](ids: List[String]): Future[List[T]] =
    for {
      res <- builtInView("_all_docs").query[String, JsValue, T](keys = ids, include_docs = true)
    } yield res.rows.flatMap { case Row(_, _, _, doc) => doc }

  /** Returns the current revision of the document if it exists */
  def getDocRevision(id: String): Future[Option[String]] =
    couch.rawHttp(Head(uri / id)).flatMap(extractRev _) withFailureMessage
      f"Failed to fetch document revision by ID $id from $uri"

  /** Returns the current revision of the documents */
  def getDocRevisions(ids: List[String]): Future[List[(String, String)]] =
    for {
      res <- builtInView("_all_docs").query[String, Map[String, String], JsObject](keys = ids) withFailureMessage
        f"Failed to fetch document revisions by IDs $ids from $uri"
    } yield res.rows.map { case Row(Some(id), _, value, _) => (id, value("rev")) }

  /** Creates or updates the given object as a document into this database
   *  The given object must have an `_id` and an optional `_rev` fields
   *  to conform to the couchdb document structure.
   *  The saved revision is returned. If something went wrong, an exception is raised
   */
  def saveDoc[T: CouchFormat](doc: T): Future[T] = {
    val format = implicitly[CouchFormat[T]]
    (for {
      upd <- resolver(credit, format._id(doc), format._rev(doc), doc.toJson)
      res <- update[T](upd.convertTo[DocUpdate])
    } yield res) withFailureMessage f"Unable to save document with ID ${format._id(doc)} at revision ${format._rev(doc)}"
  }

  def saveRawDoc(doc: JsValue): Future[JsValue] = doc match {
    case JsObject(fields) =>
      val idRev = for {
        id <- fields.get("_id").map(_.convertTo[String])
        rev = fields.get("_rev").map(_.convertTo[String])
      } yield (id, rev)
      idRev match {
        case Some((id, rev)) =>
          (for {
            upd <- resolver(credit, id, rev, doc)
            res <- updateRaw(docUpdateResult(upd))
          } yield res) withFailureMessage f"Failed to update raw document with ID $id and revision $rev"
        case None =>
          Future.failed(new SohvaException(f"Not a couchdb document: ${doc.prettyPrint}"))
      }
    case _ =>
      Future.failed(new SohvaException(f"Not a couchdb document: ${doc.prettyPrint}"))
  }

  private[this] def update[T: JsonReader](res: DocUpdate) = res match {
    case DocUpdate(true, id, rev) =>
      getDocById[T](id, Some(rev)).map(_.get)
    case DocUpdate(false, id, _) =>
      Future.failed(new SohvaException(f"Document $id could not be saved"))
  }

  private[this] def updateRaw(res: DocUpdate) = res match {
    case DocUpdate(true, id, rev) =>
      getRawDocById(id, Some(rev)).map(_.get)
    case DocUpdate(false, id, _) =>
      Future.failed(new SohvaException("Document $id could not be saved"))
  }

  /** Creates or updates a bunch of documents into the database. */
  def saveDocs[T: CouchFormat](docs: List[T], all_or_nothing: Boolean = false): Future[List[DbResult]] =
    for {
      raw <- couch.http(Post(uri / "_bulk_docs", BulkSave(all_or_nothing, docs.map(_.toJson)).toJson)) withFailureMessage
        f"Failed to bulk save documents to $uri"
    } yield bulkSaveResult(raw)

  def saveRawDocs(docs: List[JsValue], all_or_nothing: Boolean = false): Future[List[DbResult]] =
    for {
      raw <- couch.http(Post(uri / "_bulk_docs", JsObject(Map("all_or_nothing" -> JsBoolean(all_or_nothing), "docs" -> JsArray(docs.toVector))))) withFailureMessage
        f"Failed to bulk save documents to $uri"
    } yield bulkSaveResult(raw)

  private[this] def bulkSaveResult(json: JsValue) =
    json.convertTo[List[DbResult]]

  /** Creates a document in the database and returns its identifier and revision.
   *  If the json version of the object has a `_id` field, this identifier is used for the document,
   *  otherwise a new one is generated.
   */
  def createDoc[T: JsonWriter](doc: T): Future[DbResult] =
    doc.toJson match {
      case json @ JsObject(fields) if fields.contains("_id") =>
        for (res <- saveRawDoc(json))
          yield res.convertTo[DbResult]
      case json =>
        for {
          raw <- couch.http(Post(uri, json)).withFailureMessage(f"Failed to create new document into $uri")
          DocUpdate(ok, id, rev) = docUpdateResult(raw)
        } yield OkResult(ok, Some(id), Some(rev))
    }

  /** Creates a set of documents in the database and returns theirs identifiers and revision.
   *  If the json version of an object has a `_id` field, this identifier is used for the document,
   *  otherwise a new one is generated.
   */
  def createDocs[T: JsonWriter](docs: List[T]): Future[List[DbResult]] =
    saveRawDocs(docs.map(_.toJson))

  /** Copies the origin document to the target document.
   *  If the target does not exist, it is created, otherwise it is updated and the target
   *  revision must be provided
   */
  def copy(origin: String, target: String, originRev: Option[String] = None, targetRev: Option[String] = None): Future[Boolean] =
    for (
      res <- couch.http(Copy(uri / origin <<? originRev.map("rev" -> _))
        <:< Map("Destination" -> (target + targetRev.map("?rev=" + _).getOrElse("")))
      ) withFailureMessage f"Failed to copy from $origin at $originRev to $target at $targetRev from $uri"
    ) yield couch.ok(res)

  /** Patches the document identified by the given identifier in the given revision.
   *  This will work if the revision is the last one, or if it is not but the automatic
   *  conflict manager manages to solve the potential conflicts.
   *  The patched revision is returned. If something went wrong, an exception is raised
   */
  def patchDoc[T: CouchFormat](id: String, rev: String, patch: JsonPatch): Future[T] =
    (for {
      doc <- getDocById[T](id, Some(rev))
      res <- patchDoc(id, doc, patch)
    } yield res) withFailureMessage "Failed to patch document with ID $id at revision $rev"

  private[this] def patchDoc[T: CouchFormat](id: String, doc: Option[T], patch: JsonPatch) = doc match {
    case Some(doc) =>
      val format = implicitly[CouchFormat[T]]
      saveDoc(format.withRev(patch(doc), format._rev(doc)))
    case None =>
      Future.failed(new SohvaException("Uknown document to patch: " + id))
  }

  /** Deletes the document from the database.
   *  The document will only be deleted if the caller provided the last revision
   */
  def deleteDoc[T: CouchFormat](doc: T): Future[Boolean] = {
    val format = implicitly[CouchFormat[T]]
    for (
      res <- couch.http(Delete(uri / format._id(doc) <<? Map("rev" -> format._rev(doc).getOrElse("")))) withFailureMessage
        f"Failed to delete document with ID ${format._id(doc)} at revision ${format._rev(doc)} from $uri"
    ) yield couch.ok(res)
  }

  /** Deletes the document identified by the given id from the database.
   *  If the document exists it is deleted and the method returns `true`,
   *  otherwise returns `false`.
   */
  def deleteDoc(id: String): Future[Boolean] =
    (for {
      rev <- getDocRevision(id)
      res <- delete(rev, id)
    } yield res) withFailureMessage f"Failed to delete document with ID $id"

  private[this] def delete(rev: Option[String], id: String) =
    rev match {
      case Some(rev) =>
        for (
          res <- couch.http(Delete(uri / id <<? Map("rev" -> rev))) withFailureMessage
            f"Failed to delete document with ID $id from $uri"
        ) yield couch.ok(res)
      case None =>
        Future.successful(false)
    }

  /** Deletes a bunch of documents at once returning the results
   *  for each identifier in the document list. One can choose the update strategy
   *  by setting the parameter `all_or_nothing` to `true` or `false`.
   */
  def deleteDocs(ids: List[String], all_or_nothing: Boolean = false): Future[List[DbResult]] =
    for {
      revs <- getDocRevisions(ids)
      raw <- couch.http(
        Post(uri / "_bulk_docs",
          JsObject(
            Map(
              "all_or_nothing" -> all_or_nothing.toJson,
              "docs" -> revs.map {
                case (id, rev) => JsObject(
                  "_id" -> id.toJson,
                  "_rev" -> rev.toJson,
                  "_deleted" -> true.toJson)
              }.toJson
            )
          )
        )
      ) withFailureMessage f"Failed to bulk delete docs $ids from $uri"
    } yield bulkSaveResult(raw)

  /** Attaches the given file to the given document id.
   *  This method returns `true` iff the file was attached to the document.
   */
  def attachTo(docId: String, file: File, contentType: String): Future[Boolean] = {
    import MultipartMarshallers._
    // first get the last revision of the document (if it exists)
    for {
      rev <- getDocRevision(docId)
      res <- couch.http(
        Put(uri / docId / file.getName <<? rev.map("rev" -> _),
          HttpEntity(
            ContentType(MediaType.custom(contentType)),
            HttpData(file)
          )
        )
      ) withFailureMessage f"Failed to attach file ${file.getName} to document with ID $docId at $uri"
    } yield couch.ok(res)
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
    contentType: String): Future[Boolean] = {
    // create a temporary file with the content of the input stream
    val file = new File(System.getProperty("java.io.tmpdir"), attachment)
    for (fos <- managed(new FileOutputStream(file))) {
      for (bis <- managed(new BufferedInputStream(stream))) {
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
  def getAttachment(docId: String, attachment: String): Future[Option[(String, InputStream)]] =
    couch.rawHttp(Get(uri / docId / attachment)).flatMap(readFile) withFailureMessage
      f"Failed to get attachment $attachment for document ID $docId from $uri"

  /** Deletes the given attachment for the given docId */
  def deleteAttachment(docId: String, attachment: String): Future[Boolean] =
    for {
      // first get the last revision of the document (if it exists)
      rev <- getDocRevision(docId)
      res <- deleteAttachment(docId, attachment, rev)
    } yield res

  private[this] def deleteAttachment(docId: String, attachment: String, rev: Option[String]) =
    rev match {
      case Some(r) =>
        for (
          res <- couch.http(Delete(uri / docId / attachment <<?
            Map("rev" -> r))) withFailureMessage
            f"Failed to delete attachment $attachment for document ID $docId at revision $rev from $uri"
        ) yield couch.ok(res)
      case None =>
        // doc does not exist? well... good... just do nothing
        Future.successful(false)
    }

  /** Returns the security document of this database if any defined */
  def securityDoc: Future[SecurityDoc] =
    for (
      doc <- couch.http(Get(uri / "_security")) withFailureMessage
        f"Failed to fetch security doc from $uri"
    ) yield extractSecurityDoc(doc)

  /** Creates or updates the security document.
   *  Security documents are special documents with no `_id` nor `_rev` fields.
   */
  def saveSecurityDoc(doc: SecurityDoc): Future[Boolean] =
    for (
      res <- couch.http(Put(uri / "_security", doc.toJson)) withFailureMessage
        f"failed to save security document for $uri"
    ) yield couch.ok(res)

  /** Returns a design object that allows user to work with views */
  def design(designName: String, language: String = "javascript"): Design =
    new Design(this, designName, language)

  /** Returns a built-in view of this database, identified by its name.
   *  E.g. `_all_docs`.
   */
  def builtInView(view: String): View =
    new BuiltInView(this, view)

  /** Returns a temporary view of this database, specified by the `ViewDoc`. */
  def temporaryView(viewDoc: ViewDoc): View =
    new TemporaryView(this, viewDoc)

  // helper methods

  protected[sohva] def uri =
    couch.uri / name

  private def readFile(response: HttpResponse): Future[Option[(String, InputStream)]] = {
    if (response.status.intValue == 404) {
      Future.successful(None)
    } else if (response.status.isSuccess) {
      Future.successful(
        Some(
          response.headers.find(_.is("content-type")).map(_.value).getOrElse("application/json") ->
            new ByteArrayInputStream(response.entity.data.toByteArray)))
    } else {
      val code = response.status.intValue
      // something went wrong...
      val error = Try(JsonParser(response.entity.asString).convertTo[ErrorResult]).toOption
      Future.failed(CouchException(code, error))
    }
  }

  private def okResult(json: JsValue) =
    json.convertTo[OkResult]

  private def infoResult(json: JsValue) =
    json.convertTo[InfoResult]

  private def docResult[T: JsonReader](json: JsValue) =
    json.convertTo[T]

  private def docResultOpt[T: JsonReader](json: JsValue) =
    Try(docResult[T](json)).toOption

  private def docUpdateResult(json: JsValue) =
    json.convertTo[DocUpdate]

  private def extractRev(response: HttpResponse) = {
    if (response.status.intValue == 404) {
      Future.successful(None)
    } else if (response.status.isSuccess) {
      Future.successful(response.headers.find(_.is("etag")) map { etags =>
        etags.value.stripPrefix("\"").stripSuffix("\"")
      })
    } else {
      // something went wrong...
      val code = response.status.intValue
      val error = Try(JsonParser(response.entity.asString).convertTo[ErrorResult]).toOption
      Future.failed(new CouchException(code, error))
    }
  }

  private def extractSecurityDoc(json: JsValue) =
    json.convertTo[SecurityDoc]

  override def toString =
    uri.toString

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
private[sohva] final case class BulkSave(all_or_nothing: Boolean, docs: List[JsValue])
