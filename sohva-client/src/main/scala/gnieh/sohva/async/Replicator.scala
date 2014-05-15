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

import strategy._

import java.net.URL

import scala.concurrent.Future

/** A replicator database that allows people to manage replications:
 *   - start replication
 *   - cancel or stop replications
 *   - list current replications
 *
 *  @author Lucas Satabin
 */
class Replicator(name: String, couch: CouchDB, credit: Int, strategy: Strategy)
    extends Database(name, couch, credit, strategy) with gnieh.sohva.Replicator[Future] {

  def start(replication: Replication): Future[Replication] =
    saveDoc(replication)

  def stop(id: String): Future[Boolean] =
    for {
      repl <- getDocById[Replication](id)
      ok <- deleteReplication(repl)
    } yield ok

  private def deleteReplication(repl: Option[Replication]) = repl match {
    case Some(r) =>
      for {
        ok <- deleteDoc(r)
        // is this the original document that started the replication task?
      } yield r._replication_state.isDefined && ok
    case None =>
      Future.successful(false)
  }

}
