/*
* This file is part of the sohva project.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*couch.http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package gnieh.sohva

import strategy._

import dispatch._
import Defaults._

import com.ning.http.client.{
  RequestBuilder,
  Response
}

import java.security.MessageDigest

import scala.util.Random

import net.liftweb.json._

/** A CouchDB instance.
 *  Allows users to access the different databases and information.
 *  This is the key class to start with when one wants to work with couchdb.
 *  Through this one you will get access to the databases.
 *
 *  @author Lucas Satabin
 *
 */
abstract class CouchDB {

  self =>

  /** The couchdb instance host name. */
  val host: String

  /** The couchdb instance port. */
  val port: Int

  /** The couchdb instance version. */
  val version: String

  /** The Json (de)serializer */
  val serializer: JsonSerializer

  /** Returns the database on the given couch instance. */
  def database(name: String, credit: Int = 0, strategy: Strategy = BarneyStinsonStrategy): Database =
    new Database(name, this, credit, strategy)

  /** Returns the names of all databases in this couch instance. */
  def _all_dbs: Result[List[String]] =
    for(dbs <- http(request / "_all_dbs").right)
      yield asStringList(dbs)

  /** Returns the requested number of UUIDS (by default 1). */
  def _uuids(count: Int = 1): Result[List[String]] =
    for(uuids <- http(request / "_uuids" <<? Map("count" -> count.toString)).right)
      yield asUuidsList(uuids)

  /** Indicates whether this couchdb instance contains the given database */
  def contains(dbName: String): Result[Boolean] =
    for(dbs <- _all_dbs.right)
      yield dbs.contains(dbName)

  // user management section

  /** Exposes the interface for managing couchdb users. */
  object users extends Users(this)


  // helper methods

  private[sohva] def request: RequestBuilder

  private[sohva] def _http: Http

  private[sohva] def bytes2string(bytes: Array[Byte]) =
    bytes.foldLeft(new StringBuilder) {
      (res, byte) =>
        res.append(Integer.toHexString(byte & 0xff))
    }.toString

  private[sohva] def hash(s: String) = {
    val md = MessageDigest.getInstance("SHA-1")
    bytes2string(md.digest(s.getBytes("UTF-8")))
  }

  private[sohva] def passwordSha(password: String) = {

    // compute the password hash
    // the password string is concatenated with the generated salt
    // and the result is hashed using SHA-1
    val saltArray = new Array[Byte](16)
    Random.nextBytes(saltArray)
    val salt = bytes2string(saltArray)

    (salt, hash(password + salt))
  }

  private[sohva] def http(request: RequestBuilder): Result[String] =
    _http(request > handleCouchResponse _)

  private[sohva] def optHttp(request: RequestBuilder): Result[Option[String]] =
    _http(request > handleOptionalCouchResponse _)

  private def handleCouchResponse(response: Response): Either[(Int, Option[ErrorResult]), String] = {
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

  private def handleOptionalCouchResponse(response: Response): Either[(Int, Option[ErrorResult]), Option[String]] =
    handleCouchResponse(response) match {
      case Right(v) => Right(Some(v))
      case Left((404, _)) => Right(None)
      case Left(err) => Left(err)
    }

  private[sohva] def ok(json: String) =
    serializer.fromJson[OkResult](json).ok

  private def asStringList(json: String) =
    serializer.fromJson[List[String]](json)

  private def asUuidsList(json: String) =
    serializer.fromJson[Uuids](json).uuids

}

// the different object that may be returned by the couchdb server

final case class OkResult(ok: Boolean, id: Option[String], rev: Option[String])

final case class ErrorResult(error: String, reason: String)

private[sohva] final case class Uuids(uuids: List[String])
