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

import strategy._

import scala.concurrent.{
  Future,
  ExecutionContext
}

import scala.util.Try

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.unmarshalling._

import akka.stream.{
  Materializer,
  KillSwitches,
  UniqueKillSwitch
}
import akka.stream.scaladsl._

import akka.actor._

import akka.util.ByteString

import spray.json._

/** A CouchDB instance.
 *  Allows users to access the different databases and information.
 *  This is the key class to start with when one wants to work with couchdb.
 *  Through this one you will get access to the databases.
 *
 *  @author Lucas Satabin
 *
 */
abstract class CouchDB {

  implicit def ec: ExecutionContext

  import SohvaProtocol._
  import SprayJsonSupport._

  implicit val system: ActorSystem

  implicit val materializer: Materializer

  /** The couchdb instance host name. */
  val host: String

  /** The couchdb instance port. */
  val port: Int

  /** Whether to use ssl */
  val ssl: Boolean

  /** Returns the couchdb instance information */
  def info: Future[CouchInfo] =
    for (
      json <- http(HttpRequest(uri = uri)) withFailureMessage
        f"Unable to fetch info from $uri"
    ) yield asCouchInfo(json)

  /** Returns the database on the given couch instance. */
  def database(name: String, credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Database =
    new Database(name, this, credit, strategy)

  /** Returns the replicator database */
  def replicator(name: String = "_replicator", credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Replicator =
    new Replicator(name, this, credit, strategy)

  /** Returns the names of all databases in this couch instance. */
  def _all_dbs: Future[List[String]] =
    for (
      dbs <- http(HttpRequest(uri = uri / "_all_dbs")) withFailureMessage
        f"Unable to fetch databases list from $uri"
    ) yield asStringList(dbs)

  def dbUpdates(timeout: Option[Int] = None, heartbeat: Boolean = true): Source[DbUpdate, UniqueKillSwitch] = {

    val parameters = List(
      Some("feed" -> "continuous"),
      timeout.map(t => "timeout" -> t.toString),
      Some("heartbeat" -> heartbeat.toString)).flatten

    Source.single(prepare(HttpRequest(uri = uri / "_db_updates" <<? parameters)))
      .via(connectionFlow)
      .flatMapConcat(_.entity.dataBytes)
      .via(Framing.delimiter(ByteString("\n"), Int.MaxValue))
      .mapConcat(bs => if (bs.isEmpty) collection.immutable.Seq() else collection.immutable.Seq(JsonParser(bs.utf8String).convertTo[DbUpdate]))
      .viaMat(KillSwitches.single)(Keep.right)
  }

  /** Returns the list of nodes known by this node and the clusters.
   *
   *  @group CouchDB2
   */
  def membership: Future[Membership] =
    for {
      mem <- http(HttpRequest(uri = uri / "_membership")).withFailureMessage(f"Unable to fetch membership frin $uri")
    } yield mem.convertTo[Membership]

  /** Restarts the CouchDB instance. */
  def restart: Future[Boolean] =
    for (resp <- http(HttpRequest(HttpMethods.POST, uri = uri / "_restart")).withFailureMessage(f"Unable to restart instance at $uri"))
      yield resp.asJsObject("ok").convertTo[Boolean]

  /** Returns one UUID */
  def _uuid: Future[String] =
    for (uuid <- _uuids(1))
      yield uuid.head

  /** Returns the requested number of UUIDS (by default 1). */
  def _uuids(count: Int = 1): Future[List[String]] =
    for (
      uuids <- http(HttpRequest(uri = uri / "_uuids" <<? Map("count" -> count.toString))) withFailureMessage
        f"Failed to fetch $count uuids from $uri"
    ) yield asUuidsList(uuids)

  /** Returns the configuration object for this CouchDB instance */
  def _config: Future[Configuration] =
    for (
      config <- http(HttpRequest(uri = uri / "_config")) withFailureMessage
        f"Failed to fetch config from $uri"
    ) yield config.convertTo[Configuration]

  /** Returns the configuration section identified by its name
   *  (an empty map is returned if the section does not exist)
   */
  def _config(section: String): Future[Map[String, String]] =
    for (
      section <- http(HttpRequest(uri = uri / "_config" / section)) withFailureMessage
        f"Failed to fetch config for $section from $uri"
    ) yield section.convertTo[Map[String, String]]

  /** Returns the configuration value
   *  Returns `None` if the value does not exist
   */
  def _config(section: String, key: String): Future[Option[String]] =
    for (
      section <- _config(section) withFailureMessage
        f"Failed to fetch config for $section with key `$key' from $uri"
    ) yield section.get(key)

  /** Saves the given key/value association in the specified section
   *  The section and/or the key is created if it does not exist
   */
  def saveConfigValue(section: String, key: String, value: String): Future[Boolean] =
    for {
      entity <- Marshal(value.toJson).to[RequestEntity]
      res <- http(HttpRequest(HttpMethods.PUT, uri = uri / "_config" / section / key, entity = entity)) withFailureMessage
        f"Failed to save config $section with key `$key' and value `$value' to $uri"
    } yield ok(res)

  /** Deletes the given configuration key inthe specified section */
  def deleteConfigValue(section: String, key: String): Future[Boolean] =
    for (
      res <- http(HttpRequest(HttpMethods.DELETE, uri = uri / "_config" / section / key)) withFailureMessage
        f"Failed to delete config $section with key `$key' from $uri"
    ) yield ok(res)

  /** Indicates whether this couchdb instance contains the given database */
  def contains(dbName: String): Future[Boolean] =
    for (dbs <- _all_dbs)
      yield dbs.contains(dbName)

  /** Exposes the interface for managing couchdb users. */
  object users extends Users(this)

  // helper methods

  protected[sohva] val uri: Uri

  private lazy val http = Http()

  protected[sohva] def prepare(req: HttpRequest): HttpRequest

  protected[sohva] def rawHttp(req: HttpRequest): Future[HttpResponse] =
    http.singleRequest(prepare(req))

  protected[sohva] def http(req: HttpRequest): Future[JsValue] =
    rawHttp(req).flatMap(handleCouchResponse)

  protected[sohva] def optHttp(req: HttpRequest): Future[Option[JsValue]] =
    rawHttp(req).flatMap(handleOptionalCouchResponse)

  protected[sohva] def connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
    if (ssl)
      Http().outgoingConnectionHttps(host, port = port)
    else
      Http().outgoingConnection(host, port = port)

  private def handleCouchResponse(response: HttpResponse): Future[JsValue] =
    Unmarshal(response.entity.withContentType(ContentTypes.`application/json`)).to[JsValue].flatMap { json =>
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
    handleCouchResponse(response).map(Some(_)).recoverWith {
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

// the different object that may be returned by the couchdb server

sealed trait DbResult

final case class OkResult(ok: Boolean, id: Option[String], rev: Option[String]) extends DbResult

final case class ErrorResult(id: Option[String], error: String, reason: String) extends DbResult

final case class CouchInfo(couchdb: String, version: String)

private[sohva] final case class Uuids(uuids: List[String])

final case class Membership(all_nodes: Vector[String], cluster_nodes: Vector[String])

final case class DbUpdate(db_name: String, seq: JsValue, `type`: String)
