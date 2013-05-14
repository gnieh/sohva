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
package gnieh.sohva

import dispatch._
import Defaults._

/** A design gives access to the different views.
 *  Use this class to get or create new views.
 *
 *  @author Lucas Satabin
 */
case class Design(db: Database,
                  name: String,
                  language: String) {

  import db.couch.serializer

  private[sohva] def request = db.request / "_design" / name.trim

  /** Returns the design document from the couchdb instance.
   *  Returns `None` if the design document does not exist.
   */
  def getDesignDocument: Result[Option[DesignDoc]] =
    for(design <- db.couch.optHttp(request).right)
      yield design.map(designDoc)

  /** Deletes this design document from the couchdb instance */
  def delete: Result[Boolean] =
    db.deleteDoc("_design/" + name.trim)

  /** Creates or updates the view in this design
   *  with the given name, map function and reduce function.
   *  If the design does not exist yet, it is created.
   */
  def saveView(viewName: String,
               mapFun: String,
               reduceFun: Option[String] = None): Result[Boolean] =
    saveView(viewName, ViewDoc(mapFun, reduceFun))

  /** Creates or updates the view in this design with the given name.
   *  If the design does not exist yet, it is created.
   */
  def saveView(viewName: String, view: ViewDoc): Result[Boolean] =
    for {
      design <- getDesignDocument.right
      doc <- db.saveDoc(newDoc(design, viewName, view)).right
    } yield doc.isDefined

  private[this] def newDoc(design: Option[DesignDoc], viewName: String, view: ViewDoc) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(views = design.views + (viewName -> view))
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(viewName -> view), None)
    }

  /** Deletes the view with the given name from the design */
  def deleteView(viewName: String): Result[Boolean] =
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

  /** Returns the (typed) view in this design document.
   *  The different types are:
   *  - Key: type of the key for this view
   *  - Value: Type of the value returned in the result
   *  - Doc: Type of the full document in the case where the view is queried with `include_docs` set to `true`
   */
  def view[Key: Manifest, Value: Manifest, Doc: Manifest](viewName: String): View[Key, Value, Doc] =
    View[Key, Value, Doc](this, viewName)

  /** Creates or updates the document validation function.
   *  If the design does not exist yet, it is created.
   */
  def saveValidateFunction(validateFun: String): Result[Boolean] =
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

  /** Deletes the document validation function from the design */
  def deleteValidateFunction: Result[Boolean] =
    for {
      design <- getDesignDocument.right
      res <- deleteValidateFunction(design)
    } yield res

  private[this] def deleteValidateFunction(design: Option[DesignDoc]) =
    design match {
      case Some(design) =>
        for(doc <- db.saveDoc(design.copy(validate_doc_update = None)).right)
          yield doc.isDefined
      case None => Future.successful(Right(false))
    }

  /** Creates or updates a filter function.
   *  If the design does not exist yet, it is created.
   */
  def saveFilter(name: String, filterFun: String): Result[Boolean] =
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
        DesignDoc("_design/" + name, language, Map(), None, Map(filterName -> filterFun))
    }

  /** Deletes the filter identified by its name from the design document */
  def deleteFilter(name: String): Result[Boolean] =
    for {
      design <- getDesignDocument.right
      res <- deleteFilter(design, name)
    } yield res

  private[this] def deleteFilter(design: Option[DesignDoc], filterName: String) =
    design match {
      case Some(design) =>
        for(doc <- db.saveDoc(design.copy(filters = design.filters - filterName)).right)
          yield doc.isDefined
      case None =>
        Future.successful(Right(false))
    }

  // helper methods

  private def designDoc(json: String) =
    serializer.fromJson[DesignDoc](json)

}

private[sohva] case class DesignDoc(_id: String,
                                    language: String,
                                    views: Map[String, ViewDoc],
                                    validate_doc_update: Option[String],
                                    filters: Map[String, String] = Map(),
                                    val _rev: Option[String] = None)

