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

import strategy._

import dispatch._
import Defaults._

import scala.concurrent.Future

import com.ning.http.client.{
  RequestBuilder,
  Response,
  AsyncHandler
}

import net.liftweb.json._

/** A CouchDB instance.
 *  Allows users to access the different databases and information.
 *  This is the key class to start with when one wants to work with couchdb.
 *  Through this one you will get access to the databases.
 *
 *  @author Lucas Satabin
 *
 */
abstract class CouchDB extends gnieh.sohva.CouchDB[AsyncResult] {

  self =>

  def info: AsyncResult[CouchInfo] =
    for (json <- http(request).right)
      yield asCouchInfo(json)

  def database(name: String, credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Database =
    new Database(name, this, credit, strategy)

  def replicator(name: String = "_replicator", credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Replicator =
    new Replicator(name, this, credit, strategy)

  def _all_dbs: AsyncResult[List[String]] =
    for (dbs <- http(request / "_all_dbs").right)
      yield asStringList(dbs)

  def _uuid: AsyncResult[String] =
    for (uuid <- _uuids(1).right)
      yield uuid.head

  def _uuids(count: Int = 1): AsyncResult[List[String]] =
    for (uuids <- http(request / "_uuids" <<? Map("count" -> count.toString)).right)
      yield asUuidsList(uuids)

  def _config: AsyncResult[Configuration] =
    for (config <- http(request / "_config").right)
      yield serializer.fromJson[Configuration](config)

  def _config(section: String): AsyncResult[Map[String, String]] =
    for (section <- http(request / "_config" / section).right)
      yield serializer.fromJson[Map[String, String]](section)

  def _config(section: String, key: String): AsyncResult[Option[String]] =
    for (section <- _config(section).right)
      yield section.get(key)

  def saveConfigValue(section: String, key: String, value: String): AsyncResult[Boolean] =
    for (res <- http((request / "_config" / section / key << serializer.toJson(value)).PUT).right)
      yield ok(res)

  def deleteConfigValue(section: String, key: String): AsyncResult[Boolean] =
    for (res <- http((request / "_config" / section / key).DELETE).right)
      yield ok(res)

  def contains(dbName: String): AsyncResult[Boolean] =
    for (dbs <- _all_dbs.right)
      yield dbs.contains(dbName)

  // user management section

  /** Exposes the interface for managing couchdb users. */
  object users extends Users(this)

  // helper methods

  protected[sohva] def request: RequestBuilder

  protected[sohva] def _http[T](req: Req, handler: AsyncHandler[T]): Future[T]

  protected[sohva] def http(request: RequestBuilder, contentType: String = "application/json", contentEncoding: String = "UTF-8"): AsyncResult[String] =
    _http(request.setBodyEncoding(contentEncoding) <:< Map("Content-Type" -> contentType), new FunctionHandler(handleCouchResponse _))

  protected[sohva] def optHttp(request: RequestBuilder, contentType: String = "application/json", contentEncoding: String = "UTF-8"): AsyncResult[Option[String]] =
    _http(
      request.setBodyEncoding(contentEncoding) <:< Map("Content-Type" -> contentType), new FunctionHandler(handleOptionalCouchResponse _))

  private def handleCouchResponse(response: Response): RawResult[String] = {
    val json = as.String(response)
    val code = response.getStatusCode
    if (code / 100 != 2) {
      // something went wrong...
      val error = serializer.fromJsonOpt[ErrorResult](json)
      Left((code, error))
    } else {
      Right(json)
    }
  }

  private def handleOptionalCouchResponse(response: Response): RawResult[Option[String]] =
    handleCouchResponse(response) match {
      case Right(v)       => Right(Some(v))
      case Left((404, _)) => Right(None)
      case Left(err)      => Left(err)
    }

  @inline
  protected[sohva] def ok(json: String) =
    serializer.fromJson[OkResult](json).ok

  @inline
  private def asCouchInfo(json: String) =
    serializer.fromJson[CouchInfo](json)

  @inline
  private def asStringList(json: String) =
    serializer.fromJson[List[String]](json)

  @inline
  private def asUuidsList(json: String) =
    serializer.fromJson[Uuids](json).uuids

  @inline
  private def asConfiguration(json: String) =
    serializer.fromJson[Configuration](json)

}
