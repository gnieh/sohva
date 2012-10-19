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

import net.liftweb.json._

import java.security.MessageDigest

import scala.util.Random

object UserSerializer {

  /** The version of the couchdb server, used to determine the format of user documents. */
  var couchVersion = "1.2"

  private def bytes2string(bytes: Array[Byte]) =
    bytes.foldLeft(new StringBuilder) {
      (res, byte) =>
        res.append(Integer.toHexString(byte & 0xff))
    }.toString

  private[sohva] def passwordSha(password: String) = {

    // compute the password hash
    val md = MessageDigest.getInstance("SHA-1")

    // the password string is concatenated with the generated salt
    // and the result is hashed using SHA-1
    val saltArray = new Array[Byte](16)
    Random.nextBytes(saltArray)
    val salt = bytes2string(saltArray)

    (salt, bytes2string(md.digest((password + salt).getBytes("UTF-8"))))
  }

}

private[sohva] class UserSerializer extends Serializer[CouchUser] {

  import UserSerializer._

  val userClass = classOf[CouchUser]

  def deserialize(implicit format: Formats) = {
    case (TypeInfo(clazz, _), json) if userClass.isAssignableFrom(clazz) =>
      json.extractOpt[CouchUser](standardFormats, manifest[CouchUser]) match {
        case Some(user) => user
        case None => throw new MappingException("Can't convert " + json + " to a couch user")
      }
  }

  def serialize(implicit format: Formats) = {
    case user: CouchUser =>
      // since version 1.2 password is managed automatically by couchdb
      // so we do not have to generate the `password_sha' and `salt' fields

      val fields =
        if (couchVersion >= "1.2")
          List(
            Some(JField("_id", JString(user._id))),
            user._rev.map(rev => JField("_rev", JString(rev))),
            Some(JField("name", JString(user.name))),
            user.password.map(pwd => JField("password", JString(pwd))),
            Some(JField("roles", JArray(user.roles.map(JString)))),
            Some(JField("type", JString("user"))))
        else {
          val (salt, password_sha) = user.password match {
            case Some(pwd) =>
              val (s, p) = passwordSha(pwd)
              (Some(s), Some(p))
            case None => (None, None)
          }
          List(
            Some(JField("_id", JString(user._id))),
            user._rev.map(rev => JField("_rev", JString(rev))),
            Some(JField("name", JString(user.name))),
            password_sha.map(pwd => JField("password_sha", JString(pwd))),
            salt.map(s => JField("salt", JString(s))),
            Some(JField("roles", JArray(user.roles.map(JString)))),
            Some(JField("type", JString("user"))))
        }
      JObject(fields.flatten)
  }
}