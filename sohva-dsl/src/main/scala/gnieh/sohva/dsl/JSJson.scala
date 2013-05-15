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

import net.liftweb.json._

/** `JSON2` object access
 *
 *  @author Lucas Satabin
 */
trait JSJson extends JSProxyBase {

  /** the special `undefined` javascript value */
  val undefined: Rep[Nothing]
  /** the JSON2 object */
  val JSON: Rep[JSON]

  trait JSON {
    /** Returns the string representation of the javascript object */
    def stringify(value: Rep[Any], replacer: Rep[Any] = undefined, space: Rep[Any] = undefined): Rep[String]
    /** Parses a Json object to a javascript value */
    def parse(data: Rep[String], reviver: Rep[(String, Any) => Any] = undefined): Rep[Any]
  }

  implicit def repToJSON(x: Rep[JSON]): JSON =
    repProxy[JSON](x)

}

trait JSJsonExp extends JSJson with JSProxyExp {

  object undefined extends Rep[Nothing]

  case object JSON extends Rep[JSON]

}

trait JSGenJson extends JSGenProxy with QuoteGen {
  val IR: JSJsonExp
  import IR._

  override def quote(x: Rep[Any]) = x match {
    case JSON => "JSON"
    case _    => super.quote(x)
  }
}
