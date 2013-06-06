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

import scala.collection.mutable.Map
import scala.virtualization.lms.common._

import scala.js._
import language._
import gen.QuoteGen
import gen.js._
import exp._

trait JSCouchDesign extends JSCouch {

  /** Extracts the next row from a related view result */
  def getRow(): Rep[Any]

  /** Registers callable handler for specified MIME key. */
  def provides(key: Rep[String], handler: Rep[() => Record]): Rep[Record]

  /** Registers list of MIME types by associated key */
  def registerType(key: Rep[String], mime: Rep[String], mimes: Rep[String]*): Rep[Unit]

  /** Sends a single string chunk in response. */
  def send(chunk: Rep[String]): Rep[Unit]

  /** Initiates chunked response */
  def start(resp: Record): Rep[Unit]

}

/** A couchDB design documents consists of several parts:
 *   - the (possibly empty) list of views
 *      - a dictionary of views that can be queried
 *      - an optional special `lib` view containing common code
 *   - an optional validation function
 *   - the (possibly empty) list of `show` functions
 *   - the (possibly empty) list of `list` functions
 *   - the (possibly empty) list of `filter` functions
 *
 *  @author Lucas Satabin
 */
trait JSDesign extends JSCouchExp {

  private[dsl] val views = Map.empty[String, ViewManifest[_, _]]

  private[dsl] val view_libs = Map.empty[String, Rep[Any]]

  private[dsl] val shows = Map.empty[String, Rep[((Doc, Request)) => Record]]

  private[dsl] val lists = Map.empty[String, Rep[((Head, Request)) => String]]

  private[dsl] val filters = Map.empty[String, Rep[((Doc)) => Boolean]]

  private[dsl] val updates =  Map.empty[String, Rep[((Doc, Request)) => (Doc, Record)]]

  private[dsl] val libs = Map.empty[String, Rep[Any]]

  case object GetRow extends Def[Any]
  case class Provides(key: Rep[String], handler: Rep[() => Record]) extends Def[Record]
  case class RegisterType(key: Rep[String], mime: List[Rep[String]]) extends Def[Unit]
  case class Send(chunk: Rep[String]) extends Def[Unit]
  case class Start(resp: Rep[Record]) extends Def[Unit]

  val _id: String

  def getRow(): Rep[Any] =
    GetRow

  def provides(key: Rep[String], handler: Rep[() => Record]): Rep[Record] =
    Provides(key, handler)

  def registerType(key: Rep[String], mime: Rep[String], mimes: Rep[String]*): Rep[Unit] =
    RegisterType(key, mime :: mimes.toList)

  def send(chunk: Rep[String]): Rep[Unit] =
    Send(chunk)

  def start(resp: Rep[Record]): Rep[Unit] =
    Start(resp)

  /** Add a new view with the given name */
  def view[Key: Manifest, Mapped: Manifest](name: String)(view: JSView[Key, Mapped]): Unit =
    views(name) = new ViewManifest(view)

  /** Add a new view library */
  def view_lib(name: String)(lib: Exp[Any]): Unit =
    view_libs(name) = lib

  /** Add a show function */
  def show(name: String)(show: Exp[((Doc, Request)) => Record]): Unit =
    shows(name) = show

  /** Add a list function */
  def list(name: String)(list: Exp[((Head, Request)) => String]): Unit =
    lists(name) = list

  /** Add a filter function */
  def filter(name: String)(filter: Exp[((Doc)) => Boolean]): Unit =
    filters(name) = filter

  /** Add an update function */
  def update(name: String)(update: Exp[((Doc, Request)) => (Doc, Record)]): Unit =
    updates(name) = update

  /** Add a library */
  def lib(name: String)(lib: Exp[Any]): Unit =
    libs(name) = lib

  val validate_doc_update: Exp[((Doc, Doc, UserCtx)) => Boolean] =
    undefined

}

trait JSGenCouchDesign extends JSGenCouch {
  val IR: JSDesign
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case GetRow                   => emitValDef(sym, "getRow()")
    case Provides(key, handler)   => emitValDef(sym, q"provides($key, $handler)")
    case RegisterType(key, mimes) =>
      val mimes1 = mimes.map(quote).mkString(", ")
      emitValDef(sym, q"""registerType($key, $mimes1)""")
    case Send(chunk)              => emitValDef(sym, q"send($chunk)")
    case Start(resp)              => emitValDef(sym, q"start($resp)")
    case _                        => super.emitNode(sym, rhs)
  }

}

class ViewManifest[Key: Manifest, Mapped: Manifest](val view: JSView[Key, Mapped]) {
  type K = Key
  type M = Mapped
  val mK: Manifest[K] = manifest[Key]
  val mM: Manifest[M] = manifest[Mapped]
}
