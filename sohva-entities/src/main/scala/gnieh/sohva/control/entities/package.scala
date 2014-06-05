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
package control

import scala.concurrent.{
  Await,
  Future
}
import scala.concurrent.duration.Duration

import scala.util.Try

/** This package exposes classes that allows user to manage entities and their
 *  components within a CouchDB database.
 *
 *  Entities are conceptually a simple identifier. In database they are stored as
 *  a simple document that has a single optional `tag` field.
 *  The components are stored in their own document as well.
 *  The [[gnieh.sohva.entities.EntityManager]] also manages views that allow for
 *  retrieving entities and their components.
 */
package object entities {

  type Entity = String

  def synced[T](f: Future[T]): Try[T] = Try(Await.result(f, Duration.Inf))

  implicit class RichEntity(val entity: Entity) extends AnyVal {

    /** Adds the given component to the entity */
    def add[T <: IdRev: Manifest](component: T)(implicit manager: EntityManager): Try[Boolean] =
      manager.addComponent(entity, component)

    /** Removes the given component to the entity */
    def remove[T <: IdRev: Manifest](component: T)(implicit manager: EntityManager): Try[Boolean] =
      manager.removeComponent(entity, component)

    /** Removes all components of a given type attached to the entity */
    def remove[T: Manifest](implicit manager: EntityManager): Try[Boolean] =
      manager.removeComponentType[T](entity)

    /** Indicates whether the entity has at least one component of the given name */
    def has[T: Manifest](implicit manager: EntityManager): Try[Boolean] =
      manager.hasComponentType[T](entity)

    /** Indicates whether the entity has the given component */
    def has[T: Manifest](component: T)(implicit manager: EntityManager): Try[Boolean] =
      manager.hasComponent(entity, component)

    /** Gets the component of the given type attached to the entity if any */
    def get[T: Manifest](implicit manager: EntityManager): Try[Option[T]] =
      manager.getComponent[T](entity)

    /** Removes the entity from the system */
    def delete(implicit manager: EntityManager): Try[Boolean] =
      manager.deleteEntity(entity)

  }

}
