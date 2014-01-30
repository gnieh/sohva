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
package async

import dispatch._
import Defaults._

/** A design gives access to the different views.
 *  Use this class to get or create new views.
 *
 *  @author Lucas Satabin
 */
class Design(val db: Database,
    val name: String,
    val language: String) extends gnieh.sohva.Design[AsyncResult] {

  import db.couch.serializer

  protected[sohva] def request = db.request / "_design" / name.trim

  def getDesignDocument: AsyncResult[Option[DesignDoc]] =
    for (design <- db.couch.optHttp(request).right)
      yield design.map(designDoc)

  def delete: AsyncResult[Boolean] =
    db.deleteDoc("_design/" + name.trim)

  def saveView(viewName: String,
    mapFun: String,
    reduceFun: Option[String] = None): AsyncResult[Boolean] =
    saveView(viewName, ViewDoc(mapFun, reduceFun))

  def saveView(viewName: String, view: ViewDoc): AsyncResult[Boolean] =
    for {
      design <- getDesignDocument.right
      doc <- db.saveDoc(newDoc(design, viewName, view)).right
    } yield doc.isDefined

  private[this] def newDoc(design: Option[DesignDoc], viewName: String, view: ViewDoc) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(views = design.views + (viewName -> view)).withRev(design._rev)
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(viewName -> view), None)
    }

  def deleteView(viewName: String): AsyncResult[Boolean] =
    for {
      design <- getDesignDocument.right
      res <- deleteView(design, viewName)
    } yield res

  private[this] def deleteView(design: Option[DesignDoc], viewName: String) =
    design match {
      case Some(design) =>
        db.saveDoc(design.copy(views = design.views - viewName)).right.map(_.isDefined)
      case None => Future.successful(Right(false))
    }

  def view[Key: Manifest, Value: Manifest, Doc: Manifest](viewName: String): View[Key, Value, Doc] =
    new View[Key, Value, Doc](this.name, db, viewName)

  def saveValidateFunction(validateFun: String): AsyncResult[Boolean] =
    for {
      design <- getDesignDocument.right
      res <- db.saveDoc(newDoc(design, validateFun)).right
    } yield res.isDefined

  private[this] def newDoc(design: Option[DesignDoc], validateFun: String) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(validate_doc_update = Some(validateFun))
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(), Some(validateFun))
    }

  def deleteValidateFunction: AsyncResult[Boolean] =
    for {
      design <- getDesignDocument.right
      res <- deleteValidateFunction(design)
    } yield res

  private[this] def deleteValidateFunction(design: Option[DesignDoc]) =
    design match {
      case Some(design) =>
        for (doc <- db.saveDoc(design.copy(validate_doc_update = None)).right)
          yield doc.isDefined
      case None => Future.successful(Right(false))
    }

  def saveFilter(name: String, filterFun: String): AsyncResult[Boolean] =
    for {
      design <- getDesignDocument.right
      res <- db.saveDoc(withFilterDoc(design, name, filterFun)).right
    } yield res.isDefined

  private[this] def withFilterDoc(design: Option[DesignDoc], filterName: String, filterFun: String) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(filters = design.filters.updated(filterName, filterFun))
      case None =>
        // the design does not exist yet
        DesignDoc("_design/" + name, language, Map(), None, filters = Map(filterName -> filterFun))
    }

  def deleteFilter(name: String): AsyncResult[Boolean] =
    for {
      design <- getDesignDocument.right
      res <- deleteFilter(design, name)
    } yield res

  private[this] def deleteFilter(design: Option[DesignDoc], filterName: String) =
    design match {
      case Some(design) =>
        for (doc <- db.saveDoc(design.copy(filters = design.filters - filterName)).right)
          yield doc.isDefined
      case None =>
        Future.successful(Right(false))
    }

  // helper methods

  private def designDoc(json: String) =
    serializer.fromJson[DesignDoc](json)

}

