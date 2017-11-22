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

import gnieh.diffson._
import sprayJson._

import spray.json._

/** This strategy applies a simple structural merge algorithm between variation from
 *  a base document to the last one in the database and from the base to the current
 *  revision of the document to merge
 *
 *  In our case, the base document is taken as common ancestor and we create patches
 *  that models these changes:
 *   - let's name document `base`, `db` and `current` respectively the base document,
 *     the last document from the database and the document one wants to save
 *   - let `BD` be the name of the patch from `base` to `db` and `BC` be the patch from
 *     `base` to `current`.
 *   - compute `DC` the patch that makes the same modifications as `BC` but in the context of
 *     document `db`
 *   - apply this patch to `db`
 *
 *  The rules to apply when merging paths are the following:
 *  <table style="border-collapse: collapse; border: 1px solid black">
 *  <tr>
 *    <th style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: center">`BD`</th>
 *    <th style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: center">`BC`</th>
 *    <th style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: center">`DC`</th>
 *  </tr>
 *  <tr>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Not Changed</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Not Changed</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Not Changed</td>
 *  </tr>
 *  <tr>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Not Changed</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Changed</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Changed</td>
 *  </tr>
 *  <tr>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Not Changed</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Deleted</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Deleted</td>
 *  </tr>
 *  <tr>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Changed</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Not Changed</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Changed</td>
 *  </tr>
 *  <tr>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Changed</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Changed</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Changed (`BC`)</td>
 *  </tr>
 *  <tr>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Changed</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Deleted</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Deleted</td>
 *  </tr>
 *  <tr>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Deleted</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Not Changed</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Deleted</td>
 *  </tr>
 *  <tr>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Deleted</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Changed</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Deleted</td>
 *  </tr>
 *  <tr>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Deleted</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Deleted</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Deleted</td>
 *  </tr>
 *  <tr>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Added</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Not Changed</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Added</td>
 *  </tr>
 *  <tr>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Added</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Added</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Added (`BC`)</td>
 *  </tr>
 *  <tr>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Not Changed</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Added</td>
 *    <td style="border-width: 1px; padding: 3px; border: 1px solid black; text-align: right">Added</td>
 *  </tr>
 *  </table>
 *
 *  As a special case, if the document was deleted inbetween, the current document is saved "as is" in the database (without revision)
 *
 *  @author Lucas Satabin
 */
object StructuralMergeStrategy extends Strategy {

  def apply(baseDoc: Option[JsValue], dbDoc: Option[JsValue], currentJson: JsValue) = dbDoc match {
    case Some(dbJson) =>
      // if the document was created, the base document is an empty object
      val baseJson = baseDoc.getOrElse(JsObject())

      // compute base2db
      val base2db = JsonDiff.diff(baseJson, dbJson, false)

      // println("BD: " + base2db)

      // compute base2current
      val base2current = JsonDiff.diff(baseJson, currentJson, false)

      // println("BC: " + base2current)

      @tailrec
      def loop(ops1: List[Operation], ops2: List[Operation], acc: List[Operation]): List[Operation] = ops1 match {
        case (op @ Replace(path, _, _)) :: tail1 =>
          if (modifiesParent(ops2, path)) {
            // the path or one of its parent is modified or deleted in the second patch
            loop(tail1, ops2, acc)
          } else {
            loop(tail1, ops2, op :: acc)
          }
        case (op @ Remove(path, _)) :: tail1 =>
          // drop all modifications that applies to `path` or one of its children from the second patch
          // if the path is an array pointer, also shifts subsequent elements in the array by -1
          loop(
            tail1,
            shift(ops2 filterNot (modifiesChild(_, path)), path, -1),
            op :: acc)
        case (op @ Add(path, _)) :: tail1 =>
          // it was added, if it is also added in the second one, ignore this
          if (addedIn(ops2, path)) {
            loop(tail1, ops2, acc)
          } else {
            // if the path is an array pointer, also shifts subsequent elements in the array by +1
            loop(
              tail1,
              shift(ops2, path, 1),
              op :: acc)
          }
        case op :: tail1 =>
          throw new Exception("Unsupported patch operation " + op)
        case Nil =>
          acc reverse_::: ops2
      }

      // compute db2current by merging operations
      val db2current = JsonPatch(loop(base2db.ops, base2current.ops, Nil))

      // println("DC: " + db2current)

      // apply the db2current to the base Json
      Some(db2current(baseJson))

    case None =>
      // the document was deleted, simply remove the revision from the current document
      Some(JsObject(currentJson.asJsObject.fields.filter {
        case ("_rev", _) => false
        case _           => true
      }))
  }

  def shift(ops: List[Operation], p: Pointer, value: Int): List[Operation] = p match {
    case ArrayIdx(parent, idx) =>
      ops map {
        case Replace(ArrayIdx(parent2, idx2), v, old) if parent2 == parent && idx2 > idx =>
          Replace(parent2 / (idx2 + value), v, old)
        case Remove(ArrayIdx(parent2, idx2), old) if parent2 == parent && idx2 > idx =>
          Remove(parent2 / (idx2 + value), old)
        case Add(ArrayIdx(parent2, idx2), v) if parent2 == parent && idx2 > idx =>
          Add(parent2 / (idx2 + value), v)
        case op => op
      }
    case _ =>
      // not an array index, ops are returned unchanged
      ops
  }

  object ArrayIdx {
    private[this] val int = "(0|[1-9][0-9]*)".r
    def unapply(p: Pointer): Option[(Pointer, Int)] =
      p match {
        case Pointer(elems @ _*) if elems.size > 0 =>
          // all but last
          val parent = elems.dropRight(1)
          elems.last match {
            case int(idx) => Some(Pointer(parent: _*) -> idx.toInt)
            case _        => None
          }
        case _ =>
          None
      }
  }

  /** Indicates whether the pointer is added in the list of operations */
  def addedIn(ops: List[Operation], p: Pointer): Boolean =
    ops exists {
      case Add(path, _) => path == p
      case _            => false
    }

  /** Indicates whether an operation exists in the list that modifies the pointer or one of its parents */
  def modifiesParent(ops: List[Operation], p: Pointer): Boolean =
    ops.exists(op => p.toString.startsWith(op.path.toString))

  /** Indicates whether the operation modifies the pointer or one of its children */
  def modifiesChild(op: Operation, p: Pointer): Boolean =
    op.path.toString.startsWith(p.toString)

}

