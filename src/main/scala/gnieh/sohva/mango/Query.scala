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
package mango

import spray.json._

final case class Query(selector: Selector, fields: Iterable[String], sort: Seq[Sort], limit: Option[Int], skip: Option[Int], use_index: Option[UseIndex], r: Option[Int] = None, bookmark: Option[String] = None) {

  /** Creates a query with a new selector. */
  def where(sel: Selector): Query =
    copy(selector = sel)

  /** Creates a query with a new list of fields to select. */
  def fields(flds: String*): Query =
    copy(fields = flds.toList)

  /** Creates a query with a new list of sorts. */
  def sortBy(s: Sort*): Query =
    copy(sort = s.toList)

  /** Creates a query with a new limit. */
  def limit(l: Int): Query =
    copy(limit = Some(l))

  /** Creates a query with a new skip. */
  def skip(s: Int): Query =
    copy(skip = Some(s))

  /** Creates a query with a new use index. */
  def use(idx: String): Query =
    copy(use_index = Some(Left(idx)))

  /** Creates a query with a new use index. */
  def use(idx: (String, String)): Query =
    copy(use_index = Some(Right(idx)))

  /** Creates a query with a new quorum. */
  def r(i: Int): Query =
    copy(r = Some(i))

  /** Creates a query where some properties are removed. */
  def without(without: Without*): Query =
    without.foldLeft(this) {
      case (q, Without.Fields) =>
        q.copy(fields = Nil)
      case (q, Without.Sort) =>
        q.copy(sort = Nil)
      case (q, Without.Limit) =>
        q.copy(limit = None)
      case (q, Without.Skip) =>
        q.copy(skip = None)
      case (q, Without.Index) =>
        q.copy(use_index = None)
      case (q, Without.R) =>
        q.copy(r = None)
      case (q, Without.Bookmark) =>
        q.copy(bookmark = None)
    }

}
