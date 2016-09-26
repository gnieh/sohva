/*
* This file is part of the sohva project.
* Copyright (c) 2016 Lucas Satabin
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

import spray.json._

/** The [mango query server](http://docs.couchdb.org/en/2.0.0/api/database/find.html) was introduced in CouchDB 2.0.
 *  It allows for querying documents in a database with a declarative syntax and is easier to use that the classic CouchDB views.
 */
package object mango {

  type UseIndex = Either[String, (String, String)]

  object SingleMap {

    def unapply(map: Map[String, JsValue]): Option[(String, JsValue)] =
      map.headOption

  }

}
