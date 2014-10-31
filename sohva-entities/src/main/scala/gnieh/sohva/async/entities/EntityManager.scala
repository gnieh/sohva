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
package async
package entities

import gnieh.sohva.entities.Entity

import scala.annotation.tailrec
import scala.concurrent._

import impl._

import net.liftweb.json._

import org.slf4j.LoggerFactory

/** The `EntityManager` is responsible for creating, storing and deleting the entities
 *  and associated components.
 *  Entities are stored in a CouchDB database.
 *
 *  @author Lucas Satabin
 */
class EntityManager(val database: Database) extends gnieh.sohva.entities.EntityManager[Future] {

  import database.ec

  private val logger = LoggerFactory.getLogger(classOf[EntityManager])

  private val manager =
    new DesignManager(database)

  def createSimple(): Future[Entity] =
    for {
      uuid <- database.couch._uuid
      () <- create(uuid, None)
    } yield uuid

  def createTagged(tag: String): Future[Entity] =
    for {
      uuid <- database.couch._uuid
      () <- create(uuid, Some(tag))
    } yield uuid

  def create(uuid: String, tag: Option[String]): Future[Unit] =
    for (_ <- database.saveRawDoc(serializeEntity(CouchEntity(uuid, tag))))
      yield ()

  def deleteEntity(entity: Entity): Future[Boolean] =
    for {
      comps <- manager.components.query[List[String], String, JValue](
        startkey = Some(List(entity)),
        endkey = Some(List(s"${entity}0")),
        inclusive_end = false)
      results <- database.deleteDocs(entity :: comps.rows.map(_.value))
      res <- allOk(results, true)
    } yield res

  def saveComponent[T <: IdRev: Manifest](entity: Entity, component: T): Future[T] =
    database.getDocRevision(entity).flatMap {
      case Some(_) =>
        // the entity is known
        if (logger.isDebugEnabled)
          logger.debug(s"Add component ${component._id} to entity $entity")
        for (c <- database.saveRawDoc(serializeComponent(entity, component)))
          yield database.serializer.fromJson[T](c)
      case None =>
        // the entity is unknown
        Future.failed(new SohvaException(s"Trying to add component ${component._id} to unknown entity $entity"))
    }

  def hasComponentType[T: Manifest](entity: Entity): Future[Boolean] =
    for {
      res <- manager.components.query[List[String], JValue, JValue](key = Some(List(entity, compType[T])))
    } yield res.total_rows > 0

  def hasComponent[T: Manifest](entity: Entity, component: T): Future[Boolean] =
    for {
      res <- manager.components.query[List[String], Set[T], JValue](key = Some(List(entity, compType[T])))
    } yield res.rows.find {
      case Row(_, _, c, _) => c == component
    }.isDefined

  def getComponent[T: Manifest](entity: Entity): Future[Option[T]] =
    manager.components.query[List[String], JValue, T](key = Some(List(entity, compType[T])), include_docs = true) map {
      case ViewResult(_, _, List(Row(_, _, _, doc)), _) => doc
      case _ => None
    }

  def removeComponentType[T: Manifest](entity: Entity): Future[Boolean] =
    manager.components.query[List[String], List[String], JValue](key = Some(List(entity, compType[T]))) flatMap {
      case ViewResult(_, _, List(Row(_, _, comps, _)), _) =>
        for {
          results <- database.deleteDocs(comps)
          res <- allOk(results, true)
        } yield res
      case _ =>
        Future.successful(false)
    }

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
      ViewResult(_, _, rows, _) <- manager.tags.query[String, String, JValue]()
    } yield rows.map {
      case Row(_, _, entity, _) => entity
    }.toSet

  def entities(tag: String): Future[Set[Entity]] =
    for {
      ViewResult(_, _, rows, _) <- manager.tags.query[String, String, JValue](key = Some(tag))
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
