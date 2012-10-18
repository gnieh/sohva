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
object TestGet extends App {

  val couch = CouchDB(host = "127.0.0.1", admin = Some(("admin", "admin")))

  val db = couch.database("test")

  println(couch._all_dbs())

  case class TestDoc(_id: String, toto: Int)(val _rev: Option[String] = None)

  db.info.flatMap {
    case Some(info) =>
      println("well, database test exists")
      println(info)
      println("saving a doc in it")
      db.saveDoc(TestDoc("toto", 4)()).apply() match {
        case Some(doc) => println("youpi")
        case None => println("argh, y u no save doc???")
      }
      println("and now deleting...")
      db.delete
    case None =>
      println("database test does not exist")
      db.create
  }.foreach(_ => couch.shutdown)

}