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

/** The users database, exposing the interface for managing couchdb users.
 *
 *  @author Lucas Satabin
 */
class Users(couch: CouchDB) extends gnieh.sohva.Users {

  type Result[T] = Future[Either[(Int, Option[ErrorResult]), T]]

  import couch._

  var dbName: String = "_users"

  private def userDb = couch.database(dbName)

  def add(name: String,
          password: String,
          roles: List[String] = Nil): Result[Boolean] = {

    val user = CouchUser(name, password, roles)

    for(res <- http((request / dbName / user._id << serializer.toJson(user)).PUT).right)
      yield ok(res)

  }

  def delete(name: String): Result[Boolean] =
    database(dbName).deleteDoc("org.couchdb.user:" + name)

  def generateResetToken(name: String, until: Date): Result[Option[String]] =
    for {
      tok <- _uuid.right
      user <- userDb.getDocById[PasswordResetUser]("org.couchdb.user:" + name).right
      token <- generate(user, tok, until)
    } yield token

  private[this] def generate(user: Option[PasswordResetUser], token: String, until: Date) =
    user match {
      case Some(user) =>
        // enrich the user document with password reset information
        val (token_salt, token_sha) = passwordSha(token)
        val u =
          user.copy(
            reset_token_sha = Some(token_sha),
            reset_token_salt = Some(token_salt),
            reset_validity = Some(until))
        // save back the enriched user document
        for (user <- userDb.saveDoc(u).right)
          yield user.map(_ => token)
      case None =>
        Future.successful(Right(None))
    }

  def resetPassword(name: String, token: String, password: String): Result[Boolean] =
    for {
      user <- userDb.getDocById[PasswordResetUser]("org.couchdb.user:" + name).right
      ok <- reset(user, token, password)
    } yield ok

  private[this] def reset(user: Option[PasswordResetUser], token: String, password: String) =
    user match {
      case Some(user) =>
        // check the token with the one in the database (if still valid)
        (user.reset_token_sha, user.reset_token_salt, user.reset_validity) match {
          case (Some(savedToken), Some(savedSalt), Some(validity)) =>
            val saltedToken = hash(token + savedSalt)
            if(new Date().before(validity) && savedToken == saltedToken) {
              // save the user with the new password
              val newUser = new CouchUser(user.name, password, roles = user.roles, _rev = user._rev)
             couch.http((request / dbName / user._id << serializer.toJson(newUser)).PUT).right.map(ok _)
            } else {
              Future.successful(Right(false))
            }
          case _ =>
            Future.successful(Right(false))
        }
      case None =>
        Future.successful(Right(false))
    }

}
