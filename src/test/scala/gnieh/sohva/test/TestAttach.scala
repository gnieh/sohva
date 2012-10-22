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

import java.io.File

/** @author Lucas Satabin
 *
 */
object TestAttach extends App {

  case class Test(_id: String)(
    val _rev: Option[String] = None,
    val _attachments: Option[Map[String, Attachment]] = None)

  val couch = new CouchClient().startSession

  val test = couch.database("test")

  couch.login("admin", "admin")!

  test.create!

  couch.logout!

  for (res1 <- test.attachTo("truie", new File("src/test/resources/test.txt"), None)) {
    println(res1)

    for (res2 <- test.getDocById[Test]("truie")) {
      println(res2)
      couch.shutdown
    }

  }

}