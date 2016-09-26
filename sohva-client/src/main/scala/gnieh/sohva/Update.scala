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

import scala.concurrent.Future

import spray.http._

import spray.json._

import spray.client.pipelining._

import spray.httpx.unmarshalling._

/** An update handler that can be queried.
 *
 *  @author Lucas Satabin
 */
class Update(
  val design: String,
  val db: Database,
  val update: String)
    extends SprayJsonSupport {

  import db.ec

  protected[this] def uri = db.uri / "_design" / design / "_update" / update

  /** Indicates whether this update handler exists */
  def exists: Future[Boolean] =
    for (h <- db.couch.rawHttp(Head(uri)))
      yield h.status == StatusCodes.OK

  /** Queries the update handler as a POST request.
   *  `body` is sent as a json value.
   */
  def query[Body: RootJsonWriter, Resp: Unmarshaller](
    body: Body,
    docId: Option[String] = None,
    parameters: Map[String, String] = Map()): Future[Resp] = {
    val req = docId match {
      case Some(docId) => Put(uri / docId <<? parameters, body)
      case None        => Post(uri <<? parameters, body)
    }
    for (resp <- db.couch.rawHttp(req))
      yield resp.as[Resp] match {
      case Left(error) => throw new SohvaException(f"Unable to deserialize result for update function $update: $error")
      case Right(v)    => v
    }
  }

  /** Queries the update handler as a POST form-data request. */
  def queryForm[Resp: Unmarshaller](
    data: Map[String, String],
    docId: String,
    parameters: Map[String, String] = Map()): Future[Resp] =
    for (resp <- db.couch.rawHttp(Post(uri / docId <<? parameters, FormData(data))))
      yield resp.as[Resp] match {
      case Left(error) => throw new SohvaException(f"Unable to deserialize result for update function $update: $error")
      case Right(v)    => v
    }

  override def toString =
    uri.toString

}
