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

import scala.annotation.tailrec

import liftjson.serializer.formats
import net.liftweb.json._

import conflict._

import dispatch._

/** A (almost) standard three-way merge strategy. The diff between base and last document
 *  and between base and current document is computed. If modifications conflict (modify same
 *  path) the one from the client document is taken. Modifications of the last document from
 *  the database are first applied (excluding conflicting ones) and then the ones from the
 *  client document. It is kind of the same as if the client had modified the conflicting version
 *  (modulo conflict resolution).
 *
 *  For example, if we have this diff between base revision and last from the database:
 *  ```
 *  [
 *    ...
 *    { "op": "replace", "path": "/f", "value": 3 }
 *    ...
 *  ]
 *  ```
 *  and a diff that contains this delete operation between base revision and client document:
 *  ```
 *  [
 *    ...
 *    { "op": "remove", "path": "/f" }
 *    ...
 *  ]
 *  ```
 *  The first modification is not present in the resulting patch, only the deletion appears.
 *
 * @author Lucas Satabin
 */
object ThreeWayMergeStrategy extends Strategy {

  def apply(baseDoc: Option[JValue], lastDoc: Option[JValue], currentDoc: JValue): JValue = {
    val baseJson = baseDoc.getOrElse(JObject(Nil))
    val lastJson = lastDoc.getOrElse(JObject(Nil))
    // compute the difference from base to current
    val currentDiff = JsonDiff.diff(baseJson, currentDoc)
    // compute the difference from base to last
    val lastDiff = JsonDiff.diff(baseJson, lastJson)
    // merge bot patches, if both modify/delete/add the same pointer,
    // take the one from the current document
    val newLastDiff = lastDiff.ops filter (op => !currentDiff.isModified(op.path))
    // apply the new patch to the base revision
    //val newBaseJson = newLastDiff(baseJson)
    // recompute the diff betwen new base document and current document
    //val newCurrentDiff = JsonDiff.diff(newBaseJson, currentDoc)
    // and finally apply it
    //newCurrentDiff(newBaseJson)
    currentDoc
  }
}
