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

import spray.httpx.unmarshalling.Unmarshaller

import scala.language.higherKinds

/** A show function that can be queried.
 *
 *  @author Lucas Satabin
 */
trait Show[Result[_]] {

  /** Indicates whether this view exists */
  def exists: Result[Boolean]

  /** Returns the result of querying the show function with the document with the given identifier
   *  or `None` for the `null` document.
   */
  def query[T: Unmarshaller](docId: Option[String] = None, format: Option[String] = None): Result[T]

}
