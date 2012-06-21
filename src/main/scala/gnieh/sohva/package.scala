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

import net.liftweb.json._

import java.text.SimpleDateFormat

import org.apache.http.client.params.{ ClientPNames, CookiePolicy }
import org.apache.http.params.CoreProtocolPNames

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

  private[sohva] val standardFormats = new DefaultFormats {
    override def dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS")
  }

  implicit val formats = standardFormats

  private lazy val sohvaHttp =
    new Http {
      override def make_client = {
        val client = super.make_client
        // does not automatically manage cookies
        client.getParams.setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.IGNORE_COOKIES)
        client
      }
    }

  /** Executes the given request and catch the exceptions given as optional parameter. */
  def http[T](handler: Handler[T])(exc: PartialFunction[(Int, Option[ErrorResult]), T] = null): T =
    try {
      sohvaHttp(handler)
    } catch {
      case StatusCode(code, contents) =>

        val error = try {
          parse(contents).extractOpt[ErrorResult]
        } catch {
          case e => None
        }

        if (exc != null && exc.isDefinedAt(code, error)) {
          exc(code, error)
        } else {
          error match {
            case Some(ErrorResult(error, reason)) =>
              throw CouchException(code, error, reason)
            case None =>
              throw CouchException(code, contents, null)
          }
        }
    }

}
