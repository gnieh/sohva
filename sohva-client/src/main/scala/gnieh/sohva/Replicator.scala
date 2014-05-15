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

import strategy._

import java.net.URL

/** A replicator database that allows people to manage replications:
 *   - start replication
 *   - cancel or stop replications
 *   - list current replications
 *
 *  @author Lucas Satabin
 */
trait Replicator[Result[_]] extends Database[Result] {

  /** Starts a new replication from `source` to `target`. if a replication
   *  task already exists for the same source and target, the document is added
   *  but the replication is not started again. The result only contains the identifier
   *  of the actual replication task, not its state.
   */
  def start(replication: Replication): Result[Replication]

  /** Stops the replication identified by the given replication document id.
   *  if the identifier does not describe the document that started the replication,
   *  it is deleted from the replicator database, but the replication task is not stopped.
   *  It returns `true` only if the replication was actually stopped, `false` otherwise.
   */
  def stop(id: String): Result[Boolean]

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
case class Replication(
  _id: String,
  source: DbRef,
  target: DbRef,
  continuous: Option[Boolean] = None,
  create_target: Option[Boolean] = None,
  _replication_id: Option[String] = None,
  _replication_state: Option[String] = None,
  _replication_state_time: Option[String] = None,
  doc_ids: List[String] = Nil,
  user_ctx: Option[UserCtx] = None) extends IdRev

