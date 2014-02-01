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

import dispatch._
import Defaults._
import retry.{
  CountingRetry,
  Success
}
import stream.Strings

import com.ning.http.client.Response

import java.io.{
  File,
  InputStream,
  FileOutputStream,
  BufferedInputStream
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
class Database private[sohva] (
  val name: String,
  val couch: CouchDB,
  val credit: Int,
  val strategy: Strategy)
    extends gnieh.sohva.Database[AsyncResult] {

  import couch.serializer

  /* the resolver is responsible for applying the merging strategy on conflict and retrying
   * to save the document after resolution process */
  private object resolver extends CountingRetry {
    // only retry if a conflict occurred, other errors are considered as 'Success'
    val saveOk = new Success[Either[(Int, Option[ErrorResult]), String]]({
      case Left((409, _)) => false
      case _              => true
    })
    def apply(credit: Int, docId: String, baseRev: Option[String], doc: String): AsyncResult[String] = {
      def doit(count: Int): AsyncResult[String] =
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

  def info: AsyncResult[Option[InfoResult]] =
    for (info <- couch.optHttp(request).right)
      yield info.map(infoResult)

  @inline
  def exists: AsyncResult[Boolean] =
    couch.contains(name)

  def changes(filter: Option[String] = None): ChangeStream =
    new OriginalChangeStream(this, filter)

  def create: AsyncResult[Boolean] =
    for {
      exist <- exists.right
      ok <- create(exist)
    } yield ok

  private[this] def create(exist: Boolean) =
    if (exist) {
      Future.successful(Right(false))
    } else {
      for (result <- couch.http(request.PUT).right)
        yield couch.ok(result)
    }

  def delete: AsyncResult[Boolean] =
    for {
      exist <- exists.right
      ok <- delete(exist)
    } yield ok

  private[this] def delete(exist: Boolean) =
    if (exist) {
      for (result <- couch.http(request.DELETE).right)
        yield couch.ok(result)
    } else {
      Future.successful(Right(false))
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
    inclusive_end: Boolean = true): AsyncResult[List[String]] =
    for {
      res <- builtInView[String, Map[String, String], Any]("_all_docs").query(
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
      ).right
    } yield for (Row(id, _, _, _) <- res.rows) yield id

  def getDocById[T: Manifest](id: String, revision: Option[String] = None): AsyncResult[Option[T]] =
    for (raw <- getRawDocById(id, revision).right)
      yield raw.map(docResult[T])

  def getDocsById[T: Manifest](ids: List[String]): AsyncResult[List[T]] =
    for {
      res <- builtInView[String, Map[String, String], T]("_all_docs").query(keys = ids, include_docs = true).right
    } yield res.rows.flatMap { case Row(_, _, _, doc) => doc }

  def getRawDocById(id: String, revision: Option[String] = None): AsyncResult[Option[String]] =
    couch.optHttp(request / id <<? revision.map("rev" -> _).toList)

  def getDocRevision(id: String): AsyncResult[Option[String]] =
    couch._http((request / id).HEAD, new FunctionHandler(extractRev _))

  def getDocRevisions(ids: List[String]): AsyncResult[List[(String, String)]] =
    for {
      res <- builtInView[String, Map[String, String], Any]("_all_docs").query(keys = ids).right
    } yield res.rows.map(row => (row.id, row.value("rev")))

  def saveDoc[T <% IdRev: Manifest](doc: T): AsyncResult[Option[T]] =
    for {
      upd <- resolver(credit, doc._id, doc._rev, serializer.toJson(doc)).right
      res <- update(docUpdateResult(upd))
    } yield res

  def saveRawDoc(doc: String): AsyncResult[Option[String]] = serializer.fromCouchJson(doc) match {
    case Some((id, rev)) =>
      for {
        upd <- resolver(credit, id, rev, doc).right
        res <- updateRaw(docUpdateResult(upd))
      } yield res
    case None =>
      Future.successful(Right(None))
  }

  private[this] def update[T: Manifest](res: DocUpdate) = res match {
    case DocUpdate(true, id, _) =>
      getDocById[T](id)
    case DocUpdate(false, _, _) =>
      Future.successful(Right(None))
  }

  private[this] def updateRaw(res: DocUpdate) = res match {
    case DocUpdate(true, id, _) =>
      getRawDocById(id)
    case DocUpdate(false, _, _) =>
      Future.successful(Right(None))
  }

  def saveDocs[T <% IdRev](docs: List[T], all_or_nothing: Boolean = false): AsyncResult[List[DbResult]] =
    for {
      raw <- couch.http(request / "_bulk_docs" << serializer.toJson(BulkSave(all_or_nothing, docs))).right
    } yield bulkSaveResult(raw)

  private[this] def bulkSaveResult(json: String) =
    serializer.fromJson[List[DbResult]](json)

  def copy(origin: String, target: String, originRev: Option[String] = None, targetRev: Option[String] = None): AsyncResult[Boolean] =
    for (
      res <- couch.http((request / origin).subject.setMethod("COPY")
        <:< Map("Destination" -> (target + targetRev.map("?rev=" + _).getOrElse("")))
        <<? originRev.map("rev" -> _).toList
      ).right
    ) yield couch.ok(res)

  def patchDoc[T <: IdRev: Manifest](id: String, rev: String, patch: JsonPatch): AsyncResult[Option[T]] =
    for {
      doc <- getDocById[T](id, Some(rev)).right
      res <- patchDoc(doc, patch).right
    } yield res

  private[this] def patchDoc[T <: IdRev: Manifest](doc: Option[T], patch: JsonPatch) = doc match {
    case Some(doc) => saveDoc(patch(doc).withRev(doc._rev))
    case None      => Future.successful(Right(None))
  }

  def deleteDoc[T <% IdRev](doc: T): AsyncResult[Boolean] =
    for (res <- couch.http((request / doc._id).DELETE <<? Map("rev" -> doc._rev.getOrElse(""))).right)
      yield couch.ok(res)

  def deleteDoc(id: String): AsyncResult[Boolean] =
    for {
      rev <- getDocRevision(id).right
      res <- delete(rev, id)
    } yield res

  private[this] def delete(rev: Option[String], id: String) =
    rev match {
      case Some(rev) =>
        for (res <- couch.http((request / id).DELETE <<? Map("rev" -> rev)).right)
          yield couch.ok(res)
      case None =>
        Future.successful(Right(false))
    }

  def deleteDocs(ids: List[String], all_or_nothing: Boolean = false): AsyncResult[List[DbResult]] =
    for {
      revs <- getDocRevisions(ids).right
      raw <- couch.http(
        request / "_bulk_docs"
          << serializer.toJson(
            Map(
              "all_or_nothing" -> all_or_nothing,
              "docs" -> revs.map {
                case (id, rev) => Map("_id" -> id, "_rev" -> rev, "_deleted" -> true)
              }
            )
          )
      ).right
    } yield bulkSaveResult(raw)

  def attachTo(docId: String, file: File, contentType: String): AsyncResult[Boolean] =
    // first get the last revision of the document (if it exists)
    for {
      rev <- getDocRevision(docId).right
      res <- couch.http(request / docId / file.getName <<? attachParams(rev) <<< file, contentType).right
    } yield couch.ok(res)

  private[this] def attachParams(rev: Option[String]) =
    rev match {
      case Some(r) =>
        List("rev" -> r)
      case None =>
        // doc does not exist? well... good... does it matter? no!
        // couchdb will create it for us, don't worry
        Nil
    }

  def attachTo(docId: String,
    attachment: String,
    stream: InputStream,
    contentType: String): AsyncResult[Boolean] = {
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

  def getAttachment(docId: String, attachment: String): AsyncResult[Option[(String, InputStream)]] =
    couch._http(request / docId / attachment, new FunctionHandler(readFile _))

  def deleteAttachment(docId: String, attachment: String): AsyncResult[Boolean] =
    for {
      // first get the last revision of the document (if it exists)
      rev <- getDocRevision(docId).right
      res <- deleteAttachment(docId, attachment, rev)
    } yield res

  private[this] def deleteAttachment(docId: String, attachment: String, rev: Option[String]) =
    rev match {
      case Some(r) =>
        for (
          res <- couch.http((request / docId / attachment <<?
            List("rev" -> r)).DELETE).right
        ) yield couch.ok(res)
      case None =>
        // doc does not exist? well... good... just do nothing
        Future.successful(Right(false))
    }

  def securityDoc: AsyncResult[SecurityDoc] =
    for (doc <- couch.http(request / "_security").right)
      yield extractSecurityDoc(doc)

  def saveSecurityDoc(doc: SecurityDoc): AsyncResult[Boolean] =
    for (res <- couch.http((request / "_security" << serializer.toJson(doc)).PUT).right)
      yield couch.ok(res)

  def design(designName: String, language: String = "javascript"): Design =
    new Design(this, designName, language)

  def builtInView[Key: Manifest, Value: Manifest, Doc: Manifest](view: String): View[Key, Value, Doc] =
    new BuiltInView[Key, Value, Doc](this, view)

  // helper methods

  protected[sohva] def request =
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

  private def docResultOpt[T: Manifest](json: String) =
    serializer.fromJsonOpt[T](json)

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

protected[sohva] final case class BulkDocRow[T](id: String, rev: String, doc: Option[T])

protected[sohva] final case class BulkSave[T](all_or_nothing: Boolean, docs: List[T])

