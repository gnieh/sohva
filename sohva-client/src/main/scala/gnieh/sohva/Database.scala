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

import eu.medsea.util.MimeUtil

import net.liftweb.json._

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

final case class Attachment(content_type: String,
                            revpos: Int,
                            digest: String,
                            length: Int,
                            stub: Boolean)

