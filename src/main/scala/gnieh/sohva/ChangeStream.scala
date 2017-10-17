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

import mango._

import spray.json._

import akka.actor._

import akka.http.scaladsl.Http
import akka.http.scaladsl.settings._
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import akka.stream.scaladsl._
import akka.stream.{
  KillSwitches,
  UniqueKillSwitch
}

import akka.util._

import scala.util.{
  Try,
  Success
}

import scala.concurrent._
import scala.concurrent.duration.Duration

import java.util.concurrent.atomic.AtomicLong

/** A stream that represents a connection to the `_changes` stream of a database.
 *
 *  @author Lucas Satabin
 */
class ChangeStream(database: Database) {

  import SohvaProtocol._
  import SprayJsonSupport._

  import database.couch.system
  import database.couch.ec

  private def makeSince(old: Option[Either[String, JsValue]]) = old match {
    case Some(Left("now")) => Now
    case Some(Left(_))     => Origin
    case Some(Right(js))   => UpdateSequence(js)
    case None              => Origin
  }

  /** Returns a one-shot view of changes for this database. */
  @deprecated("Use the `current` stream instead.", "Sohva 2.2.0")
  def once(
    docIds: Iterable[String] = Vector.empty[String],
    conflicts: Boolean = false,
    descending: Boolean = false,
    filter: Option[String] = None,
    selector: Option[Selector] = None,
    designOnly: Boolean = false,
    includeDocs: Boolean = false,
    attachments: Boolean = false,
    attEncodingInfo: Boolean = false,
    lastEventId: Option[Int] = None,
    limit: Option[Int] = None,
    since: Option[Either[String, JsValue]] = None,
    style: Option[String] = None,
    view: Option[String] = None): Future[Changes] =
    current(docIds = docIds,
      conflicts = conflicts,
      descending = descending,
      filter = filter,
      selector = selector,
      designOnly = designOnly,
      includeDocs = includeDocs,
      attachments = attachments,
      attEncodingInfo = attEncodingInfo,
      lastEventId = lastEventId,
      limit = limit,
      since = makeSince(since),
      style = style,
      view = view)

  /** Returns a one-shot view of changes for this database. */
  def current(
    docIds: Iterable[String] = Vector.empty[String],
    conflicts: Boolean = false,
    descending: Boolean = false,
    filter: Option[String] = None,
    selector: Option[Selector] = None,
    designOnly: Boolean = false,
    includeDocs: Boolean = false,
    attachments: Boolean = false,
    attEncodingInfo: Boolean = false,
    lastEventId: Option[Int] = None,
    limit: Option[Int] = None,
    since: Since = Origin,
    style: Option[String] = None,
    view: Option[String] = None): Future[Changes] = {

    val parameters = List(if (conflicts) Some("conflicts" -> "true") else None,
      if (descending) Some("descending" -> "true") else None,
      filter.map(s => "filter" -> s),
      if (includeDocs) Some("include_docs" -> "true") else None,
      if (attachments) Some("attachments" -> "true") else None,
      if (attEncodingInfo) Some("att_encoding_info" -> "true") else None,
      lastEventId.map(n => "last-event-id" -> n.toString),
      limit.map(n => "limit" -> n.toString),
      since.option.map(s => "since" -> s),
      style.map(s => "style" -> s),
      view.map(v => "view" -> v)).flatten.toMap

    val request = selector match {
      case Some(selector) =>
        for {
          entity <- Marshal(Map("selector" -> selector)).to[RequestEntity]
        } yield HttpRequest(HttpMethods.POST, uri = uri <<? parameters.updated("filter", "_selector"), entity = entity)
      case None =>
        if (docIds.isEmpty)
          Future.successful(HttpRequest(uri = uri <<? parameters))
        else if (designOnly)
          Future.successful(HttpRequest(uri = uri <<? parameters.updated("filter", "_design")))
        else
          for {
            entity <- Marshal(docIds).to[RequestEntity]
          } yield HttpRequest(HttpMethods.POST, uri = uri <<? parameters.updated("filter", "_doc_ids"), entity = entity)
    }

    for {
      req <- request
      resp <- database.http(req)
    } yield resp.convertTo[Changes]
  }

  /** Returns a continuous stream representing the changes in the database. Each change produces an element in the stream.
   *  The returned stream can be cancelled using the kill switch returned by materializing it.
   *  E.g. if you want to log the changes to the console and shut it down after a while, you can write
   *  {{{
   *  val stream = db.changes.stream()
   *  val killSwitch = stream.toMat(Sink.foreach(println _))(Keep.left).run()
   *  ...
   *  killSwitch.shutdown()
   *  }}}
   */
  @deprecated("Use the `all` stream instead.", "Sohva 2.2.0")
  def stream(
    docIds: Iterable[String] = Vector.empty[String],
    conflicts: Boolean = false,
    descending: Boolean = false,
    filter: Option[String] = None,
    selector: Option[Selector] = None,
    designOnly: Boolean = false,
    includeDocs: Boolean = false,
    attachments: Boolean = false,
    attEncodingInfo: Boolean = false,
    lastEventId: Option[Int] = None,
    limit: Option[Int] = None,
    since: Option[Either[String, JsValue]] = None,
    style: Option[String] = None,
    view: Option[String] = None): Source[Change, UniqueKillSwitch] =
    all(docIds = docIds,
      conflicts = conflicts,
      descending = descending,
      filter = filter,
      selector = selector,
      designOnly = designOnly,
      includeDocs = includeDocs,
      attachments = attachments,
      attEncodingInfo = attEncodingInfo,
      lastEventId = lastEventId,
      limit = limit,
      since = makeSince(since),
      style = style,
      view = view)

  /** Returns a continuous stream representing the changes in the database. Each change produces an element in the stream.
   *  The returned stream can be cancelled using the kill switch returned by materializing it.
   *  E.g. if you want to log the changes to the console and shut it down after a while, you can write
   *  {{{
   *  val stream = db.changes.stream()
   *  val killSwitch = stream.toMat(Sink.foreach(println _))(Keep.left).run()
   *  ...
   *  killSwitch.shutdown()
   *  }}}
   */
  def all(
    docIds: Iterable[String] = Vector.empty[String],
    conflicts: Boolean = false,
    descending: Boolean = false,
    filter: Option[String] = None,
    selector: Option[Selector] = None,
    designOnly: Boolean = false,
    includeDocs: Boolean = false,
    attachments: Boolean = false,
    attEncodingInfo: Boolean = false,
    lastEventId: Option[Int] = None,
    limit: Option[Int] = None,
    since: Since = Origin,
    style: Option[String] = None,
    view: Option[String] = None): Source[Change, UniqueKillSwitch] = {

    val parameters = List(Some("heartbeat" -> "5000"),
      Some("feed" -> "continuous"),
      if (conflicts) Some("conflicts" -> "true") else None,
      if (descending) Some("descending" -> "true") else None,
      filter.map(s => "filter" -> s),
      if (includeDocs) Some("include_docs" -> "true") else None,
      if (attachments) Some("attachments" -> "true") else None,
      if (attEncodingInfo) Some("att_encoding_info" -> "true") else None,
      lastEventId.map(n => "last-event-id" -> n.toString),
      limit.map(n => "limit" -> n.toString),
      since.option.map(s => "since" -> s),
      style.map(s => "style" -> s),
      view.map(v => "view" -> v)).flatten.toMap

    val request = selector match {
      case Some(selector) =>
        for {
          entity <- Marshal(Map("selector" -> selector)).to[RequestEntity]
        } yield HttpRequest(HttpMethods.POST, uri = uri <<? parameters.updated("filter", "_selector"), entity = entity)
      case None =>
        if (docIds.isEmpty)
          Future.successful(HttpRequest(uri = uri <<? parameters))
        else if (designOnly)
          Future.successful(HttpRequest(uri = uri <<? parameters.updated("filter", "_design")))
        else
          for {
            entity <- Marshal(docIds).to[RequestEntity]
          } yield HttpRequest(HttpMethods.POST, uri = uri <<? parameters.updated("filter", "_doc_ids"), entity = entity)
    }

    Source.fromFuture(
      for (req <- request)
        yield database.couch.prepare(req))
      .via(database.couch.connectionFlow)
      .flatMapConcat(_.entity.dataBytes)
      .via(Framing.delimiter(ByteString("\n"), Int.MaxValue))
      .mapConcat(bs => if (bs.isEmpty) collection.immutable.Seq() else collection.immutable.Seq(JsonParser(bs.utf8String).convertTo[Change]))
      .viaMat(KillSwitches.single)(Keep.right)

  }

  override def toString =
    uri.toString

  private val uri = database.uri / "_changes"

}

case class Change(seq: JsValue, id: String, changes: Vector[Rev], deleted: Boolean, doc: Option[JsObject])

case class Rev(rev: String)

case class Changes(last_seq: JsValue, pending: Int, results: Vector[Change])
