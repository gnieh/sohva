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

import scala.util.Try

import java.util.Date

import spray.json._

import spray.client.pipelining._

/** The users database, exposing the interface for managing couchdb users.
 *
 *  @author Lucas Satabin
 */
class Users(couch: CouchDB) {

  import couch._

  import SohvaProtocol._

  var dbName: String = "_users"

  private def userDb = couch.database(dbName)

  /** Adds a new user with the given role list to the user database,
   *  and returns the new instance.
   */
  def add(name: String,
    password: String,
    roles: List[String] = Nil): Future[Boolean] = {

    val user = CouchUser(name, password, roles)

    for (res <- http(Put(uri / dbName / user._id, user.toJson)))
      yield ok(res)

  }

  /** Deletes the given user from the database. */
  def delete(name: String): Future[Boolean] =
    database(dbName).deleteDoc("org.couchdb.user:" + name)

  /** Generates a password reset token for the given user with the given validity and returns it */
  def generateResetToken(name: String, until: Date): Future[String] =
    for {
      tok <- _uuid
      user <- userDb.getDocById[JsObject]("org.couchdb.user:" + name)
      token <- generate(name, user, tok, until)
    } yield token

  private[this] def generate(name: String, user: Option[JsObject], token: String, until: Date) =
    user match {
      case Some(JsObject(fields)) =>
        // enrich the user document with password reset information
        val (token_salt, token_sha) = passwordSha(token)
        val doc = fields +
          ("reset_token_sha" -> JsString(token_sha)) +
          ("reset_token_salt" -> JsString(token_salt)) +
          ("reset_validity" -> until.toJson)
        // save back the enriched user document
        for (_ <- userDb.saveRawDoc(JsObject(doc)))
          yield token
      case None =>
        Future.failed(new SohvaException(f"Cannot generate password reset token for unknown user $name"))
    }

  /** Resets the user password to the given one if:
   *   - a password reset token exists in the database
   *   - the token is still valid
   *   - the saved token matches the one given as parameter
   */
  def resetPassword(name: String, token: String, password: String): Future[Boolean] =
    for {
      user <- userDb.getDocById[JsObject]("org.couchdb.user:" + name)
      ok <- reset(user, token, password)
    } yield ok

  private[this] def reset(user: Option[JsObject], token: String, password: String) =
    user match {
      case Some(user) =>
        // check the token with the one in the database (if still valid)
        user match {
          case PasswordResetUser(_id, _rev, name, roles, savedToken, savedSalt, validity) =>
            val saltedToken = hash(token + savedSalt)
            if (new Date().before(validity) && savedToken == saltedToken) {
              // save the user with the new password
              val newUser = new CouchUser(name, password, roles = roles).withRev(_rev)
              couch.http(Put(uri / dbName / _id, newUser.toJson)).map(ok _)
            } else {
              Future.successful(false)
            }
          case _ =>
            Future.successful(false)
        }
      case None =>
        Future.successful(false)
    }

  private[sohva] object PasswordResetUser {
    def unapply(obj: JsObject): Option[(String, Option[String], String, List[String], String, String, Date)] =
      for {
        JsString(_id) <- obj.fields.get("_id")
        JsString(name) <- obj.fields.get("name")
        roles <- obj.fields.get("roles").flatMap(r => Try(r.convertTo[List[String]]).toOption)
        JsString(reset_token_sha) <- obj.fields.get("reset_token_sha")
        JsString(reset_token_salt) <- obj.fields.get("reset_token_salt")
        reset_validity <- obj.fields.get("reset_validity").flatMap(v => Try(v.convertTo[Date]).toOption)
      } yield (_id, obj.fields.get("_rev").collect { case JsString(_rev) => _rev }, name, roles, reset_token_sha, reset_token_salt, reset_validity)

  }

  override def toString =
    userDb.toString

}
