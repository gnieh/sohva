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

import net.liftweb.json._

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

/** The original change stream (meaning unfiltered) on the client side
 *  which contains the active connection to the database
 *
 *  @author Lucas Satabin
 */
class OriginalChangeStream(database: Database,
    filter: Option[String]) extends ChangeStream {

  import database.couch.serializer.formats

  import database.ec

  import ChangeStream._

  private[this] val uri = database.uri / "_changes"

  private[this] var _closed = false

  private[this] implicit def system = database.couch.system

  private[this] val actor = system.actorOf(Props(new ChangeActor))

  private[this] class ChangeActor extends Actor with ActorLogging {

    override def preStart(): Unit = {
      val couch = database.couch
      val localSettings =
        ClientConnectionSettings(system).copy(responseChunkAggregationLimit = 0, requestTimeout = Duration.Inf)
      log.debug(s"change stream settings for database ${database.name}: $localSettings")
      IO(Http) ! Http.Connect(couch.host, port = couch.port, sslEncryption = couch.ssl, settings = Some(localSettings))
    }

    def receive = connecting(sender, Map())

    def connecting(commander: ActorRef, handlers: Map[Int, ((String, Option[JObject])) => Unit]): Receive = {
      case _: Http.Connected =>
        // connection has been established, send the change stream request
        val params = {
          val base = Map(
            "feed" -> "continuous",
            "since" -> "now",
            "include_docs" -> "true"
          )
          filter match {
            case Some(filter) => base + ("filter" -> filter)
            case None         => base
          }
        }
        val req = HttpRequest(uri = uri <<? params)

        sender ! req

        context.become(receiving(commander, handlers))

      case Http.CommandFailed(Http.Connect(address, _, _, _, _)) =>
        commander ! Status.Failure(new RuntimeException("Connection error"))
        close()

      case Register(handler) =>
        context.become(connecting(commander, handlers + (handler.hashCode -> handler)))

      case Unregister(id) =>
        context.become(connecting(commander, handlers - id))

    }

    def receiving(commander: ActorRef, handlers: Map[Int, ((String, Option[JObject])) => Unit]): Receive = {
      case MessageChunk(data, _) =>
        Try(parse(data.asString)).map {
          case change(seq, id, rev, deleted, doc) =>
            for ((_, f) <- handlers)
              f(id, doc)
          case _ =>
          // ignore other messages
        }

      case ChunkedMessageEnd(_, _) =>
        sender ! Http.Close
        close()

      case Register(handler) =>
        context.become(receiving(commander, handlers + (handler.hashCode -> handler)))

      case Unregister(id) =>
        context.become(receiving(commander, handlers - id))

      case Http.SendFailed(_) | Timedout(_) =>
        commander ! Status.Failure(new RuntimeException("Request error"))
        close()

      case CloseStream =>
        commander ! Http.Close
        context.stop(self)

    }

  }

  def close(): Unit = synchronized {
    // mark the stream as closed
    _closed = true
    actor ! CloseStream
  }

  def closed = _closed

  def foreach(f: Tuple2[String, Option[JObject]] => Unit): Int = synchronized {
    if (!closed) {
      require(f != null, "Function must not be null")
      actor ! Register(f)
      f.hashCode
    } else {
      -1
    }
  }

  def filter(p: Tuple2[String, Option[JObject]] => Boolean): ChangeStream =
    new FilteredChangeStream(p, this)

  def unregister(id: Int): Unit = synchronized {
    if (!closed)
      actor ! Unregister(id)
  }

}

/** The filtered change stream
 *
 *  @author Lucas Satabin
 */
class FilteredChangeStream(p: Tuple2[String, Option[JObject]] => Boolean, original: ChangeStream) extends ChangeStream {

  def foreach(f: Tuple2[String, Option[JObject]] => Unit) =
    original.foreach {
      case (id, doc) if p(id, doc) =>
        f(id, doc)
      case _ =>
      // do nothing
    }

  def filter(p: Tuple2[String, Option[JObject]] => Boolean): ChangeStream =
    new FilteredChangeStream(p, this)

  def closed =
    original.closed

  def close() =
    original.close()

  def unregister(id: Int) =
    original.unregister(id)

}

private case class Unregister(id: Int)
private case class Register(handler: ((String, Option[JObject])) => Unit)
private case object CloseStream

