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

import mango.{
  Selector,
  Sort,
  Query,
  Index,
  UseIndex,
  Explanation,
  SearchResult
}

import java.io.{
  File,
  InputStream,
  FileOutputStream,
  ByteArrayInputStream,
  BufferedInputStream
}

import spray.json._

import gnieh.diffson.sprayJson.JsonPatch

import scala.concurrent.Future

import scala.util.{
  Try,
  Success,
  Failure
}

import akka.http.scaladsl.model._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.unmarshalling._

import akka.stream.scaladsl._

import akka.actor._

import akka.util.ByteString

/**
 * Gives the user access to the different operations available on a database.
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
    val strategy: Strategy) extends DocumentOps {

  import SohvaProtocol._
  import SprayJsonSupport._

  implicit val ec =
    couch.ec

  import couch.materializer

  /** Returns the information about this database */
  def info: Future[Option[InfoResult]] =
    for (info <- couch.optHttp(HttpRequest(uri = uri)) withFailureMessage f"info failed for $uri")
      yield info.map(infoResult)

  /** Indicates whether this database exists */
  def exists: Future[Boolean] =
    for (r <- couch.rawHttp(HttpRequest(HttpMethods.HEAD, uri = uri)) withFailureMessage f"exists failed for $uri")
      yield r.status == StatusCodes.OK

  /** Exposes the interface to change stream for this database. */
  object changes extends ChangeStream(this)

  /**
   * Creates this database in the couchdb instance if it does not already exist.
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
      for (result <- couch.http(HttpRequest(HttpMethods.PUT, uri = uri)) withFailureMessage f"Failed while creating database at $uri")
        yield couch.ok(result)
    }

  /**
   * Deletes this database in the couchdb instance if it exists.
   *  Returns <code>true</code> iff the database was actually deleted.
   */
  def delete: Future[Boolean] =
    (for {
      exist <- exists
      ok <- delete(exist)
    } yield ok) withFailureMessage "Failed to delete database"

  private[this] def delete(exist: Boolean) =
    if (exist) {
      for (result <- couch.http(HttpRequest(HttpMethods.DELETE, uri = uri)) withFailureMessage f"Failed to delete database at $uri")
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

  /** Returns the raw repsentation of the document identified by the given id if it exists. */
  @deprecated("Use `getDocById` with return type `JsValue` instead", "2.0.0")
  def getRawDocById(id: String, revision: Option[String] = None): Future[Option[JsValue]] =
    getDocById[JsValue](id, revision)

  /**
   * Returns all the documents with given identifiers and of the given type.
   *  If the document with an identifier exists in the database but has not the
   *  required type, it is not added to the result
   */
  def getDocsById[T: JsonReader](ids: List[String]): Future[List[T]] =
    for {
      res <- builtInView("_all_docs").query[String, JsValue, T](keys = ids, include_docs = true)
    } yield res.rows.flatMap { case Row(_, _, _, doc) => doc }

  /** Returns the current revision of the document if it exists */
  def getDocRevision(id: String): Future[Option[String]] =
    couch.rawHttp(HttpRequest(HttpMethods.HEAD, uri = uri / id)).flatMap(extractRev _) withFailureMessage
      f"Failed to fetch document revision by ID $id from $uri"

  /** Returns the current revision of the documents */
  def getDocRevisions(ids: List[String]): Future[List[(String, String)]] =
    for {
      res <- builtInView("_all_docs").query[String, Map[String, String], JsObject](keys = ids) withFailureMessage
        f"Failed to fetch document revisions by IDs $ids from $uri"
    } yield res.rows.map { case Row(Some(id), _, value, _) => (id, value("rev")) }

  /**
   * Finds documents using the declarative mango query syntax. See [[sohva.mango]] for details.
   *
   *  @group CouchDB2
   */
  def find[T <: AnyRef: JsonReader](selector: Selector, fields: List[String] = Nil, sort: List[Sort], limit: Option[Int] = None, skip: Option[Int] = None, use_index: Option[UseIndex] = None): Future[SearchResult[T]] =
    find[T](Query(selector, fields, sort, limit, skip, use_index))

  /**
   * Finds documents using the declarative mango query syntax. See [[sohva.mango]] for details.
   *
   *  @group CouchDB2
   */
  def find[T <: AnyRef: JsonReader](query: Query): Future[SearchResult[T]] = {
    implicit val format = lift(implicitly[JsonReader[T]])
    for {
      entity <- Marshal(query).to[RequestEntity]
      res <- couch.http(HttpRequest(HttpMethods.POST, uri = uri / "_find", entity = entity)).withFailureMessage(f"Failed while querying document on database $uri")
    } yield res.convertTo[SearchResult[T]]
  }

  /**
   * Explains how the query is run by the CouchDB server.
   *
   *  @group CouchDB2
   */
  def explain(query: Query): Future[Explanation] =
    for {
      entity <- Marshal(query).to[RequestEntity]
      expl <- couch.http(HttpRequest(HttpMethods.POST, uri = uri / "_explain", entity = entity)).withFailureMessage(f"Failed while querying document on database $uri")
    } yield expl.convertTo[Explanation]

  /**
   * Exposes the interface for managing indices.
   *
   *  @group CouchDB2
   */
  object index extends Index(this)

  /** Exposes the interface for managing local (non-replicating) documents. */
  object local extends Local(this)

  /** Creates or updates a bunch of documents into the database. */
  def saveDocs[T: CouchFormat](docs: List[T], all_or_nothing: Boolean = false): Future[List[DbResult]] =
    for {
      entity <- Marshal(BulkSave(all_or_nothing, docs.map(_.toJson))).to[RequestEntity]
      raw <- couch.http(HttpRequest(HttpMethods.POST, uri = uri / "_bulk_docs", entity = entity)) withFailureMessage
        f"Failed to bulk save documents to $uri"
    } yield bulkSaveResult(raw)

  private def saveRawDocs(docs: List[JsValue], all_or_nothing: Boolean = false): Future[List[DbResult]] =
    for {
      entity <- Marshal(JsObject(Map("all_or_nothing" -> JsBoolean(all_or_nothing), "docs" -> JsArray(docs.toVector)))).to[RequestEntity]
      raw <- couch.http(HttpRequest(HttpMethods.POST, uri = uri / "_bulk_docs", entity = entity)) withFailureMessage
        f"Failed to bulk save documents to $uri"
    } yield bulkSaveResult(raw)

  private[this] def bulkSaveResult(json: JsValue) =
    json.convertTo[List[DbResult]]

  /**
   * Creates a document in the database and returns its identifier and revision.
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
          entity <- Marshal(json).to[RequestEntity]
          raw <- couch.http(HttpRequest(HttpMethods.POST, uri = uri, entity = entity)).withFailureMessage(f"Failed to create new document into $uri")
          DocUpdate(ok, id, rev) = docUpdateResult(raw)
        } yield OkResult(ok, Some(id), Some(rev))
    }

  /**
   * Creates a set of documents in the database and returns theirs identifiers and revision.
   *  If the json version of an object has a `_id` field, this identifier is used for the document,
   *  otherwise a new one is generated.
   */
  def createDocs[T: JsonWriter](docs: List[T]): Future[List[DbResult]] =
    saveRawDocs(docs.map(_.toJson))

  /**
   * Copies the origin document to the target document.
   *  If the target does not exist, it is created, otherwise it is updated and the target
   *  revision must be provided
   */
  def copy(origin: String, target: String, originRev: Option[String] = None, targetRev: Option[String] = None): Future[Boolean] =
    for (
      res <- couch.http(HttpRequest(COPY, uri = uri / origin <<? originRev.map("rev" -> _))
        <:< Map("Destination" -> (target + targetRev.map("?rev=" + _).getOrElse("")))
      ) withFailureMessage f"Failed to copy from $origin at $originRev to $target at $targetRev from $uri"
    ) yield couch.ok(res)

  /**
   * Patches the document identified by the given identifier in the given revision.
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

  /**
   * Deletes the document identified by the given id from the database.
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
          res <- couch.http(HttpRequest(HttpMethods.DELETE, uri = uri / id <<? Map("rev" -> rev))) withFailureMessage
            f"Failed to delete document with ID $id from $uri"
        ) yield couch.ok(res)
      case None =>
        Future.successful(false)
    }

  /**
   * Deletes a bunch of documents at once returning the results
   *  for each identifier in the document list. One can choose the update strategy
   *  by setting the parameter `all_or_nothing` to `true` or `false`.
   */
  def deleteDocs(ids: List[String], all_or_nothing: Boolean = false): Future[List[DbResult]] =
    for {
      revs <- getDocRevisions(ids)
      entity <- Marshal(JsObject(
        Map(
          "all_or_nothing" -> all_or_nothing.toJson,
          "docs" -> revs.map {
            case (id, rev) => JsObject(
              "_id" -> id.toJson,
              "_rev" -> rev.toJson,
              "_deleted" -> true.toJson)
          }.toJson
        )
      )).to[RequestEntity]
      raw <- couch.http(
        HttpRequest(HttpMethods.POST, uri = uri / "_bulk_docs", entity = entity)).withFailureMessage(f"Failed to bulk delete docs $ids from $uri")
    } yield bulkSaveResult(raw)

  /**
   * Attaches the given file to the given document id.
   *  This method returns `true` iff the file was attached to the document.
   */
  def attachTo(docId: String, file: File, contentType: String): Future[Boolean] = {
    // first get the last revision of the document (if it exists)
    for {
      mime <- ContentType.parse(contentType) match {
        case Left(_) => Future.failed(new SohvaException(f"Wrong media type $contentType"))
        case Right(mime) => Future.successful(mime)
      }
      rev <- getDocRevision(docId)
      res <- couch.http(
        HttpRequest(HttpMethods.PUT, uri = uri / docId / file.getName <<? rev.map("rev" -> _), entity = HttpEntity.fromPath(mime, file.toPath, 1000000))).withFailureMessage(f"Failed to attach file ${file.getName} to document with ID $docId at $uri")
    } yield couch.ok(res)
  }

  /**
   * Attaches the given file (given as an input stream) to the given document id.
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
    for {
      fos <- new FileOutputStream(file).autoClose
      bis <- new BufferedInputStream(stream).autoClose
    } {
      val array = new Array[Byte](bis.available)
      bis.read(array)
      fos.write(array)
    }
    attachTo(docId, file, contentType)
  }

  /**
   * Returns the given attachment for the given docId.
   *  It returns the mime type if any given in the response and the input stream
   *  to read the response from the server.
   */
  def getAttachment(docId: String, attachment: String): Future[Option[(String, ByteString)]] =
    couch.rawHttp(HttpRequest(uri = uri / docId / attachment)).flatMap(readFile) withFailureMessage
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
          res <- couch.http(HttpRequest(HttpMethods.DELETE, uri = uri / docId / attachment <<?
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
      doc <- couch.http(HttpRequest(uri = uri / "_security")) withFailureMessage
        f"Failed to fetch security doc from $uri"
    ) yield extractSecurityDoc(doc)

  /**
   * Creates or updates the security document.
   *  Security documents are special documents with no `_id` nor `_rev` fields.
   */
  def saveSecurityDoc(doc: SecurityDoc): Future[Boolean] =
    for {
      entity <- Marshal(doc).to[RequestEntity]
      res <- couch.http(HttpRequest(HttpMethods.PUT, uri = uri / "_security", entity = entity)) withFailureMessage
        f"failed to save security document for $uri"
    } yield couch.ok(res)

  /** Returns a design object that allows user to work with views */
  def design(designName: String, language: String = "javascript"): Design =
    new Design(this, designName, language)

  /**
   * Returns a built-in view of this database, identified by its name.
   *  E.g. `_all_docs`.
   */
  def builtInView(view: String): View =
    new BuiltInView(this, view)

  /**
   * Returns a temporary view of this database, specified by the `ViewDoc`.
   *
   *  @group CouchDB1
   */
  @deprecated("Temporary view were removed in CouchDB 2.0 and should not be used", "2.0.0")
  def temporaryView(viewDoc: ViewDoc): View =
    new TemporaryView(this, viewDoc)

  /** Requests a database compaction. */
  def compact: Future[Boolean] =
    for (resp <- couch.http(HttpRequest(HttpMethods.POST, uri = uri / "_compact")).withFailureMessage(f"Unable to compact database at $uri"))
      yield resp.asJsObject.fields("ok").convertTo[Boolean]

  /** Ensures that all changes are written to disk. */
  @deprecated("You shouldn't need to call this if you have the recommended setting `delayed_commits=false`", "2.0.0")
  def ensureFullCommit: Future[Boolean] =
    for (resp <- couch.http(HttpRequest(HttpMethods.POST, uri = uri / "_ensure_full_commit" / name)).withFailureMessage(f"Unable to ensure full commit at $uri"))
      yield resp.asJsObject.fields("ok").convertTo[Boolean]

  /** Cleanups old views. */
  def viewCleanup: Future[Boolean] =
    for (resp <- couch.http(HttpRequest(HttpMethods.POST, uri = uri / "_view_cleanup" / name)).withFailureMessage(f"Unable to perform view cleanup at $uri"))
      yield resp.asJsObject.fields("ok").convertTo[Boolean]

  /** Returns the revision for each document in the map that are not present in this node. */
  def missingRevs(revs: Map[String, Vector[String]]): Future[Map[String, Vector[String]]] =
    for {
      entity <- Marshal(revs).to[RequestEntity]
      resp <- couch.http(HttpRequest(HttpMethods.POST, uri = uri / "_missing_revs", entity = entity)).withFailureMessage(f"Unable to get missing revisions from $uri")
    } yield resp.asJsObject.fields("missing_revs").convertTo[Map[String, Vector[String]]]

  /** Given a list of documents and revisions, returns the revision that are missing in this node. */
  def revsDiff(revs: Map[String, Vector[String]]): Future[Map[String, RevDiff]] =
    for {
      entity <- Marshal(revs).to[RequestEntity]
      resp <- couch.http(HttpRequest(HttpMethods.POST, uri = uri / "_revs_diff", entity = entity)).withFailureMessage(f"Unable to get revision difffrom $uri")
    } yield resp.convertTo[Map[String, RevDiff]]

  /** Gets the current database revision limit. */
  def getRevsLimit: Future[Int] =
    for (resp <- couch.http(HttpRequest(uri = uri / "_revs_limit")).withFailureMessage(f"Unable to get revision limit at $uri"))
      yield resp.convertTo[Int]

  /** Sets the current database revision limit. */
  def setRevsLimit(l: Int): Future[Boolean] =
    for {
      entity <- Marshal(JsNumber(l)).to[RequestEntity]
      resp <- couch.http(HttpRequest(HttpMethods.PUT, uri = uri / "_revs_limit", entity = entity)).withFailureMessage(f"Unable to get revision limit at $uri")
    } yield resp.asJsObject.fields("ok").convertTo[Boolean]

  // helper methods

  protected[sohva] val uri =
    couch.uri / name

  protected[sohva] def http(req: HttpRequest): Future[JsValue] =
    couch.http(req)

  protected[sohva] def optHttp(req: HttpRequest): Future[Option[JsValue]] =
    couch.optHttp(req)

  private def readFile(response: HttpResponse): Future[Option[(String, ByteString)]] = {
    if (response.status.intValue == 404) {
      Future.successful(None)
    } else if (response.status.isSuccess) {
      Unmarshal(response.entity).to[ByteString].map { is =>
        Some(
          response.headers.find(_.is("content-type")).map(_.value).getOrElse("application/json") -> is)
      }
    } else {
      val code = response.status.intValue
      // something went wrong...
      Unmarshal(response.entity).to[JsValue].flatMap { json =>
        // something went wrong...
        val code = response.status.intValue
        val error = Try(json.convertTo[ErrorResult]).toOption
        Future.failed(CouchException(code, error))
      }
    }
  }

  private def okResult(json: JsValue) =
    json.convertTo[OkResult]

  private def infoResult(json: JsValue) =
    json.convertTo[InfoResult]

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
      Unmarshal(response.entity).to[ErrorResult].map(Some(_)).recover { case _: Exception => None }.flatMap(error => Future.failed(new CouchException(code, error)))
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
  disk_size: Long,
  doc_count: Long,
  doc_del_count: Long,
  data_size: Long,
  instance_start_time: String,
  purge_seq: Long,
  update_seq: JsValue,
  sizes: Option[Sizes],
  other: Option[Map[String, JsValue]])

final case class Sizes(file: Long, external: Long, active: Long)

final case class DocUpdate(
  ok: Boolean,
  id: String,
  rev: String)

private[sohva] final case class BulkDocs[T](rows: List[BulkDocRow[T]])

private[sohva] final case class BulkDocRow[T](id: String, rev: String, doc: Option[T])
private[sohva] final case class BulkSave(all_or_nothing: Boolean, docs: List[JsValue])

final case class RevDiff(missing: Vector[String], possible_ancestors: Vector[String])
