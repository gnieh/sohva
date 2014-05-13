package gnieh.sohva
package entities

import async._

import akka.actor.ActorSystem
import akka.util._

import scala.concurrent.duration._

object BasicTest extends App {

  implicit val timeout = Timeout(20.seconds)
  implicit val system = ActorSystem("sohva")

  import system.dispatcher

  val couch = new CouchClient
  val session = couch.startCookieSession

  val f = for {
    true <- session.login("admin", "admin")
    db = session.database("entities")
    _ <- db.create
    manager = new EntityManager(db)
    entity <- manager.createTagged("Test Entity")
    comp1 = Component1("gruik", 3, "my first component")
    true <- manager.addComponent(entity, comp1)
    comp2 <- manager.getComponent[Component1](entity)
    true <- db.delete
  } yield comp2

  f.onComplete {
    case res =>
      println(res)
      system.shutdown()
  }

}

case class Component1(_id: String, field1: Int, field2: String) extends IdRev
