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

import gnieh.sohva.async.{
  CouchClient => ACouchClient
}

/** A CouchDB instance.
 *  Allows users to access the different databases and instance information.
 *  This is the key class to start with when one wants to work with couchdb.
 *  Through this one you will get access to the sessions and anonymous access
 *  to databases.
 *
 *  @author Lucas Satabin
 *
 */
class CouchClient private[sync] (wrapped: ACouchClient) extends CouchDB(wrapped) with gnieh.sohva.CouchClient[Identity] {

  def this(host: String = "localhost",
    port: Int = 5984,
    ssl: Boolean = false,
    version: String = "1.4") =
    this(new ACouchClient(host, port, ssl, version))

  def startSession =
    new CouchSession(wrapped.startSession)

  def shutdown =
    wrapped.shutdown

}
