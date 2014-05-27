/* Licensed under the Apache License, Version 2.0 (the "License");
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
package entities
package impl

import net.liftweb.json.JValue

/** Reprensetation of an entity in the CouchDB database.
 *  In CouchDB an entity is nothing but an identifier and an optional tag.
 *  Components will be stored in their own document, managed views may be used to
 *  aggregate several components at once.
 *
 *  @author Lucas Satabin
 */
case class CouchEntity(_id: String, tag: Option[String]) extends IdRev
