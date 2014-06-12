package gnieh.sohva

/** This package exposes classes that allows user to manage entities and their
 *  components within a CouchDB database.
 *
 *  Entities are conceptually a simple identifier. In database they are stored as
 *  a simple document that has a single optional `tag` field.
 *  The components are stored in their own document as well.
 *  The [[gnieh.sohva.entities.EntityManager]] also manages views that allow for
 *  retrieving entities and their components.
 *
 *  @author Lucas Satabin
 */
package object entities {

  /** An entity is simply a string identifier */
  type Entity = String

}
