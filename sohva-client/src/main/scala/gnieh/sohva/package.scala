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
package gnieh

import scala.concurrent.Future

import java.security.MessageDigest

import scala.util.Random

import rx.lang.scala._

import spray.json._

import akka.http.scaladsl.model._

import java.io.File
import scala.concurrent.{ ExecutionContext, Future }

/** Contains all the classes needed to interact with a couchdb server.
 *  Classes in this package allows the user to:
 *  - create/delete new databases into a couchdb instance,
 *  - create/update/delete documents into a couchdb database,
 *  - create/update/delete designs and views,
 *  - manage built-in security document of a given database,
 *  - create/update/delete couchdb users,
 *  - use couchdb authentication API to create sessions and use built-in permission system.
 *
 *  @author Lucas Satabin
 *
 */
package object sohva {

  // register the COPY method
  val COPY = HttpMethod.custom("COPY")

  val now = Some(Left("now"))

  private[sohva] implicit class EnhancedUri(val uri: Uri) extends AnyVal {

    def /(part: String) =
      uri.withPath(part.split("/").foldLeft(uri.path)(_ / _))

    def /(part: Option[String]) = part match {
      case Some(part) => uri.withPath(part.split("/").foldLeft(uri.path)(_ / _))
      case None       => uri
    }

    def <<?(params: Map[String, String]): Uri =
      uri.withQuery(Uri.Query(params))

    def <<?(params: Seq[(String, String)]): Uri =
      uri.withQuery(Uri.Query(params: _*))

    def <<?(param: Option[(String, String)]): Uri =
      param match {
        case Some(param) => uri.withQuery(Uri.Query(param))
        case None        => uri
      }

  }

  private[sohva] implicit class Req(val req: HttpRequest) extends AnyVal {

    def <:<(headers: Iterable[(String, String)]): HttpRequest =
      headers.foldLeft(req) {
        case (acc, (name, value)) =>
          HttpHeader.parse(name, value) match {
            case HttpHeader.ParsingResult.Ok(header, _) =>
              acc.addHeader(header)
            case _ =>
              acc
          }
      }

  }

  private[sohva] implicit class EnhancedFuture[T](val f: Future[T]) {

    def withFailureMessage[U](msg: String)(implicit ec: ExecutionContext) = f.recover {
      case e: Exception =>
        throw new SohvaException(msg, e)
    }
  }

  protected[sohva] def bytes2string(bytes: Array[Byte]) =
    bytes.foldLeft(new StringBuilder) {
      (res, byte) =>
        res.append(Integer.toHexString(byte & 0xff))
    }.toString

  protected[sohva] def hash(s: String) = {
    val md = MessageDigest.getInstance("SHA-1")
    bytes2string(md.digest(s.getBytes("UTF-8")))
  }

  protected[sohva] def passwordSha(password: String) = {

    // compute the password hash
    // the password string is concatenated with the generated salt
    // and the result is hashed using SHA-1
    val saltArray = new Array[Byte](16)
    Random.nextBytes(saltArray)
    val salt = bytes2string(saltArray)

    (salt, hash(password + salt))
  }

  implicit class CouchJson[T <: IdRev](val value: T) extends AnyVal {

    import DefaultJsonProtocol._

    def toCouchJson(implicit writer: JsonWriter[T]): JsValue =
      writer.write(value) match {
        case JsObject(fields) =>
          JsObject(fields + ("_rev" -> value._rev.toJson))
        case _ =>
          serializationError("Object expected")
      }

  }

}
