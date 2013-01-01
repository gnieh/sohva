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

import sync._
import serializer.liftjson

/** @author satabin
 *
 */
object TestRevisions extends App {

  val couch = new CouchClient
  val session = couch.startSession

  session.login("admin", "admin")

  val test = session.database("test")

  test.create

  case class TestDoc(_id: String, value: Int)(val _rev: Option[String] = None)

  val saved = test.saveDoc(TestDoc("test", 14)())

  val last = test.saveDoc(saved.get.copy(value = 57)(saved.get._rev))

  println(test.getDocById[TestDoc]("test"))
  println(test.getDocById[TestDoc]("test", saved.get._rev))
  println(test.getDocById[TestDoc]("test", last.get._rev))

  test.deleteDoc("test")

  couch.shutdown

}