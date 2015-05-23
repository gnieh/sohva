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
package control

import gnieh.sohva.async.{
  BasicSession => ABasicSession,
  CouchClient => ACouchClient
}

import scala.util.Try

/** An instance of a Couch session that allows the user to perform authenticated
 *  operations using Basic.
 *
 *  @author Lucas Satabin
 */
class BasicSession private[control] (override val wrapped: ABasicSession)
    extends Session(wrapped) with gnieh.sohva.BasicSession[Try] {

  def this(
    username: String,
    password: String,
    couch: ACouchClient) =
    this(new ABasicSession(username, password, couch))

  val username =
    wrapped.username

  val password =
    wrapped.password

}
