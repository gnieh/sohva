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

import gnieh.sohva.entities.Entity

import scala.concurrent.Future

import spray.json._

package object entities {

  implicit class RichEntity(val entity: Entity) extends AnyVal {

    /** Adds or updates the given component to the entity */
    def save[T <: IdRev: Manifest: JsonFormat](component: T)(implicit manager: EntityManager): Future[T] =
      manager.saveComponent(entity, component)

    /** Removes the given component to the entity */
    def remove[T <: IdRev: Manifest: JsonFormat](component: T)(implicit manager: EntityManager): Future[Boolean] =
      manager.removeComponent(entity, component)

    /** Removes all components of a given type attached to the entity */
    def remove[T: Manifest](implicit manager: EntityManager): Future[Boolean] =
      manager.removeComponentType[T](entity)

    /** Indicates whether the entity has at least one component of the given name */
    def has[T: Manifest](implicit manager: EntityManager): Future[Boolean] =
      manager.hasComponentType[T](entity)

    /** Indicates whether the entity has the given component */
    def has[T: Manifest: JsonFormat](component: T)(implicit manager: EntityManager): Future[Boolean] =
      manager.hasComponent(entity, component)

    /** Gets the component of the given type attached to the entity if any */
    def get[T: Manifest: JsonFormat](implicit manager: EntityManager): Future[Option[T]] =
      manager.getComponent[T](entity)

    /** Removes the entity from the system */
    def delete(implicit manager: EntityManager): Future[Boolean] =
      manager.deleteEntity(entity)

  }

}
