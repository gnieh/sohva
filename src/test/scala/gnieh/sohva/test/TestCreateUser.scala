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
package test

/** @author Lucas Satabin
 *
 */
object TestCreateUser extends App {

  val couch = new CouchClient()

  val name = "grumpf"
  val session = couch.startSession

  println("plop")

  couch.users.add(name, name).map {
    case true =>
      println("truie")
      println(session.login(name, name)!)
      println(session.isLoggedIn!)
      println(session.logout!)
      println(session.isLoggedIn!)
      println(session.login(name, name + "_wrong")!)
      println(session.isLoggedIn!)
      println(session.login("admin", "admin")!)
      println(session.users.delete(name)!)
    case false =>
      println("argh")
  }.foreach { _ =>
    couch.shutdown
  }

}