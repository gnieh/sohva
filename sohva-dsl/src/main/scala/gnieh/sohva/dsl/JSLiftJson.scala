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

import scala.js._

import net.liftweb.json._

trait JSLiftJsonExp extends JSExp {

  trait JValueOps {

    def \(name: Rep[String]): Rep[JValue]

    def \\(name: Rep[String]): Rep[JValue]

    def ++(that: Rep[JValue]):Rep[JValue]

    def extract[A](implicit formats: Formats, mf: Manifest[A]): Rep[A]

    def extractOpt[A](implicit formats: Formats, mf: Manifest[A]): Option[A]

  }

}
