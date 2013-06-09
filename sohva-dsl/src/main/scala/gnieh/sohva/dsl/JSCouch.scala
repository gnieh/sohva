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

import scala.js._
import language._
import gen.QuoteGen
import gen.js._
import exp._

/** CouchDB methods that are available everywhere on the CouchDB server
 *
 *  @author Lucas Satabin
 */
trait JSCouch extends JS with JSJson with Structs with JSMaps with Casts {

  /** A CouchDB document is a Json object with at least an `_id` and a `_rev` field
   *
   *  @author Lucas Satabin
   */
  trait Doc {
    var _id: Rep[String]
    var _rev: Rep[String]
  }
  implicit def repToDoc(x: Rep[Doc]): Doc =
    repProxy[Doc](x)

  /** A user context which consists of the database name of the current context,
   *  a (possibly null) name, and a list of roles
   *
   *  @author Lucas Satabin
   */
  trait UserCtx {
    var db: Rep[String]
    var name: Rep[String]
    var roles: Rep[List[String]]
  }
  implicit def repToUserCtx(x: Rep[UserCtx]): UserCtx =
    repProxy[UserCtx](x)

  /* A database security object contains two lists of users/roles:
   *  - the database members
   *  - the database admins
   */
  trait SecObject {
    var admins: Rep[UsersRoles]
    var members: Rep[UsersRoles]
  }
  implicit def repToSecObject(x: Rep[SecObject]): SecObject =
    repProxy[SecObject](x)

  trait UsersRoles {
    var names: Rep[List[String]]
    var roles: Rep[List[String]]
  }
  implicit def repToUsersRoles(x: Rep[UsersRoles]): UsersRoles =
    repProxy[UsersRoles](x)

  /** Database information
   *
   *  @author Lucas Satabin
   */
  trait DbInfo {
    var committed_update_seq: Rep[Int]
    var compact_running: Rep[Boolean]
    var data_size: Rep[Long]
    var db_name: Rep[String]
    var disk_format_version: Rep[Int]
    var disk_size: Rep[Long]
    var doc_count: Rep[Int]
    var doc_del_count: Rep[Int]
    var instance_start_time: Rep[String]
    var purge_seq: Rep[Long]
    var update_seq: Rep[Long]
  }
  implicit def repToDbInfo(x: Rep[DbInfo]): DbInfo =
    repProxy[DbInfo](x)

  /** Database head information
   *
   *  @author Lucas Satabin
   */
  trait Head {
    var total_rows: Int
    var offset: Int
  }
  implicit def repToHead(x: Rep[Head]): Head =
    repProxy[Head](x)

  /** The request object containinf the request data
   *
   *  @author Lucas Satabin
   */
  trait Request {
    var body: Rep[String]
    var cookie: Rep[Map[String, String]]
    var form: Rep[Map[String, String]]
    var headers: Rep[Map[String, String]]
    var id: Rep[String]
    var info: Rep[DbInfo]
    var method: Rep[String]
    var path: Rep[List[String]]
    var peer: Rep[String]
    var query: Rep[Map[String, String]]
    var requested_path: Rep[List[String]]
    var raw_path: Rep[String]
    var secObj: Rep[SecObject]
    var userCtx: Rep[UserCtx]
    var uuid: Rep[String]
  }
  implicit def repToRequest(x: Rep[Request]): Request =
    repProxy[Request](x)

  def doFunction[A:Manifest,B:Manifest](fun: Rep[A] => Rep[B]): Rep[A => B]
  def function[A:Manifest,B:Manifest](f: Rep[A] => Rep[B]): Rep[A=>B] = doFunction(f)
  def function[A1:Manifest,A2:Manifest,B:Manifest](f: (Rep[A1], Rep[A2]) => Rep[B]): Rep[((A1,A2))=>B] =
    function((t: Rep[(A1,A2)]) => f(tuple2_get1(t), tuple2_get2(t)))
  def function[A1:Manifest,A2:Manifest,A3:Manifest,B:Manifest](f: (Rep[A1], Rep[A2], Rep[A3]) => Rep[B]): Rep[((A1,A2,A3))=>B] =
    function((t: Rep[(A1,A2,A3)]) => f(tuple3_get1(t), tuple3_get2(t), tuple3_get3(t)))

  /** Checks whether the object is an array */
  def isArray(obj: Rep[Any]): Rep[Boolean]

  /** Logs the message */
  def log[A](msg: Rep[A]): Rep[Unit]

  /** Sums the numeric values in the array */
  def sum[A: Numeric: Manifest](array: Rep[List[A]]): Rep[A]

  /** Converts the object to its Json representation (alias for `JSON.stringify(obj)` */
  def toJSON(obj: Rep[Any]): Rep[String]

  /** Imports the CommonJS module */
  def require[A](path: Rep[String]): Rep[A]

}

trait JSCouchExp extends JSExp with JSCouch with JSJsonExp with StructExp with JSMapsExp with CastsCheckedExp {
  case class IsArray(obj: Rep[Any]) extends Def[Boolean]
  case class Log[A](s: Rep[A]) extends Def[Unit]
  case class Sum[A](array: Rep[List[A]]) extends Def[A]
  case class ToJSON(obj: Rep[Any]) extends Def[String]
  case class Require[A](path: Rep[String]) extends Def[A]
  case class AnonFunction[A: Manifest,B: Manifest](param: Exp[A], body: Block[B]) extends Exp[A => B]

  def doFunction[A:Manifest,B:Manifest](f: Exp[A] => Exp[B]) : Exp[A => B] = {
    val x = unboxedFresh[A]
    val y = reifyEffects(f(x)) // unfold completely at the definition site.
    AnonFunction(x, y)
  }

  def isArray(obj: Rep[Any]): Rep[Boolean] = reflectEffect(IsArray(obj))
  def log[A](s: Rep[A]): Rep[Unit] = reflectEffect(Log(s))
  def sum[A: Numeric: Manifest](a: Rep[List[A]]): Rep[A] = reflectEffect(Sum(a))
  def toJSON(obj: Rep[Any]): Rep[String] = reflectEffect(ToJSON(obj))
  def require[A](path: Rep[String]): Rep[A] = reflectEffect(Require(path))

}

trait JSGenCouch extends GenJS with JSGenJson with GenStruct with GenJSMaps with QuoteGen {
  val IR: JSCouchExp
  import IR._

  import java.io._

  override def tupleAsArrays = true

  val substs = scala.collection.mutable.Map.empty[Sym[_], Sym[_]]

  def funBody[B: Manifest](body: Block[B]): String = {
    val block = new StringWriter
    withStream(new PrintWriter(block)) {
      emitBlock(body)
      val result = getBlockResult((body))
      if (!(result.tp <:< manifest[Unit]))
        stream.print(q"return $result\n")
    }
    block.toString
  }

  override def quote(x: Exp[Any]): String = x match {
    case sym: Sym[_] if substs.contains(sym) =>
      quote(substs(sym))
    case AnonFunction(UnboxedTuple(ps), b) =>
      val p = ps.map(quote).mkString(",")
      q"function($p) {\n${funBody(b)}}"
    case AnonFunction(p, b)  => q"function($p) {\n${funBody(b)}}"
    case _                   => super.quote(x)
  }

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case Reify(alias: Sym[_], _, _) => substs(sym) = alias
    case Cast(x, m)    => emitValDef(sym, quote(x))
    case IsArray(obj)  => emitValDef(sym, q"isArray($obj)")
    case Log(s)        => emitValDef(sym, q"log($s)")
    case Sum(a)        => emitValDef(sym, q"sum($a)")
    case ToJSON(obj)   => emitValDef(sym, q"toJSON($obj)")
    case Require(path) => emitValDef(sym, q"require($path)")
    case _             => super.emitNode(sym, rhs)
  }
}
