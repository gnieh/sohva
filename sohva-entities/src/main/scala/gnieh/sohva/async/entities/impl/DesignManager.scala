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
package async
package entities
package impl

import scala.concurrent._

import scalax.io.Resource

import org.slf4j.LoggerFactory

/** Manages the design document, create the views when not existing
 *
 *  @author Lucas Satabin
 */
private[entities] class DesignManager(database: Database) {

  import database.ec

  private val logger = LoggerFactory.getLogger(classOf[DesignManager])

  val design = database.design("sohva-entities")

  val components =
    view("components")

  val tags =
    view("tags")

  private def view(name: String): Future[View] = {
    val view = design.view(name)
    view.exists flatMap {
      case true =>
        // the design view exists
        Future.successful(view)

      case false =>
        if (logger.isDebugEnabled)
          logger.debug(s"Add unknown managed view $name.")
        // the view does not exist, create it
        // read the map function
        val map =
          Resource.fromInputStream(getClass.getResourceAsStream(s"/${name}_map.js")).string
        for {
          _ <- design.saveView(name, map, None)
        } yield view
    }
  }

}
