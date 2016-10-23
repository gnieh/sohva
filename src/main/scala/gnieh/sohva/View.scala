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

import spray.json._

import scala.concurrent.Future

import akka.http.scaladsl.model._

/**
 * A view can be queried to get the result.
 *
 *  @groupdesc LowLevel Low-level classes that may break compatibility even between patch and minor versions
 *  @groupprio LowLevel 1001
 *
 *  @author Lucas Satabin
 */
class View(
    val design: String,
    val db: Database,
    val view: String) {

  import db.ec

  import SohvaProtocol._

  protected[this] val uri = db.uri / "_design" / design / "_view" / view

  /** Indicates whether this view exists */
  def exists: Future[Boolean] =
    for (h <- db.couch.rawHttp(HttpRequest(HttpMethods.HEAD, uri = uri)))
      yield h.status == StatusCodes.OK

  /**
   * Queries the view on the server and returned the untyped result.
   *
   *  ''Warning'': This is low-level API, and might break compatibility even between patch releases
   *
   *  @group LowLevel
   */
  def queryRaw(
    key: Option[JsValue] = None,
    keys: List[JsValue] = Nil,
    startkey: Option[JsValue] = None,
    startkey_docid: Option[String] = None,
    endkey: Option[JsValue] = None,
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
    update_seq: Boolean = false): Future[RawViewResult] = {

    // build options
    val options = List(
      key.map(k => "key" -> k.toJson.compactPrint),
      if (keys.nonEmpty) Some("keys" -> keys.toJson.compactPrint) else None,
      startkey.map(k => "startkey" -> k.toJson.compactPrint),
      startkey_docid.map("startkey_docid" -> _),
      endkey.map(k => "endkey" -> k.toJson.compactPrint),
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
      if (update_seq) Some("update_seq" -> true) else None
    )
      .flatten
      .filter(_ != null) // just in case somebody gave Some(null)...
      .map { case (name, value) => (name, value.toString) }
      .toMap

    for (
      res <- db.couch.http(buildReq(options)) withFailureMessage
        f"Raw query failed for view `$view' in design `$design' at $db"
    ) yield res.convertTo[RawViewResult]

  }

  /**
   * Queries the view on the server and returned the typed result.
   * Only the found keys are returned, errors are ignored in the result.
   *
   *  BE CAREFUL: If the types given to the constructor are not correct,
   *  strange things may happen! By 'strange', I mean exceptions
   */
  def query[Key: JsonFormat, Value: JsonReader, Doc: JsonReader](
    key: Option[Key] = None,
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
    update_seq: Boolean = false): Future[ViewResult[Key, Value, Doc]] = {

    // build options
    val options = List(
      key.map(k => "key" -> k.toJson.compactPrint),
      if (keys.nonEmpty) Some("keys" -> keys.toJson.compactPrint) else None,
      startkey.map(k => "startkey" -> k.toJson.compactPrint),
      startkey_docid.map("startkey_docid" -> _),
      endkey.map(k => "endkey" -> k.toJson.compactPrint),
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
      if (update_seq) Some("update_seq" -> true) else None
    )
      .flatten
      .filter(_ != null) // just in case somebody gave Some(null)...
      .map { case (name, value) => (name, value.toString) }
      .toMap

    for (
      res <- db.couch.http(buildReq(options)) withFailureMessage
        f"Query failed for view `$view' in design `$design' at $db"
    ) yield viewResult[Key, Value, Doc](res)

  }

  // helper methods
  protected def buildReq(options: Map[String, String]) = HttpRequest(uri = uri <<? options)

  private def viewResult[Key: JsonReader, Value: JsonReader, Doc: JsonReader](json: JsValue) = {
    val RawViewResult(total_rows, offset, rawRows, update_seq) = json.convertTo[RawViewResult]
    val rows =
      for (SuccessRawRow(id, key, value, doc) <- rawRows)
        yield Row(id, key.convertTo[Key], value.convertTo[Value], doc.map(_.convertTo[Doc]))
    ViewResult(total_rows.toInt, offset.toInt, rows, update_seq.map(_.toInt))
  }

  override def toString =
    uri.toString
}

/**
 * Used to query built-in view such as `_all_docs`.
 *
 *  @author Lucas Satabin
 */
private class BuiltInView(
  db: Database,
  view: String)
    extends View("", db, view) {

  override protected[this] val uri = db.uri / view

}

private class TemporaryView(
  db: Database,
  viewDoc: ViewDoc)
    extends BuiltInView(db, "_temp_view") {

  import SohvaProtocol._

  override protected def buildReq(options: Map[String, String]) =
    HttpRequest(HttpMethods.POST, uri = uri <<? options)
      .withEntity(HttpEntity(ContentTypes.`application/json`, postData))

  lazy val postData: String = viewDoc.toJson.compactPrint

}
case class ViewDoc(map: String, reduce: Option[String])

/**
 *  ''Warning'': This is low-level API, and might break compatibility even between patch releases
 *
 *  @group LowLevel
 */
final case class RawViewResult(
  total_rows: Long,
  offset: Long,
  rows: List[RawRow],
  update_seq: Option[Long])

/**
 *  ''Warning'': This is low-level API, and might break compatibility even between patch releases
 *
 *  @group LowLevel
 */
sealed trait RawRow

/**
 *  ''Warning'': This is low-level API, and might break compatibility even between patch releases
 *
 *  @group LowLevel
 */
final case class SuccessRawRow(
  id: Option[String],
  key: JsValue,
  value: JsValue,
  doc: Option[JsObject]) extends RawRow

/**
 *  ''Warning'': This is low-level API, and might break compatibility even between patch releases
 *
 *  @group LowLevel
 */
final case class ErrorRawRow(key: JsValue, error: String) extends RawRow

final case class ViewResult[Key, Value, Doc](
    total_rows: Int,
    offset: Int,
    rows: List[Row[Key, Value, Doc]],
    update_seq: Option[Int]) {

  self =>

  def values: List[(Key, Value)] =
    for (row <- rows)
      yield (row.key, row.value)

  def docs: List[(Key, Doc)] =
    for {
      row <- rows
      doc <- row.doc
    } yield (row.key, doc)

  def foreach(f: Row[Key, Value, Doc] => Unit): Unit =
    rows.foreach(f)

  def map[Key1, Value1, Doc1](f: Row[Key, Value, Doc] => Row[Key1, Value1, Doc1]): ViewResult[Key1, Value1, Doc1] =
    ViewResult(total_rows, offset, rows.map(f), update_seq)

  def flatMap[Key1, Value1, Doc1](f: Row[Key, Value, Doc] => ViewResult[Key1, Value1, Doc1]): ViewResult[Key1, Value1, Doc1] = {
    val results = rows.map(f)
    ViewResult(results.map(_.total_rows).sum, offset, results.flatMap(_.rows), update_seq)
  }

  def withFilter(p: Row[Key, Value, Doc] => Boolean): WithFilter =
    new WithFilter(p)

  class WithFilter(p: Row[Key, Value, Doc] => Boolean) {

    def foreach(f: Row[Key, Value, Doc] => Unit): Unit =
      for {
        row <- rows
        if p(row)
      } f(row)

    def map[Key1, Value1, Doc1](f: Row[Key, Value, Doc] => Row[Key1, Value1, Doc1]): ViewResult[Key1, Value1, Doc1] =
      ViewResult(rows.size, offset, for {
        row <- rows
        if p(row)
      } yield f(row), update_seq)

    def flatMap[Key1, Value1, Doc1](f: Row[Key, Value, Doc] => ViewResult[Key1, Value1, Doc1]): ViewResult[Key1, Value1, Doc1] = {
      val rows1 = for {
        row <- rows
        if p(row)
        row1 <- f(row).rows
      } yield row1
      ViewResult(rows1.size, offset, rows1, update_seq)
    }

    def withFilter(q: Row[Key, Value, Doc] => Boolean): WithFilter =
      new WithFilter(row => p(row) && q(row))

  }

}

case class Row[Key, Value, Doc](
  id: Option[String],
  key: Key,
  value: Value,
  doc: Option[Doc] = None)
