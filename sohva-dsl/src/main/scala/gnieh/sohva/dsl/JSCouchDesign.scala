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
package dsl

import scala.js._
import scala.virtualization.lms.common._

/** A couchDB design documents consists of several parts:
 *   - the (possibly empty) list of views
 *      - a dictionary of views that can be queried
 *      - an optional special `lib` view containing common code
 *   - an optional validation function
 *   - the (possibly empty) list of `show` functions
 *   - the (possibly empty) list of `list` functions
 *   - the (possibly empty) list of `filter` functions
 *
 *  @author Lucas Satabin
 */
trait JSCouchDesign extends JSCouch with JSFunctions {

  val views: Map[String, JSCouchView[_, _]] = Map()

  val views_lib: Map[String, Rep[Any]] = Map()

  val validate_doc_update: Rep[((Doc, Doc, UserCtx)) => Boolean]

}
