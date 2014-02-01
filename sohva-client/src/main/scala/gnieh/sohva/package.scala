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

import dispatch._
import Defaults._

import scala.concurrent.Future

import java.security.MessageDigest

import scala.util.Random

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

  /** A couchdb document must have an `_id` field and an optional `_rev` field.
   */
  type Doc = {
    val _id: String
    val _rev: Option[String]
  }

  @deprecated(message = "Use type CookieSession instead", since = "0.5")
  type CouchSession[Result[_]] = CookieSession[Result]

  implicit def doc2idrev(d: Doc): IdRev =
    new IdRev {
      val _id = d._id
      _rev = d._rev
    }

  implicit def map2idrev(m: Map[String, Any]): IdRev =
    new IdRev {
      val _id = m.get("_id").collect { case s: String => s }.getOrElse("")
      _rev = m.get("_rev").collect { case s: String => s }
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

}
