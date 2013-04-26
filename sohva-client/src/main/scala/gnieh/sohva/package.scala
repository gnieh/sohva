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

  type OptIdDoc = {
    val _id: Option[String]
    val _rev: Option[String]
  }

  type Result[T] = Future[Either[(Int, Option[ErrorResult]), T]]

}
