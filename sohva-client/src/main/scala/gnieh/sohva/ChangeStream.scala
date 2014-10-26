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

import spray.json._

import rx.lang.scala._

import scala.util.Try

/** A stream that represents a connection to the `_changes` stream of a database.
 *
 *  @author Lucas Satabin
 */
trait ChangeStream {

  /** The `Observable` to subscribe to to be notified about changes over this stream.
   *  This `Observable` can be combined with other ones, filtered, mapped, ...
   *  See the [documentation](http://rxscala.github.io/scaladoc/index.html#rx.lang.scala.Observable) for more details.
   */
  def stream: Observable[(String, Option[JsObject])]

  /** Subscribe to the original change stream initiated with the database.
   *  this is equivalent to calling `changes.stream.subscribe(obs)`
   */
  def subscribe(obs: ((String, Option[JsObject])) => Unit): Subscription

  /** Closes this stream and the underlying `Observable`. Subscriber will receive a terminatio
   *  message
   */
  def close(): Unit

}

object Change {
  def unapply(json: JsValue)(implicit formats: JsonFormat[Change]): Option[(Int, String, String, Boolean, Option[JsObject])] =
    Try(json.convertTo[Change]).toOption.flatMap(Change.unapply)
}

object LastSeq {
  def unapply(json: JsValue)(implicit formats: JsonFormat[LastSeq]): Option[Int] =
    Try(json.convertTo[LastSeq]).toOption.flatMap(LastSeq.unapply)
}

case class Change(seq: Int, id: String, rev: String, deleted: Boolean, doc: Option[JsObject])

case class LastSeq(last_seq: Int)
