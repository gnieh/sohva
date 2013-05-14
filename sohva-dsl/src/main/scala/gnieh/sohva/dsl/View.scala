/*
* This file is part of the sohva project.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*couch.http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package gnieh.sohva
package dsl

import java.io.{
  StringWriter,
  PrintWriter
}

object View {

  /** Compiles the given view written in the Sohva DSL to a couch view */
  def compile[Key: Manifest, Mapped: Manifest](view: JSCouchViewExp[Key, Mapped]): ViewDoc = {
    val codegen = new JSGenCouchView[Key, Mapped] {
      val IR: view.type = view
    }
    // compile the map function
    val mapWriter = new StringWriter
    codegen.emitSource0(() => view.map, "map", new PrintWriter(mapWriter))
    val mapFun = "(" + mapWriter.toString + ")()"
    // compile the reduce function
    val reduceFun = view.reduce match {
      case view.`undefined` =>
        None
      case _ =>
        val reduceWriter = new StringWriter
        codegen.emitSource0(() => view.reduce, "reduce", new PrintWriter(reduceWriter))
        Some("(" + reduceWriter.toString + ")()")
    }
    ViewDoc(mapFun, reduceFun)
  }

}
