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
package strategy

import net.liftweb.json._

/** A strategy indicates how update conflict are resolved.
 *  It is used by the conflict resolver at each try and may be called
 *  several times with the same document if the document changed while the
 *  strategy was applied.
 *
 * @author Lucas Satabin
 */
trait Strategy {

  /** Applies the resolving strategy between the last known revision `baseDoc`
   *  (or `None` if the document did not exist before to the client knowledge),
   *  the last revision in the database `lastDoc` (or `None` if the document was deleted)
   *  and the document the client wants to save `currentDoc`.
   *  If no automatic merge could be found, then returns `None` */
  def apply(baseDoc: Option[JValue],
            lastDoc: Option[JValue],
            currentDoc: JValue): JValue
}
