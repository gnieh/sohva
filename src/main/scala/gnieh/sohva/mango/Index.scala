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

import scala.concurrent.Future

import spray.json._

import akka.http.scaladsl.model._
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

/** The index manager for a given database.
 *
 *  @author Lucas Satabin
 */
class Index(db: Database) {

  import SprayJsonSupport._
  import MangoProtocol._

  import db.couch.ec

  /** Creates a new index for the given fields and optional name and design. */
  def create(
    fields: Vector[Sort],
    ddoc: Option[String] = None,
    name: Option[String] = None,
    partial_filter_selector: Option[Selector] = None): Future[IndexCreationResult] = {
    val jsFields = Seq("index" -> JsObject(Map("fields" -> fields.toJson) ++ partial_filter_selector.map("partial_filter_selector" -> _.toJson))) ++ ddoc.map("ddoc" -> _.toJson) ++ name.map("name" -> _.toJson)
    for {
      entity <- Marshal(JsObject(jsFields: _*)).to[RequestEntity]
      res <- db.couch.http(HttpRequest(HttpMethods.POST, uri = uri, entity = entity))
    } yield res.convertTo[IndexCreationResult]
  }

  /** Returns the index information for the underlying database. */
  def getIndexes: Future[IndexInfo] =
    for (res <- db.couch.http(HttpRequest(uri = uri)))
      yield res.convertTo[IndexInfo]

  /** Deletes the given index from the database. */
  def delete(ddoc: String, name: String): Future[Boolean] =
    for (res <- db.couch.http(HttpRequest(HttpMethods.DELETE, uri = uri / ddoc / "json" / name)))
      yield res.asJsObject("ok").convertTo[Boolean]

  protected[sohva] val uri =
    db.uri / "_index"

}

final case class IndexCreationResult(result: String, id: String, name: String)

final case class IndexInfo(total_rows: Int, indexes: Vector[IndexDef])

final case class IndexDef(ddoc: Option[String], name: String, `type`: String, `def`: Def)

final case class Def(fields: Vector[Sort], partial_filter_selector: Option[Selector])
