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
package strategy

import spray.json._

/** This strategy is the anti-[[BarneyStinsonStrategy]] by definition as it applies
 *  a simple rule: ''Old is always better''.
 *  Whenever a conflict occurs when trying to save a document in the database,
 *  the oldest document (the one from the database) is kept.
 *
 *  @author Lucas Satabin
 *
 */
object TedMosbyStrategy extends Strategy {

  def apply(baseDoc: Option[JsValue], lastDoc: Option[JsValue], currentDoc: JsValue) =
    Some(JsNull)

}
