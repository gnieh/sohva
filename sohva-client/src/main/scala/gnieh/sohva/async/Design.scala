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

import scala.concurrent.Future

import net.liftweb.json._

import spray.client.pipelining._

/** A design gives access to the different views.
 *  Use this class to get or create new views.
 *
 *  @author Lucas Satabin
 */
class Design(val db: Database,
    val name: String,
    val language: String) extends gnieh.sohva.Design[Future] {

  import db.couch.serializer

  import db.ec

  protected[sohva] def uri = db.uri / "_design" / name.trim

  def getDesignDocument: Future[Option[DesignDoc]] =
    for (design <- db.couch.optHttp(Get(uri)))
      yield design.map(designDoc)

  def delete: Future[Boolean] =
    db.deleteDoc("_design/" + name.trim)

  def saveView(viewName: String,
    mapFun: String,
    reduceFun: Option[String] = None): Future[Unit] =
    saveView(viewName, ViewDoc(mapFun, reduceFun))

  def saveView(viewName: String, view: ViewDoc): Future[Unit] =
    for {
      design <- getDesignDocument
      doc <- db.saveDoc(newDoc(design, viewName, view))
    } yield ()

  private[this] def newDoc(design: Option[DesignDoc], viewName: String, view: ViewDoc) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(views = design.views + (viewName -> view)).withRev(design._rev)
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(viewName -> view), None)
    }

  def deleteView(viewName: String): Future[Unit] =
    for {
      design <- getDesignDocument
      _ <- deleteView(design, viewName)
    } yield ()

  private[this] def deleteView(design: Option[DesignDoc], viewName: String) =
    design match {
      case Some(design) =>
        db.saveDoc(design.copy(views = design.views - viewName))
      case None => Future.failed(new SohvaException("Unable to deleted view " + viewName + " for unknown design " + name))
    }

  def view(viewName: String): View =
    new View(this.name, db, viewName)

  def saveValidateFunction(validateFun: String): Future[Unit] =
    for {
      design <- getDesignDocument
      _ <- db.saveDoc(newDoc(design, validateFun))
    } yield ()

  private[this] def newDoc(design: Option[DesignDoc], validateFun: String) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(validate_doc_update = Some(validateFun))
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(), Some(validateFun))
    }

  def deleteValidateFunction: Future[Unit] =
    for {
      design <- getDesignDocument
      _ <- deleteValidateFunction(design)
    } yield ()

  private[this] def deleteValidateFunction(design: Option[DesignDoc]) =
    design match {
      case Some(design) =>
        for (doc <- db.saveDoc(design.copy(validate_doc_update = None)))
          yield doc
      case None => Future.failed(new SohvaException("Unable to delete validate function for unknown design: " + name))
    }

  def saveFilter(name: String, filterFun: String): Future[Unit] =
    for {
      design <- getDesignDocument
      _ <- db.saveDoc(withFilterDoc(design, name, filterFun))
    } yield ()

  private[this] def withFilterDoc(design: Option[DesignDoc], filterName: String, filterFun: String) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(filters = design.filters.updated(filterName, filterFun))
      case None =>
        // the design does not exist yet
        DesignDoc("_design/" + name, language, Map(), None, filters = Map(filterName -> filterFun))
    }

  def deleteFilter(name: String): Future[Unit] =
    for {
      design <- getDesignDocument
      _ <- deleteFilter(design, name)
    } yield ()

  private[this] def deleteFilter(design: Option[DesignDoc], filterName: String) =
    design match {
      case Some(design) =>
        for (doc <- db.saveDoc(design.copy(filters = design.filters - filterName)))
          yield doc
      case None =>
        Future.failed(new SohvaException("Unable to delete filter " + filterName + " for uknown design " + name))
    }

  // helper methods

  private def designDoc(json: JValue) =
    serializer.fromJson[DesignDoc](json)

}

