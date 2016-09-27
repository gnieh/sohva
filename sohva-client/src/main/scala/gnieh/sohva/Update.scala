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

import spray.json._

import akka.http.scaladsl.model._
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

/** An update handler that can be queried.
 *
 *  @author Lucas Satabin
 */
class Update(
    val design: String,
    val db: Database,
    val update: String) extends SprayJsonSupport {

  import db.ec

  import db.couch.materializer

  protected[this] val uri = db.uri / "_design" / design / "_update" / update

  /** Indicates whether this update handler exists */
  def exists: Future[Boolean] =
    for (h <- db.couch.rawHttp(HttpRequest(HttpMethods.HEAD, uri = uri)))
      yield h.status == StatusCodes.OK

  /** Queries the update handler as a POST request.
   *  `body` is sent as a json value.
   */
  def query[Body: RootJsonWriter, Resp: FromEntityUnmarshaller](
    body: Body,
    docId: Option[String] = None,
    parameters: Map[String, String] = Map()): Future[Resp] = {
    for {
      entity <- Marshal(body).to[RequestEntity]
      req = docId match {
        case Some(docId) => HttpRequest(HttpMethods.PUT, uri = uri / docId <<? parameters, entity = entity)
        case None        => HttpRequest(HttpMethods.POST, uri = uri <<? parameters, entity = entity)
      }
      resp <- db.couch.rawHttp(req)
      r <- Unmarshal(resp).to[Resp]
    } yield r
  }

  override def toString =
    uri.toString

}
