/*
* This file is part of the sohva project.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*couch.http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package gnieh.sohva
package async

import dispatch._
import Defaults._

import net.liftweb.json._

import scala.collection.mutable.Map

/** The original change stream (meaning unfiltered on the client side
 *  which contains the active connection to the database
 *
 *  @author Lucas Satabin
 */
class OriginalChangeStream(database: Database,
                           filter: Option[String]) extends ChangeStream {

  import database.couch.serializer.formats

  import ChangeStream._

  private[this] var handler = as.stream.Lines(onChange)

  private[this] var request = database.request / "_changes"

  private[this] var actions = Map.empty[Int, Tuple2[String, Option[JObject]] => Unit]

  private[this] var _closed = false

  for {
    info <- database.info.right
    () <- database.couch._http(request <<? List(
      "feed" -> "continuous",
      "since" -> info.map(_.update_seq.toString).getOrElse("0"),
      "include_docs" -> "true"
    ) <<? (if(filter.isDefined) List("filter" -> filter.get) else Nil) > handler)
  } {
    // if the request ends for any reason, close and clear everything
    close
  }

  /* notify the registered handler if any */
  private[this] def onChange(json: String) = synchronized {
    json match {
      case change(seq, id, rev, deleted, doc) if !closed =>
        for((_, f) <- actions)
          f(id, doc)
      case last_seq(seq) =>
      case _ =>
        // ignore other messages
    }
  }

  def close(): Unit = synchronized {
    // unregister the handlers if any
    actions.clear
    // stop the request handler
    handler.stop
    // mark the stream as closed
    _closed = true
    // free resources
    handler = null
    request = null
    actions = null
  }

  def closed = _closed

  def foreach(f: Tuple2[String, Option[JObject]] => Unit): Int = synchronized {
    if(!closed) {
      require(f != null, "Function must not be null")
      val fId = f.hashCode
      actions(fId) = f
      fId
    } else {
      -1
    }
  }

  def filter(p: Tuple2[String, Option[JObject]] => Boolean): ChangeStream =
    new FilteredChangeStream(p, this)

  def unregister(id: Int): Unit = synchronized {
    if(!closed)
      actions -= id
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

  def unregister(id: Int) =
    original.unregister(id)

}
