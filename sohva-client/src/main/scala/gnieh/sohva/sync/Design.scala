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
package gnieh.sohva.sync

import gnieh.sohva.{
  Design => ADesign,
  DesignDoc
}

/** A design gives access to the different views.
 *  Use this class to get or create new views.
 *
 *  @author Lucas Satabin
 */
case class Design(wrapped: ADesign) {

  val name = wrapped.name

  val language = wrapped.language

  /** Returns the design document from the couchdb instance.
   *  Returns `None` if the design document does not exist.
   */
  @inline
  def getDesignDocument: Option[DesignDoc] =
    synced(wrapped.getDesignDocument)

  /** Deletes this design document from the couchdb instance */
   @inline
  def delete: Boolean =
    synced(wrapped.delete)

  /** Creates or updates the view in this design
   *  with the given name, map function and reduce function.
   *  If the design does not exist yet, it is created.
   */
  @inline
  def saveView(viewName: String,
               mapFun: String,
               reduceFun: Option[String] = None): Boolean =
    synced(wrapped.saveView(viewName, mapFun, reduceFun))

  /** Deletes the view with the given name from the design */
  @inline
  def deleteView(viewName: String): Boolean =
    synced(wrapped.deleteView(viewName))

  /** Returns the (typed) view in this design document.
   *  The different types are:
   *  - Key: type of the key for this view
   *  - Value: Type of the value returned in the result
   *  - Doc: Type of the full document in the case where the view is queried with `include_docs` set to `true`
   */
  def view[Key: Manifest, Value: Manifest, Doc: Manifest](viewName: String): View[Key, Value, Doc] =
    View[Key, Value, Doc](wrapped.view(viewName))

  /** Creates or updates the document validation function.
   *  If the design does not exist yet, it is created.
   */
  @inline
  def saveValidateFunction(validateFun: String): Boolean =
    synced(wrapped.saveValidateFunction(validateFun))

  /** Deletes the document validation function from the design */
  @inline
  def deleteValidateFunction: Boolean =
    synced(wrapped.deleteValidateFunction)

  /** Creates or updates a filter function.
   *  If the design does not exist yet, it is created.
   */
  @inline
  def saveFilter(name: String, filterFun: String): Boolean =
    synced(wrapped.saveFilter(name, filterFun))

  /** Deletes the filter identified by its name from the design document */
   @inline
  def deleteFilter(name: String): Boolean =
    synced(wrapped.deleteFilter(name))

}
