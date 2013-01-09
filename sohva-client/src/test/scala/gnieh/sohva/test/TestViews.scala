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

import liftjson.serializer

/** @author Lucas Satabin
 *
 */
object TestViews extends App {

  val couch = new CouchClient
  val session = couch.startSession

  val db = session.database("test")

  session.login("admin", "admin")!

  db.delete!

  db.create!

  session.logout!

  case class TestDoc(_id: String, value: Int)(val _rev: Option[String] = None)

  for (i <- 1 to 10)
    db.saveDoc(TestDoc("doc:" + i, i * 27)())!

  val design = db.design("toto")

  design.saveView("test", "function(doc){emit(doc._id, doc._id);}")!

  val view = design.view[String, String, TestDoc]("test")

  view.query(include_docs = true).!.rows.foreach(println _)

  couch.shutdown

}