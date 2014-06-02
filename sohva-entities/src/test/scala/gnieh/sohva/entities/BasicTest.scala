package gnieh.sohva
package entities

import async._

import akka.actor.ActorSystem
import akka.util._

import scala.concurrent._
import scala.concurrent.duration._

import org.scalatest._

class BasicTest extends FlatSpec with ShouldMatchers with BeforeAndAfterAll {

  import system.dispatcher

  implicit val system = ActorSystem()
  implicit val timeout = Timeout(5.seconds)

  def synced[T](f: Future[T]) = Await.result(f, Duration.Inf)

  val couch = new CouchClient
  val session = couch.startCookieSession
  val db =  session.database("sohva-entities-tests")

  "It" should "be possible to create and manage entities with a manager" in {
    val comp = Component1("gruik", 3, "my first component")

    val manager = new EntityManager(db)
    val f = for {
      entity <- manager.createTagged("Test Entity")
      comp1 = Component1("gruik", 3, "my first component")
      true <- manager.addComponent(entity, comp1)
      comp2 <- manager.getComponent[Component1](entity)
    } yield comp2

    val res = synced(f)

    res should be('defined)
    val result = res.get
    result should be(comp)
    result._rev should be('defined)

  }

  override def beforeAll() {
    // login
    synced(session.login("admin", "admin"))
    // create database
    if(!synced(db.exists))
      synced(db.create)
  }

  override def afterAll() {
    // cleanup database
    if(synced(db.exists))
      synced(db.delete)
    // logout
    synced(session.logout)
    couch.shutdown()
    system.shutdown()
  }

}

case class Component1(_id: String, field1: Int, field2: String) extends IdRev
