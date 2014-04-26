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

import scala.concurrent.Future

import java.util.Date

import net.liftweb.json._

import spray.client.pipelining._

/** The users database, exposing the interface for managing couchdb users.
 *
 *  @author Lucas Satabin
 */
class Users(couch: CouchDB) extends gnieh.sohva.Users[Future] {

  import couch._

  var dbName: String = "_users"

  private def userDb = couch.database(dbName)

  def add(name: String,
    password: String,
    roles: List[String] = Nil): Future[Boolean] = {

    val user = CouchUser(name, password, roles)

    for (res <- http(Put(uri / dbName / user._id, serializer.toJson(user))))
      yield ok(res)

  }

  def delete(name: String): Future[Boolean] =
    database(dbName).deleteDoc("org.couchdb.user:" + name)

  def generateResetToken(name: String, until: Date): Future[String] =
    for {
      tok <- _uuid
      user <- userDb.getDocById[JObject]("org.couchdb.user:" + name)
      token <- generate(name, user, tok, until)
    } yield token

  private[this] def generate(name: String, user: Option[JObject], token: String, until: Date) =
    user match {
      case Some(user) =>
        // enrich the user document with password reset information
        val (token_salt, token_sha) = passwordSha(token)
        val doc = user ++
          JField("reset_token_sha", JString(token_sha)) ++
          JField("reset_token_salt", JString(token_salt)) ++
          JField("reset_validity", serializer.toJson(until))
        // save back the enriched user document
        for (_ <- userDb.saveRawDoc(doc))
          yield token
      case None =>
        Future.failed(new SohvaException("Cannot generate password reset token for unknown user " + name))
    }

  def resetPassword(name: String, token: String, password: String): Future[Boolean] =
    for {
      user <- userDb.getDocById[JObject]("org.couchdb.user:" + name)
      ok <- reset(user, token, password)
    } yield ok

  private[this] def reset(user: Option[JObject], token: String, password: String) =
    user match {
      case Some(user) =>
        // check the token with the one in the database (if still valid)
        user match {
          case PasswordResetUser(_id, _rev, name, roles, savedToken, savedSalt, validity) =>
            val saltedToken = hash(token + savedSalt)
            if (new Date().before(validity) && savedToken == saltedToken) {
              // save the user with the new password
              val newUser = new CouchUser(name, password, roles = roles).withRev(_rev)
              couch.http(Put(uri / dbName / _id, serializer.toJson(newUser))).map(ok _)
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
    def unapply(obj: JObject): Option[(String, Option[String], String, List[String], String, String, Date)] =
      for {
        JString(_id) <- (obj \ "_id").toOpt
        JString(name) <- (obj \ "name").toOpt
        roles <- (obj \ "roles").toOpt.flatMap(serializer.fromJsonOpt[List[String]])
        JString(reset_token_sha) <- (obj \ "reset_token_sha").toOpt
        JString(reset_token_salt) <- (obj \ "reset_token_salt").toOpt
        reset_validity <- (obj \ "reset_validity").toOpt.flatMap(serializer.fromJsonOpt[Date])
      } yield (_id, (obj \ "_rev").toOpt.collect { case JString(_rev) => _rev }, name, roles, reset_token_sha, reset_token_salt, reset_validity)

  }

}
