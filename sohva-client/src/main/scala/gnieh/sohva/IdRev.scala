/*
 * This file is part of the sohva project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gnieh.sohva

/** Mix in this trait with any object to have more efficient automatic handling
 *  of documents (no reflective method call is performed)
 *
 *  @author Lucas Satabin
 */
trait IdRev {
  val _id: String
  var _rev: Option[String] = None

  /** Sets the revision and returns this (modified) instance */
  def withRev(rev: Option[String]): this.type = {
    _rev = rev
    this
  }

}

