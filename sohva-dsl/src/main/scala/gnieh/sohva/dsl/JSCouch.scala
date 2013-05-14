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

/** CouchDB methods that are available everywhere on the CouchDB server
 *
 *  @author Lucas Satabin
 */
trait JSCouch extends JS with JSJson with Casts {

  def isArray(obj: Rep[Any]): Rep[Boolean]

  def log[A](msg: Rep[A]): Rep[Unit]

  def sum[A: Numeric: Manifest](array: Rep[Array[A]]): Rep[A]

  def toJSON(obj: Rep[Any]): Rep[String]

}

trait JSCouchExp extends JSExp with JSCouch with JSJsonExp with CastsCheckedExp { 
  case class IsArray(obj: Rep[Any]) extends Def[Boolean]
  case class Log[A](s: Rep[A]) extends Def[Unit]
  case class Sum[A](array: Rep[Array[A]]) extends Def[A]
  case class ToJSON(obj: Rep[Any]) extends Def[String]

  def isArray(obj: Rep[Any]): Rep[Boolean] = reflectEffect(IsArray(obj))
  def log[A](s: Rep[A]): Rep[Unit] = reflectEffect(Log(s))
  def sum[A: Numeric: Manifest](a: Rep[Array[A]]): Rep[A] = reflectEffect(Sum(a))
  def toJSON(obj: Rep[Any]): Rep[String] = reflectEffect(ToJSON(obj))

}

trait JSGenCouch extends JSGen with JSGenStruct with JSGenProxy with QuoteGen with GenCastChecked {
  val IR: JSCouchExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case IsArray(obj) => emitValDef(sym, q"isArray($obj)")
    case Log(s)       => emitValDef(sym, q"log($s)")
    case Sum(a)       => emitValDef(sym, q"sum($a)")
    case ToJSON(obj)  => emitValDef(sym, q"toJSON($obj)")
    case _            => super.emitNode(sym, rhs)
  }
}
