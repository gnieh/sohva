/*
* This file is part of the sohva project.
* Copyright (c) 2016 Lucas Satabin
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
package mango

import spray.json._

sealed abstract class Selector {

  def &&(that: Selector): Selector =
    (this, that) match {
      case (And(subs1), And(subs2)) => And(subs1 ++ subs2)
      case (And(subs), _)           => And(subs :+ that)
      case (_, And(subs))           => And(this +: subs)
      case (_, _)                   => And(Vector(this, that))
    }

  def ||(that: Selector): Selector =
    (this, that) match {
      case (Or(subs1), Or(subs2)) => Or(subs1 ++ subs2)
      case (Or(subs), _)          => Or(subs :+ that)
      case (_, Or(subs))          => Or(this +: subs)
      case (_, _)                 => Or(Vector(this, that))
    }

  def unary_! : Selector =
    this match {
      case Not(sub) => sub
      case Or(subs) => Nor(subs)
      case _        => Not(this)
    }

}

case object Empty extends Selector

final case class Field(name: String, selector: Selector) extends Selector

sealed trait Operator extends Selector

sealed trait Combination extends Operator

final case class And(selectors: Seq[Selector]) extends Combination
final case class Or(selectors: Seq[Selector]) extends Combination
final case class Not(selector: Selector) extends Combination
final case class Nor(selectors: Seq[Selector]) extends Combination
final case class All(values: Seq[JsValue]) extends Combination
final case class ElemMatch(selector: Selector) extends Combination
final case class AllMatch(selector: Selector) extends Combination

sealed trait Condition extends Operator

final case class Eq(value: JsValue) extends Condition
final case class Ne(value: JsValue) extends Condition
final case class Lt(value: JsValue) extends Condition
final case class Lte(value: JsValue) extends Condition
final case class Gt(value: JsValue) extends Condition
final case class Gte(value: JsValue) extends Condition

final case class Exists(exists: Boolean) extends Condition
final case class Type(tpe: ObjectType) extends Condition

final case class In(values: Seq[JsValue]) extends Condition
final case class Nin(values: Seq[JsValue]) extends Condition
final case class Size(size: Int) extends Condition

final case class Mod(divisor: Int, remainder: Int) extends Condition
final case class Regex(regex: String) extends Condition

sealed abstract class ObjectType(val value: String)
case object NullObject extends ObjectType("null")
case object BooleanObject extends ObjectType("boolean")
case object NumberObject extends ObjectType("number")
case object StringObject extends ObjectType("string")
case object ArrayObject extends ObjectType("array")
case object ObjectObject extends ObjectType("object")
object ObjectType {
  def apply(str: String): ObjectType =
    str match {
      case "null"    => NullObject
      case "boolean" => BooleanObject
      case "number"  => NumberObject
      case "string"  => StringObject
      case "array"   => ArrayObject
      case "object"  => ObjectObject
      case _         => throw new SohvaException(f"Unknown type $str")
    }
}

/** The base of a selector, it is one of the conditions on fields.  */
class SelectorBase private[mango] (field: Option[String]) {

  private def makeSelector(inner: Selector): Selector =
    field match {
      case Some(field) => Field(field, inner)
      case None        => inner
    }

  /** Creates a `$eq` condition. */
  def ===[T: JsonWriter](value: T): Selector =
    makeSelector(Eq(value.toJson))

  /** Creates a `$ne` condition. */
  def !==[T: JsonWriter](value: T): Selector =
    makeSelector(Ne(value.toJson))

  /** Creates a `$lt` condition. */
  def <[T: JsonWriter](value: T): Selector =
    makeSelector(Lt(value.toJson))

  /** Creates a `$lte` condition. */
  def <=[T: JsonWriter](value: T): Selector =
    makeSelector(Lte(value.toJson))

  /** Creates a `$lt` condition. */
  def >[T: JsonWriter](value: T): Selector =
    makeSelector(Gt(value.toJson))

  /** Creates a `$gte` condition. */
  def >=[T: JsonWriter](value: T): Selector =
    makeSelector(Gte(value.toJson))

  /** Creates a `$exists true` condition. */
  def exists: Selector =
    makeSelector(Exists(true))

  /** Creates a `$exists false` condition. */
  def doesNotExist: Selector =
    makeSelector(Exists(false))

  /** Creates a `$type` condition. */
  def hasType(tpe: String): Selector =
    makeSelector(Type(ObjectType(tpe)))

  /** Creates a `$in` condition. */
  def in[T: JsonWriter](values: Seq[T]): Selector =
    makeSelector(In(values.map(_.toJson)))

  /** Creates a `$nin` condition. */
  def notIn[T: JsonWriter](values: Seq[T]): Selector =
    makeSelector(Nin(values.map(_.toJson)))

  /** Creates a `$size` condition. */
  def hasSize(s: Int): Selector =
    makeSelector(Size(s))

  def %(d: Int): ModuloBase =
    new ModuloBase(field, d)

  /** Creates a `$regex` condition. */
  def matches(re: String): Selector =
    makeSelector(Regex(re))

  /** Creates a `$all` selector. */
  def containsAll[T: JsonWriter](values: Seq[T]): Selector =
    All(values.map(_.toJson))

  /** Creates a `$all` selector. */
  def containsAll[T: JsonWriter](v: T, values: T*): Selector =
    All((v +: values).map(_.toJson))

  /** Creates a `$elemMatch` selector. */
  def contains(sel: Selector): Selector =
    makeSelector(ElemMatch(sel))

  /** Creates a `$allMatch` selector. */
  def forall(sel: Selector): Selector =
    makeSelector(AllMatch(sel))

}

class ModuloBase private[mango] (field: Option[String], divisor: Int) {

  /** Creates a `$mod` condition. */
  def ===(r: Int): Selector =
    field match {
      case Some(field) => Field(field, Mod(divisor, r))
      case None        => Mod(divisor, r)
    }

}
