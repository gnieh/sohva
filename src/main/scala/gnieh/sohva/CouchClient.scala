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

import dispatch._

import com.ning.http.client._

import net.liftweb.json._

/** A CouchDB instance.
 *  Allows users to access the different databases and instance information.
 *  This is the key class to start with when one wants to work with couchdb.
 *  Through this one you will get access to the sessions and anonymous access
 *  to databases.
 *
 *  @author Lucas Satabin
 *
 */
class CouchClient(val host: String = "localhost",
                  val port: Int = 5984,
                  val ssl: Boolean = false,
                  val version: String = "1.2") extends CouchDB {

  /** Starts a new session to with this client */
  def startSession =
    new CouchSession(this)

  /** Shuts down this instance of couchdb client. */
  def shutdown =
    _http.shutdown

  // ========== internals ==========

  private[sohva] val _http = new Http

  // the base request to this couch instance
  private[sohva] def request =
    if (ssl)
      :/(host, port).secure
    else
      :/(host, port)

}