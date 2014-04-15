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
package control

import gnieh.sohva.async.{
  View => AView
}

import scala.util.Try

import net.liftweb.json.JValue

/** A view can be queried to get the result.
 *
 *  @author Lucas Satabin
 */
class View(wrapped: AView)
    extends gnieh.sohva.View[Try] {

  @inline
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
    update_seq: Boolean = false): Try[RawViewResult] =
    synced(wrapped.queryRaw(key = key,
      keys = keys,
      startkey = startkey,
      startkey_docid = startkey_docid,
      endkey = endkey,
      endkey_docid = endkey_docid,
      limit = limit,
      stale = stale,
      descending = descending,
      skip = skip,
      group = group,
      group_level = group_level,
      reduce = reduce,
      include_docs = include_docs,
      inclusive_end = inclusive_end,
      update_seq = update_seq))

  @inline
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
    update_seq: Boolean = false): Try[ViewResult[Key, Value, Doc]] =
    synced(wrapped.query[Key, Value, Doc](key = key,
      keys = keys,
      startkey = startkey,
      startkey_docid = startkey_docid,
      endkey = endkey,
      endkey_docid = endkey_docid,
      limit = limit,
      stale = stale,
      descending = descending,
      skip = skip,
      group = group,
      group_level = group_level,
      reduce = reduce,
      include_docs = include_docs,
      inclusive_end = inclusive_end,
      update_seq = update_seq))

}

