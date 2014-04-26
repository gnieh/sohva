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
package sync

import gnieh.sohva.async.{
  Design => ADesign
}

import scala.concurrent._
import duration._

/** A design gives access to the different views.
 *  Use this class to get or create new views.
 *
 *  @author Lucas Satabin
 */
class Design(wrapped: ADesign) extends gnieh.sohva.Design[Identity] {

  val name = wrapped.name

  val language = wrapped.language

  @inline
  def getDesignDocument: Option[DesignDoc] =
    synced(wrapped.getDesignDocument)

  @inline
  def delete: Boolean =
    synced(wrapped.delete)

  @inline
  def saveView(viewName: String,
    mapFun: String,
    reduceFun: Option[String] = None): Unit =
    synced(wrapped.saveView(viewName, mapFun, reduceFun))

  @inline
  def saveView(viewName: String, view: ViewDoc): Unit =
    synced(wrapped.saveView(viewName, view))

  @inline
  def deleteView(viewName: String): Unit =
    synced(wrapped.deleteView(viewName))

  def view(viewName: String): View =
    new View(wrapped.view(viewName))

  @inline
  def saveValidateFunction(validateFun: String): Unit =
    synced(wrapped.saveValidateFunction(validateFun))

  @inline
  def deleteValidateFunction: Unit =
    synced(wrapped.deleteValidateFunction)

  @inline
  def saveFilter(name: String, filterFun: String): Unit =
    synced(wrapped.saveFilter(name, filterFun))

  @inline
  def deleteFilter(name: String): Unit =
    synced(wrapped.deleteFilter(name))

}
