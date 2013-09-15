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

/** A view can be queried to get the result.
 *
 *  @author Lucas Satabin
 */
trait View[Key, Value, Doc] {

  type Result[T]

  /** Queries the view on the server and returned the typed result.
   *  BE CAREFUL: If the types given to the constructor are not correct,
   *  strange things may happen! By 'strange', I mean exceptions
   */
  def query(key: Option[Key] = None,
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

final case class ViewResult[Key, Value, Doc](total_rows: Int,
                                             offset: Int,
                                             rows: List[Row[Key, Value, Doc]]) {

  def values =
    rows.map(row => (row.key, row.value))

  def docs =
    rows.map(row => row.doc.map(_ => (row.key, row.doc))).flatten.toMap

  def foreach(f: Row[Key, Value, Doc] => Unit) =
    rows.foreach(f)

}

case class Row[Key, Value, Doc](id: String,
                                key: Key,
                                value: Value,
                                doc: Option[Doc] = None)
