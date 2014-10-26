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

/** Mix in this trait with any object to have automatic management of the attachment fields
 *
 *  @author Lucas Satabin
 */
trait Attachments {

  var _attachments: Map[String, Attachment] = Map.empty

  /** Sets the attachments and returns this (modified) instance */
  def withAttachments(atts: Map[String, Attachment]): this.type = {
    _attachments = atts
    this
  }

}

final case class Attachment(content_type: String,
  revpos: Int,
  digest: String,
  length: Int,
  stub: Boolean,
  encoded_length: Option[Int] = None,
  encoding: Option[String] = None)

