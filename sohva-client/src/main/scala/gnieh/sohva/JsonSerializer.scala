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

/** The interface for the Json serializer/deserializer.
 *  Allows for changing the implementation and using your favorite
 *  json library.
 *
 *  @author Lucas Satabin
 *
 */
abstract class JsonSerializer {

  /** Serializes the given object to a json string */
  def toJson[T: Manifest](obj: T): String

  /** Deserializes from the given json string to the object if possible or throws a
   *  `SohvaJsonExpcetion`
   */
  def fromJson[T: Manifest](json: String): T

  /** Deserializes from the given json string to the object if possible or returns
   *  `None` otherwise
   */
  def fromJsonOpt[T: Manifest](json: String): Option[T]

}

case class SohvaJsonException(msg: String, inner: Exception) extends Exception(msg, inner)

object LiftJsonSerializer extends JsonSerializer {

  import net.liftweb.json._
  import java.text.SimpleDateFormat

  implicit private val formats = new DefaultFormats {
    override def dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS")
  }

  def toJson[T: Manifest](obj: T) =
    pretty(render(Extraction.decompose(obj)))

  def fromJson[T: Manifest](json: String) =
    try {
      Extraction.extract(JsonParser.parse(json))
    } catch {
      case e: Exception =>
        throw SohvaJsonException("Unable to extract from the json string", e)
    }

  def fromJsonOpt[T: Manifest](json: String) =
    Extraction.extractOpt(JsonParser.parse(json))

}