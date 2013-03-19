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

import conflict._

import net.liftweb.json._

/** This strategy applies the principle of patch commutation as done by
 *  CVS such as darcs or git (for rebasing). It allows to linearize the patches
 *  to apply to a same base document. (see an explanation on how it works here:
 *  http://en.wikibooks.org/wiki/Understanding_Darcs/Patch_theory)
 *  In the case both patches do not commute, conflicting changes from the database
 *  document are discarded. Client document has higher priority.
 *
 *  In our case, the base document is taken as common ancestor and we create patches
 *  that models these changes:
 *   - let's name document `base`, `db` and `current` respectively the base document,
 *     the last document from the database and the document one wants to save
 *   - let `BD` be the name of the patch from `base` to `db` and `BC` be the patch from
 *     `base` to `current`.
 *   - compute `CD` the patch that makes the same modifications as `BD` but in the context of
 *     document `current`
 *   - apply this patch to `current`
 *
 *  @author Lucas Satabin
 */
object ClientCommuteStrategy extends Strategy {

  def apply(baseDoc: Option[JValue], dbDoc: Option[JValue], currentDoc: JValue) = {
    // non present base document is an empty object
    val baseJson = baseDoc.getOrElse(JObject(Nil))
    // non present document from database (document deleted inbetween) is an empty object
    val dbJson = dbDoc.getOrElse(JObject(Nil))

    // compute current2base, the inverse of base2current
    val current2base = JsonDiff.diff(currentDoc, baseJson)

    // compute base2db
    val base2db = JsonDiff.diff(baseJson, dbJson)

    // compute commutation base2db <-> current2base
    val (current2db, db2base) = commute(current2base, base2db)

    // finally apply db2current to dbJson
    current2db(currentDoc)
  }

  /** Returns the patches that are commutations of `thisPatch` and `thatPatch`.
   *  If returned patches are `(thatPatch1, thisPatch1)`, we have
   *  ```
   *  (thisPatch andThen thatPatch)(doc) == (thatPatch1 andThen thisPatch1)(doc)
   *  ```
   */
  def commute(thisPatch: Patch, thatPatch: Patch): (Patch, Patch) = {

    @tailrec
    def loop(thisOps: List[Operation],
             thatOps: List[Operation],
             thisAcc: List[Operation]): (List[Operation], List[Operation]) = thisOps match {
      case thisOp :: tail =>
        modifies(thatOps, thisOp.path) match {
          case Some(thatOp) =>
            // another operation modifies the same pointer or on of its parent
            // in that patch
            loop(tail, thatOps, thisAcc)
          case None =>
            // add this operation and go further
            loop(tail, thatOps, thisOp :: thisAcc)
        }
      case Nil =>
        (thatOps, thisAcc.reverse)
    }
    val (thatOps1, thisOps1) = loop(thisPatch.ops, thatPatch.ops, Nil)
    (Patch(thatOps1), Patch(thisOps1))
  }

  /** Find the first operation that modifies the given pointer or one of its parent */
  def modifies(ops: List[Operation], p: Pointer): Option[Operation] =
    ops.find(op => pointerString(p).startsWith(pointerString(op.path)))

}
