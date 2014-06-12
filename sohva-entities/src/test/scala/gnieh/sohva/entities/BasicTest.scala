package gnieh.sohva
package sync
package entities

import akka.actor.ActorSystem
import akka.util._

import scala.concurrent._
import scala.concurrent.duration._

import org.scalatest._

class BasicTest extends FlatSpec with ShouldMatchers with BeforeAndAfterAll {

  import system.dispatcher

  implicit val system = ActorSystem()
  implicit val timeout = Timeout(5.seconds)

  val couch = new CouchClient
  val session = couch.startCookieSession
  val db =  session.database("sohva-entities-tests")

  "It" should "be possible to create and manage entities with a manager" in {
    val comp = Component1("gruik", 3, "my first component")

    val manager = new EntityManager(db)

    val entity = manager.createTagged("Test Entity")

    val comp1 = Component1("gruik", 3, "my first component")

    val added = manager.saveComponent(entity, comp1)

    added._rev should be('defined)

    val res = manager.getComponent[Component1](entity)

    res should be('defined)
    val result = res.get
    result should be(comp)
    result._rev should be('defined)

    result should be(added)

  }

  override def beforeAll() {
    // login
    session.login("admin", "admin")
    // create database
    if(db.exists)
      db.delete
    db.create
  }

  override def afterAll() {
    // cleanup database
    if(db.exists)
      db.delete
    // logout
    session.logout
    couch.shutdown()
    system.shutdown()
  }

}

case class Component1(_id: String, field1: Int, field2: String) extends IdRev
