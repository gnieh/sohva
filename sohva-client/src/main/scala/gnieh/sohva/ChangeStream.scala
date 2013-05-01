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

import dispatch._
import Defaults._

import net.liftweb.json._

import scala.collection.mutable.Map

/** A stream that gets the changes from a database and notifies a registered handler
 *  when any change is received.
 *
 *  @author Lucas Satabin
 */
class ChangeStream(database: Database,
                   filter: Option[String]) {

  import database.couch.serializer.formats

  import ChangeStream._

  private[this] var handler = as.stream.Lines(onChange)

  private[this] var request = database.request / "_changes"

  private[this] var actions = Map.empty[Int, (String, Option[JObject]) => Unit]

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

  /** Closes this change stream. After calling this not new events are sent to the
   *  registered handler if any.
   */
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

  /** Indicates whether this stream is closed */
  def closed = _closed

  /** Alias for `foreach((id, doc) => f(id, doc))`. this is intended to be used in for-comprehensions */
  def foreach(f: Tuple2[String, Option[JObject]] => Unit): Int = {
    require(f != null, "Function must not be null")
    foreach((id, doc) => f(id, doc))
  }

  /** Calls the function for each received change. If the document was added or updated,
   *  it is passed along with its identifier, if it was deleted, only the identifier is
   *  given.
   *  The identifier of the registered handler is immediately returned to allow for later
   *  unregistration.
   *  If the stream is closed, the handler is not registered and the function returns `-1`
   */
  def foreach(f: (String, Option[JObject]) => Unit): Int = synchronized {
    if(!closed) {
      require(f != null, "Function must not be null")
      val fId = f.hashCode
      actions(fId) = f
      fId
    } else {
      -1
    }
  }

  /** Unregisters the change handler identifier by the given identifier */
  def unregister(id: Int): Unit = synchronized {
    if(!closed)
      actions -= id
  }

}

private[sohva] object ChangeStream {

  object change {
    def unapply(json: String)(implicit formats: Formats) =
      parse(json).extractOpt[Change].flatMap(Change.unapply)
  }

  object last_seq {
    def unapply(json: String)(implicit formats: Formats) =
      parse(json).extractOpt[LastSeq].flatMap(LastSeq.unapply)
  }

}

/** A change that occurred in the database.
 *
 *  @author Lucas Satabin
 */
private[sohva] case class Change(seq: Int, id: String, rev: String, deleted: Boolean, doc: Option[JObject])

/** Last update sequence sent by the server
 *
 *  @author Lucas Satabin
 */
private[sohva] case class LastSeq(last_seq: Int)

