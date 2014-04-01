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
package async

import net.liftweb.json._

import spray.http._
import spray.httpx.marshalling.{
  Marshaller,
  MarshallingContext
}

trait LiftMarshalling {

  implicit def formats: Formats

  implicit def jvalueMarshaller: Marshaller[JValue] =
    Marshaller.of[JValue](MediaTypes.`application/json`) { (value, contentType, ctx) =>
      ctx.marshalTo(HttpEntity(contentType, HttpData(pretty(render(value)), HttpCharsets.`UTF-8`)))
    }

}

