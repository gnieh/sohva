/*
 * This file is part of the blue project.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gnieh.sohva
package test

import serializer.liftjson

/** A user of blue has the standard fields of couchdb users
 *  but also contains extra information, specific to blue.
 *
 *  @author Lucas Satabin
 *
 */
case class User(name: String,
                firstName: String,
                lastName: String,
                email: String)(
                    val _rev: Option[String] = None) {

  val _id = "org.couchdb.user:" + name

}

object TestUser extends App {

  val couch = new CouchClient
  val session = couch.startSession

  session.login("admin", "admin")!

  val db = session.database("blue_users")

  db.create!

  val user = User("lsatabin", "Lucas", "Satabin", "lucas@publications.li")()

  db.saveDoc(user)!

  couch.shutdown

}