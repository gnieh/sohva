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
package sync
package entities

import net.liftweb.json._

import scala.util.Try

import gnieh.sohva.entities.Entity

/** The `EntityManager` is responsible for creating, storing and deleting the entities
 *  and associated components.
 *  Entities are stored in a CouchDB database.
 *
 *  @author Lucas Satabin
 */
class EntityManager(val database: Database) extends gnieh.sohva.entities.EntityManager[Identity] {

  val wrapped = new async.entities.EntityManager(database.wrapped)

  @inline def createSimple(): Entity =
    synced(wrapped.createSimple())

  @inline def createTagged(tag: String): Entity =
    synced(wrapped.createTagged(tag))

  @inline def create(uuid: String, tag: Option[String]): Unit =
    synced(wrapped.create(uuid, tag))

  @inline def deleteEntity(entity: Entity): Boolean =
    synced(wrapped.deleteEntity(entity))

  @inline def saveComponent[T <: IdRev: Manifest](entity: Entity, component: T): T =
    synced(wrapped.saveComponent[T](entity, component))

  @inline def hasComponentType[T: Manifest](entity: Entity): Boolean =
    synced(wrapped.hasComponentType[T](entity))

  @inline def hasComponent[T: Manifest](entity: Entity, component: T): Boolean =
    synced(wrapped.hasComponent[T](entity, component))

  @inline def getComponent[T: Manifest](entity: Entity): Option[T] =
    synced(wrapped.getComponent[T](entity))

  @inline def removeComponentType[T: Manifest](entity: Entity): Boolean =
    synced(wrapped.removeComponentType[T](entity))

  @inline def removeComponent[T <: IdRev: Manifest](entity: Entity, component: T): Boolean =
    synced(wrapped.removeComponent[T](entity, component))

  @inline def entities: Set[Entity] =
    synced(wrapped.entities)

  @inline def entities(tag: String): Set[Entity] =
    synced(wrapped.entities(tag))

}
