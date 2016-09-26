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

sealed trait Selector

final case class Field(name: String, selector: Selector) extends Selector

sealed trait Operator extends Selector

sealed trait Combination extends Operator

final case class And(selectors: Vector[Selector]) extends Combination
final case class Or(selectors: Vector[Selector]) extends Combination
final case class Not(selector: Selector) extends Combination
final case class Nor(selectors: Vector[Selector]) extends Combination
final case class All(values: Vector[JsValue]) extends Combination
final case class ElemMatch(selector: Selector) extends Combination

sealed trait Condition extends Operator

final case class Eq(value: JsValue) extends Condition
final case class Ne(value: JsValue) extends Condition
final case class Lt(value: JsValue) extends Condition
final case class Lte(value: JsValue) extends Condition
final case class Gt(value: JsValue) extends Condition
final case class Gte(value: JsValue) extends Condition

final case class Exists(exists: Boolean) extends Condition
final case class Type(tpe: ObjectType) extends Condition

final case class In(values: Vector[JsValue]) extends Condition
final case class Nin(values: Vector[JsValue]) extends Condition
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

