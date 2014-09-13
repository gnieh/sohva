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

import net.liftweb.json._

/** This strategy applies a simple rule: ''New is always better''
 *  Whenever a conflict occurs when trying to save a document in the database,
 *  the newest document (the one the client wants to store) is taken and overrides
 *  the previous revision.
 *
 *  @author Lucas Satabin
 *
 */
object BarneyStinsonStrategy extends Strategy {

  def apply(baseDoc: Option[JValue], lastDoc: Option[JValue], currentDoc: JValue) = lastDoc match {
    case Some(lastDoc) =>
      // simply replace the revision by the last one, and return the object unchanged (or add it if not present)
      currentDoc match {
        case JObject(fields) =>
          // remove the _rev field if present
          val clean = fields.filter {
            case JField("_rev", _) => false
            case _                 => true
          }
          Some(JObject(JField("_rev", lastDoc \ "_rev") :: clean))
        case _ =>
          Some(currentDoc)
      }
    case None =>
      // the document was deleted, drop the revision from the new document and retry
      Some(currentDoc.remove {
        case JField("_rev", _) => true
        case _                 => false
      })
  }
}
