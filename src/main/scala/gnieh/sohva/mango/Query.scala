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

final case class Query(selector: Selector, fields: List[String], sort: List[Sort], limit: Option[Int], skip: Option[Int], use_index: Option[UseIndex]) {

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

  /** Creates a query where some properties are removed. */
  def without(without: Without*): Query =
    without.foldLeft(this) {
      case (q, mango.fields) =>
        q.copy(fields = Nil)
      case (q, mango.sort) =>
        q.copy(sort = Nil)
      case (q, mango.limit) =>
        q.copy(limit = None)
      case (q, mango.skip) =>
        q.copy(skip = None)
      case (q, mango.index) =>
        q.copy(use_index = None)
    }

}

sealed trait Without
case object fields extends Without
case object sort extends Without
case object limit extends Without
case object skip extends Without
case object index extends Without
