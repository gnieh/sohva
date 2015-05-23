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

import scala.concurrent.{
  Future,
  ExecutionContext
}

import scala.util.Try

import spray.http._
import spray.client.pipelining._

import akka.actor._

import spray.json._

/** A CouchDB instance.
 *  Allows users to access the different databases and information.
 *  This is the key class to start with when one wants to work with couchdb.
 *  Through this one you will get access to the databases.
 *
 *  @author Lucas Satabin
 *
 */
abstract class CouchDB extends gnieh.sohva.CouchDB[Future] with SprayJsonSupport {

  self =>

  implicit def ec: ExecutionContext

  import SohvaProtocol._

  def system: ActorSystem

  val ssl: Boolean

  def info: Future[CouchInfo] =
    for (
      json <- http(Get(uri)) withFailureMessage
        f"Unable to fetch info from $uri"
    ) yield asCouchInfo(json)

  def database(name: String, credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Database =
    new Database(name, this, credit, strategy)

  def replicator(name: String = "_replicator", credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Replicator =
    new Replicator(name, this, credit, strategy)

  def _all_dbs: Future[List[String]] =
    for (
      dbs <- http(Get(uri / "_all_dbs")) withFailureMessage
        f"Unable to fetch databases list from $uri"
    ) yield asStringList(dbs)

  def _uuid: Future[String] =
    for (uuid <- _uuids(1))
      yield uuid.head

  def _uuids(count: Int = 1): Future[List[String]] =
    for (
      uuids <- http(Get(uri / "_uuids" <<? Map("count" -> count.toString))) withFailureMessage
        f"Failed to fetch $count uuids from $uri"
    ) yield asUuidsList(uuids)

  def _config: Future[Configuration] =
    for (
      config <- http(Get(uri / "_config")) withFailureMessage
        f"Failed to fetch config from $uri"
    ) yield config.convertTo[Configuration]

  def _config(section: String): Future[Map[String, String]] =
    for (
      section <- http(Get(uri / "_config" / section)) withFailureMessage
        f"Failed to fetch config for $section from $uri"
    ) yield section.convertTo[Map[String, String]]

  def _config(section: String, key: String): Future[Option[String]] =
    for (
      section <- _config(section) withFailureMessage
        f"Failed to fetch config for $section with key `$key' from $uri"
    ) yield section.get(key)

  def saveConfigValue(section: String, key: String, value: String): Future[Boolean] =
    for (
      res <- http(Put(uri / "_config" / section / key, value.toJson)) withFailureMessage
        f"Failed to save config $section with key `$key' and value `$value' to $uri"
    ) yield ok(res)

  def deleteConfigValue(section: String, key: String): Future[Boolean] =
    for (
      res <- http(Delete(uri / "_config" / section / key)) withFailureMessage
        f"Failed to delete config $section with key `$key' from $uri"
    ) yield ok(res)

  def contains(dbName: String): Future[Boolean] =
    for (dbs <- _all_dbs)
      yield dbs.contains(dbName)

  // user management section

  /** Exposes the interface for managing couchdb users. */
  object users extends Users(this)

  // helper methods

  protected[sohva] def uri: Uri

  protected[sohva] def prepare(req: HttpRequest): HttpRequest

  protected[sohva] val pipeline: HttpRequest => Future[HttpResponse]

  protected[sohva] def rawHttp(req: HttpRequest): Future[HttpResponse] =
    pipeline(prepare(req))

  protected[sohva] def http(req: HttpRequest): Future[JsValue] =
    rawHttp(req).flatMap(handleCouchResponse)

  protected[sohva] def optHttp(req: HttpRequest): Future[Option[JsValue]] =
    rawHttp(req).flatMap(handleOptionalCouchResponse)

  private def handleCouchResponse(response: HttpResponse): Future[JsValue] = {
    val json = JsonParser(response.entity.asString)
    if (response.status.isSuccess) {
      Future.successful(json)
    } else {
      // something went wrong...
      val code = response.status.intValue
      val error = Try(json.convertTo[ErrorResult]).toOption
      Future.failed(CouchException(code, error))
    }
  }

  private def handleOptionalCouchResponse(response: HttpResponse): Future[Option[JsValue]] =
    handleCouchResponse(response) map (Some(_)) recoverWith {
      case CouchException(404, _) => Future.successful(None)
      case err                    => Future.failed(err)
    }

  @inline
  protected[sohva] def ok(json: JsValue) =
    json.convertTo[OkResult].ok

  @inline
  private def asCouchInfo(json: JsValue) =
    json.convertTo[CouchInfo]

  @inline
  private def asStringList(json: JsValue) =
    json.convertTo[List[String]]

  @inline
  private def asUuidsList(json: JsValue) =
    json.convertTo[Uuids].uuids

  @inline
  private def asConfiguration(json: JsValue) =
    json.convertTo[Configuration]

  override def toString =
    uri.toString
}
