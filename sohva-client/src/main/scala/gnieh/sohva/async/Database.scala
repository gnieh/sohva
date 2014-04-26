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
package async

import strategy.Strategy

import resource._

import java.io.{
  File,
  InputStream,
  FileOutputStream,
  ByteArrayInputStream,
  BufferedInputStream
}

import net.liftweb.json._

import gnieh.diffson.JsonPatch

import scala.concurrent.Future

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
  val strategy: Strategy)
    extends gnieh.sohva.Database[Future]
    with LiftMarshalling {

  import couch.serializer

  implicit def ec =
    couch.ec

  implicit def formats =
    couch.formats

  /* the resolver is responsible for applying the merging strategy on conflict and retrying
   * to save the document after resolution process */
  private def resolver(credit: Int, docId: String, baseRev: Option[String], current: JValue): Future[JValue] =
    couch.http(Put(uri / docId, current)).recoverWith {
      case CouchException(409, _) if credit > 0 =>
        // try to resolve the conflict and save again
        for {
          // get the base document if any
          base <- getRawDocById(docId, baseRev)
          // get the last document
          last <- getRawDocById(docId)
          // apply the merge strategy between base, last and current revision of the document
          lastRev = last map (d => (d \ "_rev").toString)
          resolved = strategy(base, last, current)
          res <- resolver(credit - 1, docId, lastRev, resolved)
        } yield res
    }

  def info: Future[Option[InfoResult]] =
    for (info <- couch.optHttp(Get(uri)))
      yield info.map(infoResult)

  def exists: Future[Boolean] =
    for (h <- couch.optHttp(Head(uri)))
      yield h.isDefined

  def changes(since: Option[Int] = None, filter: Option[String] = None): ChangeStream =
    new ChangeStream(this, since, filter)

  def create: Future[Boolean] =
    for {
      exist <- exists
      ok <- create(exist)
    } yield ok

  private[this] def create(exist: Boolean) =
    if (exist) {
      Future.successful(false)
    } else {
      for (result <- couch.http(Put(uri)))
        yield couch.ok(result)
    }

  def delete: Future[Boolean] =
    for {
      exist <- exists
      ok <- delete(exist)
    } yield ok

  private[this] def delete(exist: Boolean) =
    if (exist) {
      for (result <- couch.http(Delete(uri)))
        yield couch.ok(result)
    } else {
      Future.successful(false)
    }

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
      res <- builtInView("_all_docs").query[String, Map[String, String], Any](
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
    } yield for (Row(Some(id), _, _, _) <- res.rows) yield id

  def getDocById[T: Manifest](id: String, revision: Option[String] = None): Future[Option[T]] =
    for (raw <- getRawDocById(id, revision))
      yield raw.map(docResult[T])

  def getDocsById[T: Manifest](ids: List[String]): Future[List[T]] =
    for {
      res <- builtInView("_all_docs").query[String, Map[String, String], T](keys = ids, include_docs = true)
    } yield res.rows.flatMap { case Row(_, _, _, doc) => doc }

  def getRawDocById(id: String, revision: Option[String] = None): Future[Option[JValue]] =
    couch.optHttp(Get(uri / id <<? revision.map("rev" -> _)))

  def getDocRevision(id: String): Future[Option[String]] =
    couch.pipeline(couch.prepare((Head(uri / id)))).flatMap(extractRev _)

  def getDocRevisions(ids: List[String]): Future[List[(String, String)]] =
    for {
      res <- builtInView("_all_docs").query[String, Map[String, String], Any](keys = ids)
    } yield res.rows.map { case Row(Some(id), _, value, _) => (id, value("rev")) }

  def saveDoc[T <% IdRev: Manifest](doc: T): Future[T] =
    for {
      upd <- resolver(credit, doc._id, doc._rev, serializer.toJson(doc))
      res <- update(upd.extract[DocUpdate])
    } yield res

  def saveRawDoc(doc: JValue): Future[JValue] = serializer.fromCouchJson(doc) match {
    case Some((id, rev)) =>
      for {
        upd <- resolver(credit, id, rev, doc)
        res <- updateRaw(docUpdateResult(upd))
      } yield res
    case None =>
      Future.failed(new SohvaException("Not a couchdb document: " + pretty(render(doc))))
  }

  private[this] def update[T: Manifest](res: DocUpdate) = res match {
    case DocUpdate(true, id, rev) =>
      getDocById[T](id, Some(rev)).map(_.get)
    case DocUpdate(false, id, _) =>
      Future.failed(new SohvaException("Document " + id + " could not be saved"))
  }

  private[this] def updateRaw(res: DocUpdate) = res match {
    case DocUpdate(true, id, rev) =>
      getRawDocById(id, Some(rev)).map(_.get)
    case DocUpdate(false, id, _) =>
      Future.failed(new SohvaException("Document " + id + " could not be saved"))
  }

  def saveDocs[T <% IdRev](docs: List[T], all_or_nothing: Boolean = false): Future[List[DbResult]] =
    for {
      raw <- couch.http(Post(uri / "_bulk_docs", serializer.toJson(BulkSave(all_or_nothing, docs))))
    } yield bulkSaveResult(raw)

  private[this] def bulkSaveResult(json: JValue) =
    serializer.fromJson[List[DbResult]](json)

  def copy(origin: String, target: String, originRev: Option[String] = None, targetRev: Option[String] = None): Future[Boolean] =
    for (
      res <- couch.http(Copy(uri / origin <<? originRev.map("rev" -> _))
        <:< Map("Destination" -> (target + targetRev.map("?rev=" + _).getOrElse("")))
      )
    ) yield couch.ok(res)

  def patchDoc[T <: IdRev: Manifest](id: String, rev: String, patch: JsonPatch): Future[T] =
    for {
      doc <- getDocById[T](id, Some(rev))
      res <- patchDoc(id, doc, patch)
    } yield res

  private[this] def patchDoc[T <: IdRev: Manifest](id: String, doc: Option[T], patch: JsonPatch) = doc match {
    case Some(doc) => saveDoc(patch(doc).withRev(doc._rev))
    case None      => Future.failed(new SohvaException("Uknown document to patch: " + id))
  }

  def deleteDoc[T <% IdRev](doc: T): Future[Boolean] =
    for (res <- couch.http(Delete(uri / doc._id <<? Map("rev" -> doc._rev.getOrElse("")))))
      yield couch.ok(res)

  def deleteDoc(id: String): Future[Boolean] =
    for {
      rev <- getDocRevision(id)
      res <- delete(rev, id)
    } yield res

  private[this] def delete(rev: Option[String], id: String) =
    rev match {
      case Some(rev) =>
        for (res <- couch.http(Delete(uri / id <<? Map("rev" -> rev))))
          yield couch.ok(res)
      case None =>
        Future.successful(false)
    }

  def deleteDocs(ids: List[String], all_or_nothing: Boolean = false): Future[List[DbResult]] =
    for {
      revs <- getDocRevisions(ids)
      raw <- couch.http(
        Post(uri / "_bulk_docs",
          serializer.toJson(
            Map(
              "all_or_nothing" -> all_or_nothing,
              "docs" -> revs.map {
                case (id, rev) => Map("_id" -> id, "_rev" -> rev, "_deleted" -> true)
              }
            )
          )
        )
      )
    } yield bulkSaveResult(raw)

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
      )
    } yield couch.ok(res)
  }

  def attachTo(docId: String,
    attachment: String,
    stream: InputStream,
    contentType: String): Future[Boolean] = {
    // create a temporary file with the content of the input stream
    val file = File.createTempFile(attachment, null)
    for (fos <- managed(new FileOutputStream(file))) {
      for (bis <- managed(new BufferedInputStream(stream))) {
        val array = new Array[Byte](bis.available)
        bis.read(array)
        fos.write(array)
      }
    }
    attachTo(docId, file, contentType)
  }

  def getAttachment(docId: String, attachment: String): Future[Option[(String, InputStream)]] =
    couch.pipeline(couch.prepare(Get(uri / docId / attachment))).flatMap(readFile)

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
            Map("rev" -> r)))
        ) yield couch.ok(res)
      case None =>
        // doc does not exist? well... good... just do nothing
        Future.successful(false)
    }

  def securityDoc: Future[SecurityDoc] =
    for (doc <- couch.http(Get(uri / "_security")))
      yield extractSecurityDoc(doc)

  def saveSecurityDoc(doc: SecurityDoc): Future[Boolean] =
    for (res <- couch.http(Put(uri / "_security", serializer.toJson(doc))))
      yield couch.ok(res)

  def design(designName: String, language: String = "javascript"): Design =
    new Design(this, designName, language)

  def builtInView(view: String): View =
    new BuiltInView(this, view)

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
      val error = serializer.fromJsonOpt[ErrorResult](parse(response.entity.asString))
      Future.failed(CouchException(code, error))
    }
  }

  private def okResult(json: JValue) =
    serializer.fromJson[OkResult](json)

  private def infoResult(json: JValue) =
    serializer.fromJson[InfoResult](json)

  private def docResult[T: Manifest](json: JValue) =
    serializer.fromJson[T](json)

  private def docResultOpt[T: Manifest](json: JValue) =
    serializer.fromJsonOpt[T](json)

  private def docUpdateResult(json: JValue) =
    serializer.fromJson[DocUpdate](json)

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
      val error = serializer.fromJsonOpt[ErrorResult](parse(response.entity.asString))
      Future.failed(new CouchException(code, error))
    }
  }

  private def extractSecurityDoc(json: JValue) =
    serializer.fromJson[SecurityDoc](json)

}

protected[sohva] final case class BulkDocRow[T](id: String, rev: String, doc: Option[T])

protected[sohva] final case class BulkSave[T](all_or_nothing: Boolean, docs: List[T])

