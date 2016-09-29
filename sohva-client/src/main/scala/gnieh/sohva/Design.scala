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

import scala.concurrent.Future

import spray.json._

import akka.http.scaladsl.model._

/** A design gives access to the different views.
 *  Use this class to get or create new views.
 *
 *  @author Lucas Satabin
 */
class Design(val db: Database,
    val name: String,
    val language: String) {

  import db.ec

  import SohvaProtocol._

  protected[sohva] val uri = db.uri / "_design" / name.trim

  /** Check if the design exists.
   *
   *  @return true if it does, false otherwise
   */
  def exists: Future[Boolean] =
    for (h <- db.couch.rawHttp(HttpRequest(HttpMethods.HEAD, uri = uri)))
      yield h.status == StatusCodes.OK

  /** Create an empty design document if none exists.
   *  Raises an exception if the design already exists.
   *
   *  @return the design document if created..
   */
  def create: Future[DesignDoc] =
    for {
      ex <- exists
      cr <- if (ex) throw new SohvaException(f"Failed to create design. A design with the name $name already exists.")
      else db.saveDoc(DesignDoc("_design/" + name, language, Map(), None, Map(), Map(), Map(), Map(), Nil))
    } yield cr

  /** Returns the design document from the couchdb instance.
   *  Returns `None` if the design document does not exist.
   */
  def getDesignDocument: Future[Option[DesignDoc]] =
    for (
      design <- db.couch.optHttp(HttpRequest(uri = uri)) withFailureMessage
        f"Failed to fetch design document from $uri"
    ) yield design.map(designDoc)

  /** Deletes this design document from the couchdb instance */
  def delete: Future[Boolean] =
    db.deleteDoc("_design/" + name.trim)

  /** Creates or updates the view in this design
   *  with the given name, map function and reduce function.
   *  If the design does not exist yet, it is created.
   */
  def saveView(viewName: String,
    mapFun: String,
    reduceFun: Option[String] = None): Future[Unit] =
    saveView(viewName, ViewDoc(mapFun, reduceFun))

  /** Creates or updates the view in this design with the given name.
   *  If the design does not exist yet, it is created.
   */
  def saveView(viewName: String, view: ViewDoc): Future[Unit] =
    for {
      design <- getDesignDocument
      doc <- db.saveDoc(newDocWithView(design, viewName, view))
    } yield ()

  private[this] def newDocWithView(design: Option[DesignDoc], viewName: String, view: ViewDoc) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(views = design.views + (viewName -> view)).withRev(design._rev)
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(viewName -> view), None, Map(), Map(), Map(), Map(), Nil)
    }

  /** Deletes the view with the given name from the design */
  def deleteView(viewName: String): Future[Unit] =
    for {
      design <- getDesignDocument
      _ <- deleteView(design, viewName)
    } yield ()

  private[this] def deleteView(design: Option[DesignDoc], viewName: String) =
    design match {
      case Some(design) =>
        db.saveDoc(design.copy(views = design.views - viewName))
      case None => Future.failed(new SohvaException(f"Unable to delete view $viewName for unknown design $name"))
    }

  /** Returns the view in this design document. */
  def view(viewName: String): View =
    new View(this.name, db, viewName)

  /** Creates or update the show function in this design with the given name.
   *  If the design does not exist yet, it is created.
   */
  def saveShow(showName: String, showFun: String): Future[Unit] =
    for {
      design <- getDesignDocument
      _ <- db.saveDoc(newDocWithShow(design, showName, showFun))
    } yield ()

  private[this] def newDocWithShow(design: Option[DesignDoc], showName: String, showFun: String) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(shows = design.shows.updated(showName, showFun))
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(), None, Map(), Map(showName -> showFun), Map(), Map(), Nil)
    }

  /** Deletes the show function with the given name from the design */
  def deleteShow(showName: String): Future[Unit] =
    for {
      design <- getDesignDocument
      _ <- deleteShow(design, showName)
    } yield ()

  private[this] def deleteShow(design: Option[DesignDoc], showName: String) =
    design match {
      case Some(design) =>
        db.saveDoc(design.copy(shows = design.shows - showName))
      case None => Future.failed(new SohvaException(f"Unable to delete show function $showName for unknown design $name"))
    }

  /** Returns representation of the show function for this design. */
  def show(showName: String): Show =
    new Show(this.name, db, showName)

  /** Creates or update the update function in this design with the given name.
   *  If the design does not exist yet, it is created.
   */
  def saveList(listName: String, listFun: String): Future[Unit] =
    for {
      design <- getDesignDocument
      _ <- db.saveDoc(newDocWithList(design, listName, listFun))
    } yield ()

  private[this] def newDocWithList(design: Option[DesignDoc], listName: String, listFun: String) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(lists = design.lists.updated(listName, listFun))
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(), None, Map(), Map(), Map(), Map(listName -> listFun), Nil)
    }

  /** Deletes the list function with the given name from the design */
  def deleteList(listName: String): Future[Unit] =
    for {
      design <- getDesignDocument
      _ <- deleteList(design, listName)
    } yield ()

  private[this] def deleteList(design: Option[DesignDoc], listName: String) =
    design match {
      case Some(design) =>
        db.saveDoc(design.copy(lists = design.lists - listName))
      case None => Future.failed(new SohvaException(f"Unable to delete list function $listName for unknown design $name"))
    }

  /** Returns representation of the list function with the given view. */
  def list(listName: String): CList =
    new CList(this.name, db, listName)

  /** Creates or update the update function in this design with the given name.
   *  If the design does not exist yet, it is created.
   */
  def saveUpdate(updateName: String, updateFun: String): Future[Unit] =
    for {
      design <- getDesignDocument
      _ <- db.saveDoc(newDocWithUpdate(design, updateName, updateFun))
    } yield ()

  private[this] def newDocWithUpdate(design: Option[DesignDoc], updateName: String, updateFun: String) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(updates = design.updates.updated(updateName, updateFun))
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(), None, Map(updateName -> updateFun), Map(), Map(), Map(), Nil)
    }

  /** Deletes the update function with the given name from the design */
  def deleteUpdate(updateName: String): Future[Unit] =
    for {
      design <- getDesignDocument
      _ <- deleteUpdate(design, updateName)
    } yield ()

  private[this] def deleteUpdate(design: Option[DesignDoc], updateName: String) =
    design match {
      case Some(design) =>
        db.saveDoc(design.copy(updates = design.updates - updateName))
      case None => Future.failed(new SohvaException(f"Unable to delete update function $updateName for unknown design $name"))
    }

  /** Returns representation of the update function with the given view. */
  def update(updateName: String): Update =
    new Update(this.name, db, updateName)

  /** Creates or updates the document validation function.
   *  If the design does not exist yet, it is created.
   */
  def saveValidateFunction(validateFun: String): Future[Unit] =
    for {
      design <- getDesignDocument
      _ <- db.saveDoc(newDocWithValidate(design, validateFun))
    } yield ()

  private[this] def newDocWithValidate(design: Option[DesignDoc], validateFun: String) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(validate_doc_update = Some(validateFun))
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(), Some(validateFun), Map(), Map(), Map(), Map(), Nil)
    }

  /** Deletes the document validation function from the design */
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

  /** Creates or updates a filter function.
   *  If the design does not exist yet, it is created.
   */
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
        DesignDoc("_design/" + name, language, Map(), None, Map(), Map(filterName -> filterFun), Map(), Map(), Nil)
    }

  /** Deletes the filter identified by its name from the design document */
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
        Future.failed(new SohvaException("Unable to delete filter " + filterName + " for unknown design " + name))
    }

  /** Creates or updates the list of rewrite rules.
   *  If the design does not exist yet, it is created.
   */
  def saveRewriteRules(rules: List[RewriteRule]): Future[Unit] =
    for {
      design <- getDesignDocument
      _ <- db.saveDoc(newDocWithRewriteRules(design, rules))
    } yield ()

  private[this] def newDocWithRewriteRules(design: Option[DesignDoc], rules: List[RewriteRule]) =
    design match {
      case Some(design) =>
        // the updated design
        design.copy(rewrites = rules)
      case None =>
        // the design does not exist...
        DesignDoc("_design/" + name, language, Map(), None, Map(), Map(), Map(), Map(), rules)
    }

  /** Retrieves the rewrite rules associated to this design document. */
  def getRewriteRules(): Future[List[RewriteRule]] =
    for (design <- getDesignDocument)
      yield design match {
      case Some(d) => d.rewrites
      case None    => Nil
    }

  /** Requests compaction of this design. */
  def compact: Future[Boolean] =
    for (resp <- db.couch.http(HttpRequest(HttpMethods.POST, uri = db.uri / "_compact" / name)).withFailureMessage(f""))
      yield resp.asJsObject("ok").convertTo[Boolean]

  // helper methods

  private def designDoc(json: JsValue) =
    json.convertTo[DesignDoc]

  override def toString =
    uri.toString

}

case class DesignDoc(
  _id: String,
  language: String,
  views: Map[String, ViewDoc],
  validate_doc_update: Option[String],
  updates: Map[String, String],
  filters: Map[String, String],
  shows: Map[String, String],
  lists: Map[String, String],
  rewrites: List[RewriteRule]) extends IdRev

case class RewriteRule(from: String, to: String, method: String, query: Map[String, String])
