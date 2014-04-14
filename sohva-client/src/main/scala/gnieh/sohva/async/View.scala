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

import net.liftweb.json._

import scala.concurrent.Future

import spray.client.pipelining._

/** A view can be queried to get the result.
 *
 *  @author Lucas Satabin
 */
class View(
  val design: String,
  val db: Database,
  val view: String)
    extends gnieh.sohva.View[Future] {

  import db.couch.serializer
  import db.ec
  import serializer.formats

  protected[this] def uri = db.uri / "_design" / design / "_view" / view

  def queryRaw(
    key: Option[JValue] = None,
    keys: List[JValue] = Nil,
    startkey: Option[JValue] = None,
    startkey_docid: Option[String] = None,
    endkey: Option[JValue] = None,
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
      key.map(k => "key" -> compact(render(serializer.toJson(k)))),
      if (keys.nonEmpty) Some("keys" -> compact(render(serializer.toJson(keys)))) else None,
      startkey.map(k => "startkey" -> compact(render(serializer.toJson(k)))),
      startkey_docid.map("startkey_docid" -> _),
      endkey.map(k => "endkey" -> compact(render(serializer.toJson(k)))),
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

    for (res <- db.couch.http(Get(uri <<? options)))
      yield rawViewResult(res)

  }

  def query[Key: Manifest, Value: Manifest, Doc: Manifest](key: Option[Key] = None,
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
      key.map(k => "key" -> compact(render(serializer.toJson(k)))),
      if (keys.nonEmpty) Some("keys" -> compact(render(serializer.toJson(keys)))) else None,
      startkey.map(k => "startkey" -> compact(render(serializer.toJson(k)))),
      startkey_docid.map("startkey_docid" -> _),
      endkey.map(k => "endkey" -> compact(render(serializer.toJson(k)))),
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

    for (res <- db.couch.http(Get(uri <<? options)))
      yield viewResult[Key, Value, Doc](res)

  }

  // helper methods

  private def rawViewResult(json: JValue) = {
    val offset = (json \ "offset").extractOpt[Long].getOrElse(0l)
    val rows = (json \ "rows") match {
      case JArray(rows) =>
        for(row <- rows)
          yield {
            val key = row \ "key"
            val id = (row \ "id").extractOpt[String]
            val value = row \ "value"
            val doc = (row \ "doc").extractOpt[JObject]
            RawRow(id, key, value, doc)
          }
      case _ =>
        Nil
    }
    val total_rows = (json \ "total_rows").extractOpt[Long].getOrElse(rows.size.toLong)
    val update_seq = (json \ "update_seq").extractOpt[Long]
    RawViewResult(total_rows, offset, rows, update_seq)
  }

  private def viewResult[Key: Manifest, Value: Manifest, Doc: Manifest](json: JValue) = {
    val offset = (json \ "offset").extractOpt[Int].getOrElse(0)
    val rows = (json \ "rows") match {
      case JArray(rows) =>
        rows.flatMap { row =>
          for {
            key <- (row \ "key").extractOpt[Key]
            id = (row \ "id").extractOpt[String]
            value <- (row \ "value").extractOpt[Value]
            doc = (row \ "doc").extractOpt[Doc]
          } yield Row(id, key, value, doc)
        }
      case _ =>
        Nil
    }
    val total_rows = (json \ "total_rows").extractOpt[Int].getOrElse(rows.size)
    val update_seq = (json \ "update_seq").extractOpt[Int]
    ViewResult(total_rows, offset, rows, update_seq)
  }

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

