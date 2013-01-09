package gnieh.sohva
package test

import liftjson.serializer

object TestSSL extends App {

  val couch = new CouchClient(port = 6984, ssl = true)

  println(couch._all_dbs!)

  couch.shutdown

}