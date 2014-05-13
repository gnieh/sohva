/* Licensed under the Apache License, Version 2.0 (the "License");
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
package entities

import scala.annotation.tailrec
import scala.concurrent._

import impl._

import async.Database

import net.liftweb.json._

import org.slf4j.LoggerFactory

/** The `EntityManager` is responsible for creating, storing and deleting the entities
 *  and associated components.
 *  Entities are stored in a CouchDB database.
 *
 *  @author Lucas Satabin
 */
class EntityManager(val database: Database) {

  import database.ec

  private val logger = LoggerFactory.getLogger(classOf[EntityManager])

  private val manager =
    new DesignManager(database)

  /** Creates a simple untagged entity into the entity database and returns it */
  def createSimple(): Future[Entity] =
    create(None)

  /** Creates a tagged entity into the entity database and returns it */
  def createTagged(tag: String): Future[Entity] =
    create(Some(tag))

  /** Creates an entity into the entity database and returns it */
  def create(tag: Option[String]): Future[Entity] =
    for {
      uuid <- database.couch._uuid
      _ <- database.saveRawDoc(serializeEntity(CouchEntity(uuid, tag)))
    } yield uuid

  /** Deletes an entity and all attched components from the entity database */
  def deleteEntity(entity: Entity): Future[Boolean] =
    for {
      view <- manager.components
      comps <- view.query[List[String], String, JValue](startkey = Some(List(entity)))
      results <- database.deleteDocs(entity :: comps.rows.map(_.value))
      res <- allOk(results, true)
    } yield res

  /** Adds the component to the given entity. If the entity is unknown, does nothing.
   *  Returns `true` iff the component was actually saved. */
  def addComponent[T <: IdRev: Manifest](entity: Entity, component: T): Future[Boolean] =
    database.getDocRevision(entity).flatMap {
      case Some(_) =>
        // the entity is known
        if(logger.isDebugEnabled)
          logger.debug(s"Add component ${component._id} to entity $entity")
        for(_ <- database.saveRawDoc(serializeComponent(entity, component)))
          yield true
      case None =>
        // the entity is unknown
        Future.successful(false)
    }

  def hasComponentType[T: Manifest](entity: Entity): Future[Boolean] =
    for {
      view <- manager.components
      res <- view.query[List[String], JValue, JValue](key = Some(List(entity, compType[T])))
    } yield res.total_rows > 0

  def hasComponent[T: Manifest](entity: Entity, component: T): Future[Boolean] =
    for {
      view <- manager.components
      res <- view.query[List[String], Set[T], JValue](key = Some(List(entity, compType[T])))
    } yield res.rows.find {
      case Row(_, _, c, _) => c == component
    }.isDefined

  def getComponent[T: Manifest](entity: Entity): Future[Option[T]] =
    for {
      view <- manager.components
      ViewResult(_, _, List(Row(_, _, _, doc)), _) <-
         view.query[List[String], JValue, T](key = Some(List(entity, compType[T])), include_docs = true)
    } yield doc

  /** Removes the component with the given name from the entity. If the entity
   *  does not exist or has no component with the given name, returns false */
  def removeComponentType[T: Manifest](entity: Entity): Future[Boolean] =
    for {
      view <- manager.components
      ViewResult(_, _, List(Row(_, _, comps, _)), _) <-
        view.query[List[String], List[String], JValue](key = Some(List(entity, compType[T])))
      results <- database.deleteDocs(comps)
      res <- allOk(results, true)
    } yield res

  def removeComponent[T <: IdRev: Manifest](entity: Entity, component: T): Future[Boolean] =
    hasComponent(entity, component) flatMap {
      case true =>
        // the entity has the given component, we can delete it
        database.deleteDoc(component._id)
      case false =>
        // the entity has not this component, do nothing
        Future.successful(false)
    }

  // an entity manager is also a collection of entities
  def entities: Future[Set[Entity]] =
    for {
      view <- manager.tags
      ViewResult(_, _, rows, _) <- view.query[String, String, JValue]()
    } yield rows.map {
      case Row(_, _, entity, _) => entity
    }.toSet

  def entities(tag: String): Future[Set[Entity]] =
    for {
      view <- manager.tags
      ViewResult(_, _, rows, _) <- view.query[String, String, JValue](key = Some(tag))
    } yield rows.map {
      case Row(_, _, entity, _) => entity
    }.toSet

  private def compType[T: Manifest]: String =
    implicitly[Manifest[T]].runtimeClass.getCanonicalName

  /* Add the type and name fields */
  private def serializeComponent[T: Manifest](entity: Entity, comp: T): JValue =
    database.serializer.toJson(comp) ++
      JField("sohva-entities-type", JString("component")) ++
         JField("sohva-entities-name", JString(compType[T])) ++
         JField("sohva-entities-entity", JString(entity))

  private def serializeEntity(entity: CouchEntity): JValue =
    database.serializer.toJson(entity) ++
      JField("sohva-entities-type", JString("entity"))

  @tailrec
  private def allOk(results: List[DbResult], acc: Boolean): Future[Boolean] =
    results match {
      case OkResult(ok, _, _) :: tail =>
        allOk(tail, acc && ok)
      case (error @ ErrorResult(_, _, _)) :: _ =>
        Future.failed(new CouchException(500, Some(error)))
      case Nil =>
        Future.successful(acc)
    }

}
