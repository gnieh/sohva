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

import spray.json._

import scala.concurrent.Future

import spray.client.pipelining._

import spray.http.StatusCodes

/** A view can be queried to get the result.
 *
 *  @author Lucas Satabin
 */
class View(
  val design: String,
  val db: Database,
  val view: String)
    extends gnieh.sohva.View[Future] {

  import db.ec

  import SohvaProtocol._

  protected[this] def uri = db.uri / "_design" / design / "_view" / view

  def exists: Future[Boolean] =
    for (h <- db.couch.rawHttp(Head(uri)))
      yield h.status == StatusCodes.OK

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

  def query[Key: JsonFormat, Value: JsonReader, Doc: JsonReader](key: Option[Key] = None,
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
  protected def buildReq(options: Map[String, String]) = Get(uri <<? options)

  private def viewResult[Key: JsonReader, Value: JsonReader, Doc: JsonReader](json: JsValue) = {
    val RawViewResult(total_rows, offset, rawRows, update_seq) = json.convertTo[RawViewResult]
    val rows =
      for (RawRow(id, key, value, doc) <- rawRows)
        yield Row(id, key.convertTo[Key], value.convertTo[Value], doc.map(_.convertTo[Doc]))
    ViewResult(total_rows.toInt, offset.toInt, rows, update_seq.map(_.toInt))
  }

  override def toString =
    uri.toString

}

/** Used to query built-in view such as `_all_docs`.
 *
 *  @author Lucas Satabin
 */
private class BuiltInView(
  db: Database,
  view: String)
    extends View("", db, view) {

  override protected[this] def uri = db.uri / view

}

private class TemporaryView(
  db: Database,
  viewDoc: ViewDoc)
    extends BuiltInView(db, "_temp_view") {

  import spray.http.HttpEntity
  import spray.http.ContentTypes._

  import SohvaProtocol._

  override protected def buildReq(options: Map[String, String]) =
    Post(uri <<? options)
      .withEntity(HttpEntity(`application/json`, postData))

  lazy val postData: String = viewDoc.toJson.compactPrint

}
