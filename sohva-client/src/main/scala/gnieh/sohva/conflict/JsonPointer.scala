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
package conflict

import scala.annotation.tailrec

import net.liftweb.json._

/** Thrown whenever a problem is encountered when parsing or evaluating a Json Pointer */
class PointerException(msg: String) extends Exception(msg)

/** A class to work with Json pointers according to http://tools.ietf.org/html/draft-ietf-appsawg-json-pointer-09 */
class JsonPointer(errorHandler: PartialFunction[(JValue, String), JValue] = allError) {

  private def handle(value: JValue, pointer: String): JValue =
    errorHandler.orElse(allError)(value, pointer)

  /** Parses a JSON pointer and returns the resolved path. */
  def parse(input: String): Pointer = {
    if(input == null || input.isEmpty)
      // shortcut if input is empty
      Nil
    else if(!input.startsWith("/")) {
      // a pointer MUST start with a '/'
      throw new PointerException("A JSON pointer must start with '/'")
    } else {
      // first gets the different parts of the pointer
      val parts = input.split("/")
        // easier to work with lists in scala
        .toList
        // the first element is always empty as the path starts with a '/'
        .drop(1)
      // check that an occurrence of '~' is followed by '0' or '1'
      if(parts.exists(_.replace("~0", "").replace("~1", "").contains("~"))) {
        throw new PointerException("Occurrences of '~' must be followed by '0' or '1'")
      } else {
        parts
          // transform the occurrences of '~1' into occurrences of '/'
          .map(_.replace("~1", "/"))
          // transform the occurrences of '~0' into occurrences of '~'
          .map(_.replace("~0", "~"))
      }
    }
  }

  @inline
  def evaluate(value: String, path: String): JValue =
    evaluate(JsonParser.parse(value), parse(path))

  /** Evaluates the given path in the given JSON object.
   *  Upon missing elements in value, the handler is called with the current value and element */
  @tailrec
  final def evaluate(value: JValue, path: Pointer): JValue = (value, path) match {
    case (obj: JObject, elem :: tl) =>
      evaluate(obj \ elem, tl)
    case (JArray(arr), (p @ IntIndex(idx)) :: tl) =>
      if(idx >= arr.size)
        // we know (by construction) that the index is greater or equal to zero
        evaluate(handle(value, p), tl)
      else
        evaluate(arr(idx), tl)
    case (arr: JArray, "-" :: tl) =>
      evaluate(handle(value, "-"), tl)
    case (_, Nil) =>
      value
    case (_, elem :: tl) =>
      evaluate(handle(value, elem), tl)
  }

}

object IntIndex {
  private[this] val int = "(0|[1-9][0-9]*)".r
  def unapply(s: String): Option[Int] =
    s match {
      case int(i) => Some(i.toInt)
      case _      => None
    }
}
