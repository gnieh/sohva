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

import java.util.Date

/** The users database, exposing the interface for managing couchdb users.
 *
 *  @author Lucas Satabin
 */
trait Users[Result[_]] {

  var dbName: String

  /** Adds a new user with the given role list to the user database,
   *  and returns the new instance.
   */
  def add(name: String,
    password: String,
    roles: List[String] = Nil): Result[Boolean]

  /** Deletes the given user from the database. */
  def delete(name: String): Result[Boolean]

  /** Generates a password reset token for the given user with the given validity and returns it */
  def generateResetToken(name: String, until: Date): Result[String]

  /** Resets the user password to the given one if:
   *   - a password reset token exists in the database
   *   - the token is still valid
   *   - the saved token matches the one given as parameter
   */
  def resetPassword(name: String, token: String, password: String): Result[Boolean]

}

