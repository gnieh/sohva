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

import scala.virtualization.lms.common._
import scala.virtualization.lms.internal.GenerationFailedException

import scala.js._
import language._
import gen.QuoteGen
import gen.js._
import exp._

import java.io.PrintWriter

/** Methods that are available in the context of views
 *
 *  @author Lucas Satabin
 */
trait JSCouchView[Key, Mapped] extends JSCouch {

  abstract class Stats[T: Numeric] {
    var sum: T
    var count: Int
    var min: T
    var max: T
    var sumsqr: T
  }

  /** Emits the value with the given key */
  def emit(key: Rep[Key], value: Rep[Mapped]): Rep[Unit]

  /** Built-in `_sum` reduce function (mapped values must be numbers) */
  def _sum(implicit mk: Manifest[Key], mm: Manifest[Mapped], num: Numeric[Mapped]):
    Rep[((Array[(String, Key)], Array[Mapped], Boolean)) => Mapped]

  /** Built-in `_count` reduce function */
  def _count(implicit mk: Manifest[Key], mm: Manifest[Mapped]):
    Rep[((Array[(String, Key)], Array[Mapped], Boolean)) => Int]

  /** Built-in `_stats` reduce function (mapped values must be numbers) */
  def _stats(implicit mk: Manifest[Key], mm: Manifest[Mapped], num: Numeric[Mapped]):
    Rep[((Array[(String, Key)], Array[Mapped], Boolean)) => Stats[Mapped]]

}

/** A view implemented in the Sohva-DSL must implement a `map` function and may have a `reduce` function
 *  with the given signatures.
 *
 *  @author Lucas Satabin
 */
trait JSView[Key, Mapped] extends JSCouchView[Key, Mapped] with JSCouchExp {

  type K = Key
  type M = Mapped

  case class Emit(key: Rep[Key], value: Rep[Mapped]) extends Def[Unit]
  case class BuiltinSum[K: Manifest, M: Manifest: Numeric]() extends Exp[((Array[(String, K)], Array[M], Boolean)) => M]
  case class BuiltinCount[K: Manifest, M: Manifest]() extends Exp[((Array[(String, K)], Array[M], Boolean)) => Int]
  case class BuiltinStats[K: Manifest, M: Manifest: Numeric]() extends Exp[((Array[(String, K)], Array[M], Boolean)) => Stats[M]]
  object Builtin {
    def unapply(x: Exp[Any]): Option[String] = x match {
      case BuiltinSum()   => Some("_sum")
      case BuiltinCount() => Some("_count")
      case BuiltinStats() => Some("_stats")
      case _              => None
    }
  }

  def emit(key: Rep[Key], value: Rep[Mapped]): Rep[Unit] =
    reflectEffect(Emit(key, value))

  def _sum(implicit mk: Manifest[Key], mm: Manifest[Mapped], num: Numeric[Mapped]):
    Rep[((Array[(String, Key)], Array[Mapped], Boolean)) => Mapped] =
      BuiltinSum[Key,Mapped]()

  def _count(implicit mk: Manifest[Key], mm: Manifest[Mapped]):
    Rep[((Array[(String, Key)], Array[Mapped], Boolean)) => Int] =
      BuiltinCount[Key,Mapped]()

  def _stats(implicit mk: Manifest[Key], mm: Manifest[Mapped], num: Numeric[Mapped]):
    Rep[((Array[(String, Key)], Array[Mapped], Boolean)) => Stats[Mapped]] =
      BuiltinStats[Key,Mapped]()

  /** The map function of this view */
  val map: Exp[Doc => Unit]

  /** The reduce function of this view. Override this if you need it */
  val reduce: Exp[((Array[(String, Key)], Array[Mapped], Boolean)) => Any] =
    undefined
}

trait JSGenCouchView[Key, Mapped] extends JSGenCouch {
  val IR: JSView[Key, Mapped]
  import IR._

  override def quote(x: Exp[Any]) = x match {
    case Builtin(name) =>
      throw new GenerationFailedException(s"built-in reduce function $name cannot be called directly")
    case _ =>
      super.quote(x)
  }

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case Emit(key, value) => emitValDef(sym, q"emit($key, $value)")
    case _                => super.emitNode(sym, rhs)
  }

}

