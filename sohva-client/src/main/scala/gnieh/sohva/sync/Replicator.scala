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
package sync

import strategy._

import gnieh.sohva.async.{
  Replicator => AReplicator
}

import java.net.URL

/** A replicator database that allows people to manage replications:
 *   - start replication
 *   - cancel or stop replications
 *   - list current replications
 *
 *  @author Lucas Satabin
 */
class Replicator(wrapped: AReplicator)
    extends Database(wrapped) with gnieh.sohva.Replicator[Identity] {

  def start(replication: Replication): Replication =
    synced(wrapped.start(replication))

  def stop(id: String): Boolean =
    synced(wrapped.stop(id))

}
