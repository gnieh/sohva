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

import dispatch._
import Defaults._

import java.util.Date

import net.liftweb.json._

/** The users database, exposing the interface for managing couchdb users.
 *
 *  @author Lucas Satabin
 */
class Users(couch: CouchDB) extends gnieh.sohva.Users[AsyncResult] {

  import couch._

  var dbName: String = "_users"

  private def userDb = couch.database(dbName)

  def add(name: String,
          password: String,
          roles: List[String] = Nil): AsyncResult[Boolean] = {

    val user = CouchUser(name, password, roles)

    for(res <- http((request / dbName / user._id << serializer.toJson(user)).PUT).right)
      yield ok(res)

  }

  def delete(name: String): AsyncResult[Boolean] =
    database(dbName).deleteDoc("org.couchdb.user:" + name)

  def generateResetToken(name: String, until: Date): AsyncResult[Option[String]] =
    for {
      tok <- _uuid.right
      user <- userDb.getDocById[JObject]("org.couchdb.user:" + name).right
      token <- generate(user, tok, until)
    } yield token

  private[this] def generate(user: Option[JObject], token: String, until: Date) =
    user match {
      case Some(user) =>
        // enrich the user document with password reset information
        val (token_salt, token_sha) = passwordSha(token)
        val u = user ++
          JField("reset_token_sha", JString(token_sha)) ++
          JField("reset_token_salt", JString(token_salt)) ++
          JField("reset_validity", JsonParser.parse(serializer.toJson(until)))
        val doc = pretty(render(u))
        // save back the enriched user document
        for (user <- userDb.saveRawDoc(doc).right)
          yield user.map(_ => token)
      case None =>
        Future.successful(Right(None))
    }

  def resetPassword(name: String, token: String, password: String): AsyncResult[Boolean] =
    for {
      user <- userDb.getDocById[JObject]("org.couchdb.user:" + name).right
      ok <- reset(user, token, password)
    } yield ok

  private[this] def reset(user: Option[JObject], token: String, password: String) =
    user match {
      case Some(user) =>
        // check the token with the one in the database (if still valid)
        user match {
          case PasswordResetUser(_id, _rev, name, roles, savedToken, savedSalt, validity) =>
            val saltedToken = hash(token + savedSalt)
            if(new Date().before(validity) && savedToken == saltedToken) {
              // save the user with the new password
              val newUser = new CouchUser(name, password, roles = roles).withRev(_rev)
              couch.http((request / dbName / _id << serializer.toJson(newUser)).PUT).right.map(ok _)
            } else {
              Future.successful(Right(false))
            }
          case _ =>
            Future.successful(Right(false))
        }
      case None =>
        Future.successful(Right(false))
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
