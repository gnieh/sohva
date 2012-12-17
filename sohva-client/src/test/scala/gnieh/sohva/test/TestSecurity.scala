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

/** @author satabin
 *
 */
object TestSecurity extends App {

  val couch = new CouchClient
  val session = couch.startSession

  val db = session.database("test_sec")

  couch.users.add("test", "test")!

  session.login("admin", "admin")!

  db.delete!

  println("database created: " + (db.create!))

  case class TestDoc(_id: String, value: Int)(val _rev: Option[String] = None)

  db.saveDoc(TestDoc("test", 18)())!

  session.logout!

  println("doc as anonymous (no security doc): " + (db.getDocById[TestDoc]("test")!))

  session.login("admin", "admin")!

  println(db.saveSecurityDoc(SecurityDoc(members = SecurityList(names = List("test"))))!)

  session.logout!

  try {
    println("doc as anonymous (with security doc): " + (db.getDocById[TestDoc]("test")!))
  } catch {
    case _ => println("héhéhé")
  }

  session.login("test", "test")!

  println("doc as `test' (with security doc): " + (db.getDocById[TestDoc]("test")!))

  db.deleteDoc("test")!

  session.login("admin", "admin")!

  session.users.delete("test")!

  couch.shutdown

}