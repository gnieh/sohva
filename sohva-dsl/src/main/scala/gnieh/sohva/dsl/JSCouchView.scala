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

import scala.js._
import scala.virtualization.lms.common._

trait JSCouchView[Key, Mapped] extends JSCouch with JSFunctions {

  trait Doc {
    var _id: Rep[String]
    var _rev: Rep[String]
  }
  implicit def repToDoc(x: Rep[Doc]): Doc =
    repProxy[Doc](x)

  def emit(key: Rep[Key], value: Rep[Mapped]): Rep[Unit]

}

trait JSCouchViewExp[Key, Mapped] extends JSCouchView[Key, Mapped] with JSCouchExp with JSFunctionsExp {

  case class Emit(key: Rep[Key], value: Rep[Mapped]) extends Def[Unit]

  def emit(key: Rep[Key], value: Rep[Mapped]): Rep[Unit] =
    reflectEffect(Emit(key, value))

  val map: Rep[Doc => Unit]

  val reduce: Rep[((Array[(String, Key)], Array[Mapped], Boolean)) => Any] =
    undefined
}

trait JSGenCouchView[Key, Mapped] extends JSGenCouch {
  val IR: JSCouchViewExp[Key, Mapped]
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case Emit(key, value) => emitValDef(sym, q"emit($key, $value)")
    case _                => super.emitNode(sym, rhs)
  }

}
