/*
* This file is part of the sohva project.
*
* Licensed under the Apache License, Version 2.0 (the "License");
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

import spray.json._

import akka.io._
import akka.actor._

import spray.http._
import spray.can.Http
import spray.can.client._

import scala.util.{
  Try,
  Success
}
import scala.concurrent.duration.Duration

import rx.lang.scala._

import java.util.concurrent.atomic.AtomicLong

/** The original change stream which contains the active connection to the database.
 *  this is the interface to subscribe to the observable, and to stop the stream.
 *
 *  @author Lucas Satabin
 */
class ChangeStream(database: Database, since: Option[Int], filter: Option[String]) extends gnieh.sohva.ChangeStream {

  implicit val system = database.couch.system

  private val subscriptionId = new AtomicLong

  private val actor = system.actorOf(Props(new ChangeActor(database, since, filter)))

  val stream: Observable[(String, Option[JsObject])] =
    Observable { observer =>
      val id = subscriptionId.getAndIncrement()
      // send the subscription request
      actor ! Subscribe(id, observer)
      // this subscription allows the observe to unsubscribe any time it wants
      Subscription {
        actor ! Unsubscribe(id)
      }
    }

  def subscribe(obs: ((String, Option[JsObject])) => Unit): Subscription =
    stream.subscribe(obs)

  def close(): Unit =
    actor ! CloseStream

  override def toString =
    (database.uri / "_changes").toString

}

/** This actor is responsible for managing the connection to the change stream
 *  of the database.
 *  It opens an endless update feed that gets notified by the database whenever some change
 *  happens. It notifies the obervers that subscribed to the `Observable`.
 *
 *  @author Lucas Satabin
 */
private class ChangeActor(database: Database, since: Option[Int], filter: Option[String]) extends Actor with ActorLogging {

  implicit def system = context.system

  import SohvaProtocol._

  private def uri = database.uri / "_changes"

  override def preStart(): Unit = {
    // ok so we just created a new change stream, connect to the `_changes` endpoint
    // this connection expect a chunked response and no timeout
    val couch = database.couch
    val localSettings =
      ClientConnectionSettings(system).copy(responseChunkAggregationLimit = 0, requestTimeout = Duration.Inf)
    log.debug(s"change stream settings for database ${database.name}: $localSettings")
    IO(Http) ! Http.Connect(couch.host, port = couch.port, sslEncryption = couch.ssl, settings = Some(localSettings))
  }

  def receive = connecting(Map())

  def connecting(observers: Map[Long, Observer[(String, Option[JsObject])]]): Receive = {
    case _: Http.Connected =>
      // connection has been established, send the change stream request
      val params = {
        val base = Map(
          "feed" -> "continuous",
          "since" -> since.map(_.toString).getOrElse("now"),
          "include_docs" -> "true",
          "heartbeat" -> "true"
        )
        filter match {
          case Some(filter) => base + ("filter" -> filter)
          case None         => base
        }
      }
      val req = HttpRequest(uri = uri <<? params)

      sender ! req

      // and now we are ready to receive update data
      context.become(receiving(sender, observers))

    case Http.CommandFailed(Http.Connect(address, _, _, _, _)) =>
      val exn = new RuntimeException("Could not connect to $address")
      // notify the already subscribed observers
      for ((_, o) <- observers)
        o.onError(exn)
      // and notify the commander that initiated the conncetion
      sender ! Status.Failure(exn)
      // finally, stop myself
      context.stop(self)

    case Subscribe(id, observer) =>
      // not so fast new subscriber, connection has not been established yet!
      // however, we are nice and still accept you
      context.become(connecting(observers + (id -> observer)))

    case Unsubscribe(id) =>
      // oh, that was quick, but ok, if you wanna leave, just do it,
      // we'll connect without you anyway
      context.become(connecting(observers - id))

  }

  def receiving(commander: ActorRef, observers: Map[Long, Observer[(String, Option[JsObject])]]): Receive = {
    case MessageChunk(data, _) =>
      log.debug(s"Change stream for database ${database.name} received a message")
      for {
        Change(seq, id, rev, deleted, doc) <- Try(JsonParser(data.asString).convertTo[Change])
        (_, o) <- observers
      } o.onNext(id, doc)

    case ChunkedMessageEnd(_, _) =>
      log.debug(s"Change stream for database ${database.name} received the end of stream message")
      // notify the observers that the stream has ended
      for ((_, o) <- observers)
        o.onCompleted()
      // and stop myself
      context.stop(self)

    case Subscribe(id, observer) =>
      log.debug(s"Subscription received for observer $id and database ${database.name}")
      // we are pleased to welcome a new observer, let xour observations be successful
      context.become(receiving(commander, observers + (id -> observer)))

    case Unsubscribe(id) =>
      log.debug(s"Unsubscription received for observer $id and database ${database.name}")
      // goodbye buddy!
      context.become(receiving(commander, observers - id))

    case Http.SendFailed(_) | Timedout(_) =>
      // notify the observers that some error happened
      val exn = new RuntimeException("The change stream request to $uri failed")
      for ((_, o) <- observers)
        o.onError(exn)
      // notify the commander that initiated the request
      commander ! Status.Failure(exn)
      // and stop myself
      context.stop(self)

    case CloseStream =>
      log.debug(s"Change stream of database ${database.name} was closed")
      // notify the observers that the stream has ended
      for ((_, o) <- observers)
        o.onCompleted()
      // close the connection
      commander ! Http.Close
      // and stop myself
      context.stop(self)

  }

}

private case class Unsubscribe(id: Long)
private case class Subscribe(id: Long, observer: Observer[(String, Option[JsObject])])
private case object CloseStream
