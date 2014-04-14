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

import net.liftweb.json.{
  JValue,
  JObject
}

/** A view can be queried to get the result.
 *
 *  @author Lucas Satabin
 */
trait View[Result[_]] {

  /** Queries the view on the server and returned the untyped result. */
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
    update_seq: Boolean = false): Result[RawViewResult]

  /** Queries the view on the server and returned the typed result.
   *  BE CAREFUL: If the types given to the constructor are not correct,
   *  strange things may happen! By 'strange', I mean exceptions
   */
  def query[Key: Manifest, Value: Manifest, Doc: Manifest](
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
    update_seq: Boolean = false): Result[ViewResult[Key, Value, Doc]]

}

case class ViewDoc(map: String, reduce: Option[String])

final case class RawViewResult(
  total_rows: Long,
  offset: Long,
  rows: List[RawRow],
  update_seq: Option[Long])

final case class RawRow(
  id: Option[String],
  key: JValue,
  value: JValue,
  doc: Option[JObject])

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

