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

import spray.httpx.unmarshalling.Unmarshaller

/** A design gives access to the different views.
 *  Use this class to get or create new views.
 *
 *  @author Lucas Satabin
 */
class Design(val wrapped: ADesign) extends gnieh.sohva.Design[Try] {

  val name = wrapped.name

  val language = wrapped.language

  @inline
  def exists: Try[Boolean] = synced(wrapped.exists)

  @inline
  def create: Try[DesignDoc] = synced(wrapped.create)

  @inline
  def getDesignDocument: Try[Option[DesignDoc]] =
    synced(wrapped.getDesignDocument)

  @inline
  def delete: Try[Boolean] =
    synced(wrapped.delete)

  @inline
  def saveShow(showName: String, show: String): Try[Unit] =
    synced(wrapped.saveShow(showName, show))

  @inline
  def deleteShow(showName: String): Try[Unit] =
    synced(wrapped.deleteShow(showName))

  @inline
  def show(showName: String): Show =
    new Show(wrapped.show(showName))

  @inline
  def saveList(listName: String, list: String): Try[Unit] =
    synced(wrapped.saveList(listName, list))

  @inline
  def deleteList(listName: String): Try[Unit] =
    synced(wrapped.deleteList(listName))

  @inline
  def list(listName: String): CList =
    new CList(wrapped.list(listName))

  @inline
  def saveUpdate(updateName: String, update: String): Try[Unit] =
    synced(wrapped.saveUpdate(updateName, update))

  @inline
  def deleteUpdate(updateName: String): Try[Unit] =
    synced(wrapped.deleteUpdate(updateName))

  @inline
  def update(updateName: String): Update =
    new Update(wrapped.update(updateName))

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

  @inline
  def saveRewriteRules(rules: List[RewriteRule]): Try[Unit] =
    synced(wrapped.saveRewriteRules(rules))

  @inline
  def getRewriteRules(): Try[List[RewriteRule]] =
    synced(wrapped.getRewriteRules())

  override def toString =
    wrapped.toString

}
