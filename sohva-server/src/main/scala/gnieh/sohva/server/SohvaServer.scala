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
package server

import java.io.{ InputStream, PrintStream }

import net.liftweb.json._

/** @author Lucas Satabin
 *
 */
class SohvaServer(input: InputStream, output: PrintStream) {

  def log(level: String, msg: String) {
    respond(JArray(List(JString(level), JString(msg))))
  }

  def respond(array: JArray) {
    output.println(compact(render(array)))
  }

  def start {
    while (true) {
      val scanner = new java.util.Scanner(input)
      val line = scanner.nextLine
      JsonParser.parse(line) match {
        case JArray(NewDDoc(action, name, ddoc))        =>
        case JArray(UpdateDDoc(id, action, name, ddoc)) =>
        case JArray(Reset(config))                      =>
        case JArray(JString("add_fun") :: args)         =>
        case JArray(JString("add_lib") :: args)         =>
        case JArray(JString("map_doc") :: args)         =>
        case JArray(JString("reduce") :: args)          =>
        case JArray(JString("rereduce") :: args)        =>
        case JArray(JString(cmd) :: _)                  => log("error", "unknown command: " + cmd)
        case _ =>
          throw new RuntimeException("Should NEVER happen")
      }
      output.println("""["log", """" + line.replace("\"", "\\\"") + "\"]")
      // ["ddoc","new","_design/test",{"_id":"_design/test","_rev":"4-c32544c33102bd27ff2e9676c3a5058e","language":"scala","views":{"my":{"map":"def map(doc: String) = {}"}},"validate_doc_update":"truie"}]
    }
  }

}

object SohvaServer extends App {

  new SohvaServer(System.in, System.out).start

}