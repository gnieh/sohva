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

import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling._

/** A show function that can be queried.
 *
 *  @author Lucas Satabin
 */
class Show(
    val design: String,
    val db: Database,
    val show: String) {

  import db.ec

  import db.couch.materializer

  protected[this] val uri = db.uri / "_design" / design / "_show" / show

  /** Indicates whether this view exists */
  def exists: Future[Boolean] =
    for (h <- db.couch.rawHttp(HttpRequest(HttpMethods.HEAD, uri = uri)))
      yield h.status == StatusCodes.OK

  /** Returns the result of querying the show function with the document with the given identifier
   *  or `None` for the `null` document.
   */
  def query[T: FromEntityUnmarshaller](docId: Option[String] = None, format: Option[String] = None): Future[T] =
    for {
      resp <- db.couch.rawHttp(HttpRequest(uri = uri / docId <<? format.map(f => ("format", f))))
      t <- Unmarshal(resp).to[T]
    } yield t

  override def toString =
    uri.toString

}
