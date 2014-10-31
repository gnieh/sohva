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

/** An update handler that can be queried.
 *
 *  @author Lucas Satabin
 */
trait Update[Result[_]] {

  /** Indicates whether this update handler exists */
  def exists: Result[Boolean]

  /** Queries the update handler as a POST request.
   *  `body` is sent as a json value.
   */
  def query[Body, Resp: Unmarshaller](
    body: Body,
    docId: Option[String] = None,
    parameters: Map[String, String] = Map()): Result[Resp]

  /** Queries the update handler as a POST form-data request. */
  def queryForm[Resp: Unmarshaller](
    data: Map[String, String],
    docId: String,
    parameters: Map[String, String] = Map()): Result[Resp]

}
