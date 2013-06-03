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

/** Code written in the Sohva-DSL must be compiled to Sohva representations.
 *   - views are compiled to Sohva `ViewDoc`
 *   - designs are compiled to Sohva `DesignDoc`
 *
 *  @author Lucas Satabin
 */
object DSL {

  /** Compiles the given view written in the Sohva DSL to a couch view */
  def compile[Key: Manifest, Mapped: Manifest](view: JSView[Key, Mapped]): ViewDoc = {
    val codegen = new JSGenCouchView[Key, Mapped] {
      val IR: view.type = view
    }
    // compile the map function
    val mapWriter = new StringWriter
    codegen.emitExecution(view.map, new PrintWriter(mapWriter))
    val mapFun = mapWriter.toString
    // compile the reduce function
    val reduceFun = view.reduce match {
      case view.`undefined` =>
        None
      case view.Builtin(name) =>
        Some(s""""$name"""")
      case _ =>
        val reduceWriter = new StringWriter
        codegen.emitExecution(view.reduce, new PrintWriter(reduceWriter))
        Some(reduceWriter.toString)
    }
    ViewDoc(mapFun, reduceFun)
  }

  def compile(design: JSDesign): DesignDoc = {
    val codegen = new JSGenCouchDesign {
      val IR: design.type = design
    }

    // compile the view libraries
    val view_libs = design.view_libs.mapValues { lib =>
      val writer = new StringWriter
      codegen.emitExecution(lib, new PrintWriter(writer))
      writer.toString
    }

    // compile the views
    val views = design.views.mapValues(view => compile(view.view.asInstanceOf[JSView[view.K, view.M]])(view.mK, view.mM)).toMap

    // compile the libraries
    val libs = design.libs.mapValues { lib =>
      val writer = new StringWriter
      codegen.emitExecution(lib, new PrintWriter(writer))
      writer.toString
    }.toMap

    // compile the validate function
    val validate_doc_update = design.validate_doc_update match {
      case design.`undefined` =>
        None
      case _ =>
        val validateWriter = new StringWriter
        codegen.emitExecution(design.validate_doc_update, new PrintWriter(validateWriter))
        Some(validateWriter.toString)
    }
    // compile the sho functions
    val shows = design.shows.mapValues { show =>
      val writer = new StringWriter
      codegen.emitExecution(show, new PrintWriter(writer))
      writer.toString
    }.toMap

    // compile the lists functions
    val lists = design.lists.mapValues { list =>
      val writer = new StringWriter
      codegen.emitExecution(list, new PrintWriter(writer))
      writer.toString
    }.toMap

    // compile the filters functions
    val filters = design.filters.mapValues { filter =>
      val writer = new StringWriter
      codegen.emitExecution(filter, new PrintWriter(writer))
      writer.toString
    }.toMap

    // compile the updates functions
    val updates = design.updates.mapValues { update =>
      val writer = new StringWriter
      codegen.emitExecution(update, new PrintWriter(writer))
      writer.toString
    }.toMap

    DesignDoc(design._id, "javascript", views, validate_doc_update, updates, filters, shows, lists)

  }

}
