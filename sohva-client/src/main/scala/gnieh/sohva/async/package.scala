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

import spray.http._
import spray.httpx._
import spray.client.pipelining._

import java.io.File
import scala.concurrent.{ ExecutionContext, Future }

package object async {

  // register the COPY method
  val COPY = HttpMethod.custom("COPY", true, true, false)
  HttpMethods.register(COPY)
  val Copy = new RequestBuilding.RequestBuilder(COPY)

  private[async] implicit class EnhancedUri(val uri: Uri) extends AnyVal {

    def /(part: String) =
      uri.withPath(part.split("/").foldLeft(uri.path)(_ / _))

    def <<?(params: Map[String, String]): Uri =
      uri.withQuery(params)

    def <<?(params: Seq[(String, String)]): Uri =
      uri.withQuery(params: _*)

    def <<?(param: Option[(String, String)]): Uri =
      param match {
        case Some(param) => uri.withQuery(param)
        case None        => uri
      }

  }

  private[async] implicit class Req(val req: HttpRequest) extends AnyVal {

    def <:<(headers: Iterable[(String, String)]): HttpRequest =
      headers.foldLeft(req) {
        case (acc, (name, value)) =>
          acc ~> addHeader(name, value)
      }

  }

  private[async] implicit class EnhancedFuture[T](val f: Future[T]) {

    def withFailureMessage[U](msg: String)(implicit ec: ExecutionContext) = f.recover {
      case e: Exception =>
        throw new SohvaException(msg, e)
    }
  }

}

