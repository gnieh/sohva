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
package control

import gnieh.sohva.async.{
  Design => ADesign
}

import scala.util.Try

/** A design gives access to the different views.
 *  Use this class to get or create new views.
 *
 *  @author Lucas Satabin
 */
class Design(wrapped: ADesign) extends gnieh.sohva.Design[Try] {

  val name = wrapped.name

  val language = wrapped.language

  @inline
  def getDesignDocument: Try[Option[DesignDoc]] =
    synced(wrapped.getDesignDocument)

  @inline
  def delete: Try[Boolean] =
    synced(wrapped.delete)

  @inline
  def saveView(viewName: String,
    mapFun: String,
    reduceFun: Option[String] = None): Try[Unit] =
    synced(wrapped.saveView(viewName, mapFun, reduceFun))

  @inline
  def saveView(viewName: String, view: ViewDoc): Try[Unit] =
    synced(wrapped.saveView(viewName, view))

  @inline
  def deleteView(viewName: String): Try[Unit] =
    synced(wrapped.deleteView(viewName))

  def view(viewName: String): View =
    new View(wrapped.view(viewName))

  @inline
  def saveValidateFunction(validateFun: String): Try[Unit] =
    synced(wrapped.saveValidateFunction(validateFun))

  @inline
  def deleteValidateFunction: Try[Unit] =
    synced(wrapped.deleteValidateFunction)

  @inline
  def saveFilter(name: String, filterFun: String): Try[Unit] =
    synced(wrapped.saveFilter(name, filterFun))

  @inline
  def deleteFilter(name: String): Try[Unit] =
    synced(wrapped.deleteFilter(name))

}
