/*
 * This file is part of the sohva project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gnieh.sohva

import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.Unmarshaller
import spray.json._
import spray.http._

import scala.language.implicitConversions

trait SprayJsonSupport {

  implicit def sprayJsonUnmarshallerConverter[T](reader: JsonReader[T]) =
    sprayJsonUnmarshaller(reader)

  implicit def sprayJsonUnmarshaller[T: JsonReader] =
    Unmarshaller[T](MediaTypes.`application/json`) {
      case x: HttpEntity.NonEmpty =>
        val json = JsonParser(x.asString(defaultCharset = HttpCharsets.`UTF-8`))
        jsonReader[T].read(json)
    }

  implicit def sprayJsonMarshallerConverter[T](writer: JsonWriter[T])(implicit printer: JsonPrinter = PrettyPrinter): Marshaller[T] =
    sprayJsonMarshaller[T](writer, printer)

  implicit def sprayJsonMarshaller[T](implicit writer: JsonWriter[T], printer: JsonPrinter = PrettyPrinter) =
    Marshaller.delegate[T, String](ContentTypes.`application/json`.withCharset(HttpCharsets.`UTF-8`)) { value =>
      val json = writer.write(value)
      printer(json)
    }

}
