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

import spray.json._

/** The `EntityManager` is responsible for creating, storing and deleting the entities
 *  and associated components.
 *  Entities are stored in a CouchDB database.
 *
 *  @author Lucas Satabin
 */
trait EntityManager[Result[_]] {

  val database: Database[Result]

  /** Creates a simple untagged entity into the entity database and returns it */
  def createSimple(): Result[Entity]

  /** Creates a tagged entity into the entity database and returns it */
  def createTagged(tag: String): Result[Entity]

  /** Creates an entity into the entity database and returns it */
  def create(uuid: String, tag: Option[String]): Result[Unit]

  /** Deletes an entity and all attched components from the entity database */
  def deleteEntity(entity: Entity): Result[Boolean]

  /** Adds or updates the component to the given entity. If the entity is unknown, does nothing.
   *  Returns the saved component.
   */
  def saveComponent[T <: IdRev: Manifest: JsonFormat](entity: Entity, component: T): Result[T]

  /** Indicates whether the entity has a component of the given type attached to it */
  def hasComponentType[T: Manifest](entity: Entity): Result[Boolean]

  /** Indicates whether the entity has a component attached to it */
  def hasComponent[T: Manifest: JsonFormat](entity: Entity, component: T): Result[Boolean]

  /** Retrieves the component of the given type attached to the entity if any */
  def getComponent[T: Manifest: JsonReader](entity: Entity): Result[Option[T]]

  /** Removes the component with the given name from the entity. If the entity
   *  does not exist or has no component with the given name, returns false
   */
  def removeComponentType[T: Manifest](entity: Entity): Result[Boolean]

  /** Removes the given component from the entity. If the entity
   *  does not exist or has not this component attached, returns false
   */
  def removeComponent[T <: IdRev: Manifest: JsonFormat](entity: Entity, component: T): Result[Boolean]

  /** Returns the list of known entities */
  def entities: Result[Set[Entity]]

  /** Returns the list of known entities with the given tag */
  def entities(tag: String): Result[Set[Entity]]

}
