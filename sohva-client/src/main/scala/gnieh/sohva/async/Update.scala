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

import scala.concurrent.Future

import spray.http._

import spray.client.pipelining._

import spray.httpx.unmarshalling._

class Update(
  val design: String,
  val db: Database,
  val update: String)
    extends gnieh.sohva.Update[Future]
    with LiftMarshalling {

  import db.ec

  implicit def formats = db.serializer.formats

  protected[this] def uri = db.uri / "_design" / design / "_update" / update

  def exists: Future[Boolean] =
    for (h <- db.couch.optHttp(Head(uri)))
      yield h.isDefined

  def query[Body, Resp: Unmarshaller](body: Body, docId: Option[String] = None, parameters: Map[String, String] = Map()): Future[Resp] = {
    val req = docId match {
      case Some(docId) => Put(uri / docId <<? parameters, db.serializer.toJson(body))
      case None        => Post(uri <<? parameters, db.serializer.toJson(body))
    }
    for (resp <- db.couch.rawHttp(req))
      yield resp.as[Resp] match {
      case Left(error) => throw new SohvaException(f"Unable to deserialize result for update function $update: $error")
      case Right(v)    => v
    }
  }

  def queryForm[Resp: Unmarshaller](data: Map[String, String], docId: String, parameters: Map[String, String] = Map()): Future[Resp] =
    for (resp <- db.couch.rawHttp(Post(uri / docId <<? parameters, FormData(data))))
      yield resp.as[Resp] match {
      case Left(error) => throw new SohvaException(f"Unable to deserialize result for update function $update: $error")
      case Right(v)    => v
    }

  override def toString =
    uri.toString

}
