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

trait MangoProtocol extends DefaultJsonProtocol {

  implicit object sortProtocol extends JsonFormat[Sort] {
    def read(json: JsValue): Sort = json match {
      case JsString(field) => Asc(field)
      case JsObject(SingleMap((field, JsString("asc")))) => Asc(field)
      case JsObject(SingleMap((field, JsString("desc")))) => Desc(field)
      case _ => deserializationError(f"sort object expected but got $json")
    }

    def write(sort: Sort): JsValue = sort match {
      case Asc(field)  => JsString(field)
      case Desc(field) => JsObject(Map(field -> JsString("desc")))
    }

  }

  implicit object objectTypeFormat extends JsonFormat[ObjectType] {
    def read(json: JsValue): ObjectType = json match {
      case JsString("null")    => NullObject
      case JsString("boolean") => BooleanObject
      case JsString("number")  => NumberObject
      case JsString("string")  => StringObject
      case JsString("array")   => ArrayObject
      case JsString("object")  => ObjectObject
      case _                   => deserializationError(f"object type object expected but got $json")
    }
    def write(tpe: ObjectType): JsString =
      JsString(tpe.value)
  }

  implicit object selectorProtocol extends RootJsonFormat[Selector] {

    private def read(p: (String, JsValue)): Selector =
      p match {
        case ("$and", JsArray(sub)) =>
          And(sub.map(read))
        case ("$or", JsArray(sub)) =>
          Or(sub.map(read))
        case ("$not", sub) =>
          Not(read(sub))
        case ("$nor", JsArray(sub)) =>
          Nor(sub.map(read))
        case ("$all", JsArray(values)) =>
          All(values)
        case ("$elemMatch", sub) =>
          ElemMatch(read(sub))
        case ("$lt", value) =>
          Lt(value)
        case ("$lte", value) =>
          Lte(value)
        case ("$eq", value) =>
          Eq(value)
        case ("$ne", value) =>
          Ne(value)
        case ("$gt", value) =>
          Gt(value)
        case ("$gte", value) =>
          Gte(value)
        case ("$exists", JsBoolean(e)) =>
          Exists(e)
        case ("$type", tpe) =>
          Type(objectTypeFormat.read(tpe))
        case ("$in", JsArray(values)) =>
          In(values)
        case ("$nin", JsArray(values)) =>
          Nin(values)
        case ("$size", JsNumber(s)) =>
          Size(s.toInt)
        case ("$mod", JsArray(Vector(JsNumber(divisor), JsNumber(remainder)))) =>
          Mod(divisor.toInt, remainder.toInt)
        case ("$regex", JsString(re)) =>
          Regex(re)
        case (name, value) =>
          Field(name, read(value))
      }

    def read(json: JsValue): Selector = json match {
      case JsObject(fields) if fields.size > 0 =>
        fields.map(read).toVector match {
          case Vector(sel) => sel
          case sels        => And(sels)
        }
      case _ =>
        Eq(json)
    }

    def write(selector: Selector): JsObject = selector match {
      case Empty            => JsObject()
      case Field(name, sub) => JsObject(Map(name -> write(sub)))
      case And(sub)         => JsObject(Map("$and" -> JsArray(sub.map(write).toVector)))
      case Or(sub)          => JsObject(Map("$or" -> JsArray(sub.map(write).toVector)))
      case Not(sub)         => JsObject(Map("$not" -> write(sub)))
      case Nor(sub)         => JsObject(Map("$nor" -> JsArray(sub.map(write).toVector)))
      case All(values)      => JsObject(Map("$all" -> JsArray(values.toVector)))
      case ElemMatch(sub)   => JsObject(Map("$elemMatch" -> write(sub)))
      case AllMatch(sub)    => JsObject(Map("$allMatch" -> write(sub)))
      case Eq(value)        => JsObject(Map("$eq" -> value))
      case Ne(value)        => JsObject(Map("$ne" -> value))
      case Lt(value)        => JsObject(Map("$lt" -> value))
      case Lte(value)       => JsObject(Map("$lte" -> value))
      case Gt(value)        => JsObject(Map("$gt" -> value))
      case Gte(value)       => JsObject(Map("$gte" -> value))
      case Exists(e)        => JsObject(Map("$exists" -> JsBoolean(e)))
      case Type(tpe)        => JsObject(Map("$type" -> JsString(tpe.value)))
      case In(values)       => JsObject(Map("$in" -> JsArray(values.toVector)))
      case Nin(values)      => JsObject(Map("$nin" -> JsArray(values.toVector)))
      case Size(s)          => JsObject(Map("$size" -> JsNumber(s)))
      case Mod(div, rem)    => JsObject(Map("$mod" -> JsArray(JsNumber(div), JsNumber(rem))))
      case Regex(re)        => JsObject(Map("$regex" -> JsString(re)))
    }
  }

  implicit val queryFormat = jsonFormat9(Query)

  implicit val indexCreationResultFormat = jsonFormat3(IndexCreationResult)

  implicit val defFormat = jsonFormat1(Def)

  implicit val indexDefFormat = jsonFormat4(IndexDef)

  implicit val indexInfoFormat = jsonFormat2(IndexInfo)

  implicit val queryRangeFormat = jsonFormat2(QueryRange)

  implicit val explanationFormat = jsonFormat8(Explanation)

  implicit def searchResultFormat[T: JsonFormat] = jsonFormat2(SearchResult[T])

}

object MangoProtocol extends MangoProtocol
