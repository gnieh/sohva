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

import strategy._

import dispatch._
import Defaults._

import java.net.URL

/** A replicator database that allows people to manage replications:
 *   - start replication
 *   - cancel or stop replications
 *   - list current replications
 *
 *  @author Lucas Satabin
 */
class Replicator(name: String, couch: CouchDB, credit: Int, strategy: Strategy)
  extends Database(name, couch, credit, strategy) {

  /** Starts a new replication from `source` to `target`. if a replication
   *  task already exists for the same source and target, the document is added
   *  but the replication is not started again. The result only contains the identifier
   *  of the actual replication task, not its state.
   */
  def start(replication: Replication): Result[Option[Replication]] =
    saveDoc(replication)

  /** Stops the replication identified by the given replication document id.
   *  if the identifier does not describe the document that started the replication,
   *  it is deleted from the replicator database, but the replication task is not stopped.
   *  It returns `true` only if the replication was actually stopped, `false` otherwise.
   */
  def stop(id: String): Result[Boolean] =
    for {
      repl <- getDocById[Replication](id).right
      ok <- deleteReplication(repl).right
    } yield ok

  private def deleteReplication(repl: Option[Replication]) = repl match {
    case Some(r) =>
      for {
        ok <- deleteDoc(r).right
        // is this the original document that started the replication task?
      } yield r._replication_state.isDefined && ok
    case None =>
      Future.successful(Right(false))
  }

}

/** A Reference to a database.
 *
 *  @author Lucas Satabin
 */
abstract class DbRef(val string: String)

/** A Reference to a local database identified by its name.
 *
 *  @author Lucas Satabin
 */
case class LocalDb(name: String) extends DbRef(name)

/** A Reference to a remote database identified by its url.
 *
 *  @author Lucas Satabin
 */
case class RemoteDb(url: URL) extends DbRef(url.toString)

/** A replication document contains information about a particular replication
 *  process (continuous or not, ...)
 *
 *  @author Lucas Satabin
 */
case class Replication(_id: String,
                       source: DbRef,
                       target: DbRef,
                       continuous: Option[Boolean] = None,
                       create_target: Option[Boolean] = None,
                       _replication_id: Option[String] = None,
                       _replication_state: Option[String] = None,
                       _replication_state_time: Option[String] = None,
                       doc_ids: List[String] = Nil,
                       user_ctx: Option[UserCtx] = None,
                       _rev: Option[String] = None)
