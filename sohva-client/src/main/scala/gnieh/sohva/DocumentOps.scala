/*
* This file is part of the sohva project.
* Copyright (c) 2016 Lucas Satabin
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

import akka.http.scaladsl.model._
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import spray.json._

import scala.concurrent._

import org.slf4j.LoggerFactory

abstract class DocumentOps {

  import SohvaProtocol._
  import SprayJsonSupport._

  implicit val ec: ExecutionContext

  val credit: Int

  val strategy: Strategy

  /** Returns the document identified by the given id if it exists */
  def getDocById[T: JsonReader](id: String, revision: Option[String] = None): Future[Option[T]] =
    for (raw <- optHttp(HttpRequest(uri = uri / id <<? revision.flatMap(r => if (r.nonEmpty) Some("rev" -> r) else None))).withFailureMessage(f"Failed to fetch document by ID $id and revision $revision"))
      yield raw.map(_.convertTo[T])

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

  protected[this] def saveRawDoc(doc: JsValue): Future[JsValue] = doc match {
    case JsObject(fields) =>
      val idRev = for {
        id <- fields.get("_id").map(_.convertTo[String])
        rev = fields.get("_rev").map(_.convertTo[String])
      } yield (id, rev)
      idRev match {
        case Some((id, rev)) =>
          (for {
            upd <- resolver(credit, id, rev, doc)
            res <- updateRaw(upd.convertTo[DocUpdate])
          } yield res) withFailureMessage f"Failed to update raw document with ID $id and revision $rev"
        case None =>
          Future.failed(new SohvaException(f"Not a couchdb document: ${doc.prettyPrint}"))
      }
    case _ =>
      Future.failed(new SohvaException(f"Not a couchdb document: ${doc.prettyPrint}"))
  }

  /* the resolver is responsible for applying the merging strategy on conflict and retrying
   * to save the document after resolution process */
  private def resolver(credit: Int, docId: String, baseRev: Option[String], current: JsValue): Future[JsValue] = current match {
    case JsNull =>
      LoggerFactory.getLogger(getClass).info("No document to save")
      Future.successful(DocUpdate(true, docId, baseRev.getOrElse("")).toJson)
    case _ =>
      (for {
        entity <- Marshal(current).to[RequestEntity]
        res <- http(HttpRequest(HttpMethods.PUT, uri = uri / docId, entity = entity))
      } yield res).recoverWith {
        case exn @ ConflictException(_) if credit > 0 =>
          LoggerFactory.getLogger(getClass).info("Conflict occurred, try to resolve it")
          // try to resolve the conflict and save again
          for {
            // get the base document if any
            base <- getDocById[JsValue](docId, baseRev)
            // get the last document
            last <- getDocById[JsValue](docId)
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

  private[this] def update[T: JsonReader](res: DocUpdate) = res match {
    case DocUpdate(true, id, rev) =>
      getDocById[T](id, Some(rev)).map(_.get)
    case DocUpdate(false, id, _) =>
      Future.failed(new SohvaException(f"Document $id could not be saved"))
  }

  private[this] def updateRaw(res: DocUpdate) = res match {
    case DocUpdate(true, id, rev) =>
      getDocById[JsValue](id, Some(rev)).map(_.get)
    case DocUpdate(false, id, _) =>
      Future.failed(new SohvaException("Document $id could not be saved"))
  }

  /** Deletes the document from the database.
   *  The document will only be deleted if the caller provided the last revision
   */
  def deleteDoc[T: CouchFormat](doc: T): Future[Boolean] = {
    val format = implicitly[CouchFormat[T]]
    for (
      res <- http(HttpRequest(HttpMethods.DELETE, uri = uri / format._id(doc) <<? Map("rev" -> format._rev(doc).getOrElse("")))) withFailureMessage
        f"Failed to delete document with ID ${format._id(doc)} at revision ${format._rev(doc)} from $uri"
    ) yield res.convertTo[OkResult].ok
  }

  protected[sohva] def http(req: HttpRequest): Future[JsValue]

  protected[sohva] def optHttp(req: HttpRequest): Future[Option[JsValue]]

  protected[sohva] val uri: Uri

}
