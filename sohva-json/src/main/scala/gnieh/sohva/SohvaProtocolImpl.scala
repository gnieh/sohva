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

import scala.reflect.macros.Context

import scala.language.experimental.macros

import spray.json._

trait CouchFormat[T] extends RootJsonFormat[T] {

  def _id(t: T): String
  def _rev(t: T): Option[String]
  def withRev(t: T, rev: Option[String]): T

}

trait CouchFormatImpl {

  def couchFormat[T]: CouchFormat[T] =
    macro CouchFormatImpl.couchFormat[T]

}

object CouchFormatImpl {

  def couchFormat[T: c.WeakTypeTag](c: Context): c.Expr[CouchFormat[T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]

    if(!tpe.typeSymbol.asClass.isCaseClass)
      c.abort(c.enclosingPosition, s"${tpe} is not a case class. `couchFormat' can only generate a RootJsonFormat[T] for case classes!")

    val methodNames = tpe.declarations.toList.collect {
      case method: MethodSymbol if method.isCaseAccessor => q"${method.name.toString}"
    }

    val attachments =
      tpe.member(newTermName("_attachments")) match {
        case NoSymbol => q"Map.empty[String, gnieh.sohva.Attachment]"
        case _        => q"v._attachments"
      }

    val withAttachments =
      tpe.member(newTermName("withAttachments")) match {
        case NoSymbol => q"v"
        case _        => q"v.withAttachments(atts)"
      }

    c.Expr[CouchFormat[T]](q"""new gnieh.sohva.CouchFormat[$tpe] {

      val inner = jsonFormat(${tpe.typeSymbol.companionSymbol}, ..$methodNames)
      def _id(v: $tpe) = v._id
      def _rev(v: $tpe) = v._rev
      def _attachments(v: $tpe) = $attachments
      def withRev(v: $tpe, rev: Option[String]) = {
        v._rev = rev
        v
      }
      def withAttachments(v: $tpe, atts: Map[String, gnieh.sohva.Attachment]) =
        $withAttachments

      def read(json: spray.json.JsValue) = {
        val base = inner.read(json)
        json match {
          case spray.json.JsObject(fields) =>
            val base1 = fields.get("_rev") match {
              case Some(spray.json.JsString(rev)) => withRev(base, Some(rev))
              case None                           => withRev(base, None)
              case _                              => base
            }
            fields.get("_attachments") match {
              case Some(atts @ spray.json.JsObject(_)) => withAttachments(base1, atts.convertTo[Map[String, gnieh.sohva.Attachment]])
              case _                                   => base1
            }
          case _ =>
            base
        }
      }

      def write(v: $tpe) = {
        inner.write(v) match {
          case spray.json.JsObject(fields) =>
            val fields1 = fields + ("_id" -> spray.json.JsString(_id(v)))
            val fields2 = _rev(v) match {
              case Some(r) => fields1 + ("_rev" -> spray.json.JsString(r))
              case None    => fields1
            }
            val fields3 =
              if(_attachments(v).isEmpty)
                fields2
              else
                fields2 + ("_attachments" -> spray.json.JsObject(_attachments(v).mapValues(att =>
                    gnieh.sohva.SohvaProtocol.attachmentFormat.write(att))))
            spray.json.JsObject(fields3)
          case json =>
            json
        }

      }

    }""")
  }

}
