/*
* This file is part of the sohva project.
* Copyright (c) 2017 Lucas Satabin
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

import spray.json._

/** start sequence of a change stream. */
sealed trait Since {
  def option: Option[String]
}
/** Start at the given update sequence. */
final case class UpdateSequence(seq: JsValue) extends Since {
  def option = Some(CompactPrinter(seq))
}
/** Start with changes from now on. */
case object Now extends Since {
  def option = Some("now")
}
/** Start at the beginning of time. */
case object Origin extends Since {
  def option = None
}
