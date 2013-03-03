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

import java.io.File

/** @author Lucas Satabin
 *
 */
object TestAttach extends App {

  case class Test(_id: String)(
    val _rev: Option[String] = None,
    val _attachments: Option[Map[String, Attachment]] = None)

  val couch = new CouchClient
  val session = couch.startSession

  val test = session.database("test")

  session.login("admin", "admin")!

  test.create!

  for (res1 <- test.attachTo("truie", new File("src/test/resources/test.txt"), None)) {
    println(res1)

    for (res2 <- test.getDocById[Test]("truie").right) {
      println(res2.flatMap(_._attachments))
      for (att <- test.getAttachment("truie", "test.txt").right) {
        println(att.map(_._1))
        println(att.map(f => new java.util.Scanner(f._2).useDelimiter("\\A").next))

        session.logout!

        for (
          res3 <- test.deleteAttachment("truie", "test.txt").right;
          res4 <- test.getDocById[Test]("truie").right
        ) {
          println(res3)
          println(res4.flatMap(_._attachments))
          for (att <- test.getAttachment("truie", "test.txt").right) {
            println(att)
            couch.shutdown
          }
        }
      }
    }

  }

}
