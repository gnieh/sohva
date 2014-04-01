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

import net.liftweb.json._

import scala.collection.mutable.Map

/** A stream that gets the changes from a database and notifies a registered handler
 *  when any change is received.
 *
 *  @author Lucas Satabin
 */
abstract class ChangeStream {

  /** Calls the function for each received change. If the document was added or updated,
   *  it is passed along with its identifier, if it was deleted, only the identifier is
   *  given.
   *  The identifier of the registered handler is immediately returned to allow for later
   *  unregistration.
   *  If the stream is closed, the handler is not registered and the function returns `-1`
   */
  def foreach(f: Tuple2[String, Option[JObject]] => Unit): Int

  /** Alias for `filter(f)`. this is intended to be used in for-comprehensions */
  def withFilter(f: Tuple2[String, Option[JObject]] => Boolean): ChangeStream = {
    require(f != null, "Function must not be null")
    filter(f)
  }
  /** Returns a change stream that is filtered by the given predicate */
  def filter(p: Tuple2[String, Option[JObject]] => Boolean): ChangeStream

  /** Unregisters the change handler identifier by the given identifier */
  def unregister(id: Int)

  /** Indicates whether this stream is closed */
  def closed: Boolean

  /** Closes this stream */
  def close(): Unit

}

protected[sohva] object ChangeStream {

  object change {
    def unapply(json: JValue)(implicit formats: Formats) =
      json.extractOpt[Change].flatMap(Change.unapply)
  }

  object last_seq {
    def unapply(json: JValue)(implicit formats: Formats) =
      json.extractOpt[LastSeq].flatMap(LastSeq.unapply)
  }

}

/** A change that occurred in the database.
 *
 *  @author Lucas Satabin
 */
protected[sohva] case class Change(seq: Int, id: String, rev: String, deleted: Boolean, doc: Option[JObject])

/** Last update sequence sent by the server
 *
 *  @author Lucas Satabin
 */
protected[sohva] case class LastSeq(last_seq: Int)

