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
package testing

import org.scalatest._

/** Trait that can be mixed into suites that need to interact with some CouchDB
 *  and query it with a client.
 *
 *  @author Lucas Satabin
 */
trait CouchClientSupport[Client <: CouchClient] {
  this: fixture.Suite =>

  type FixtureParam = Client

  /** Build the couch client used in the tests. Each call to this method must return a new instance */
  def makeCouch(config: Map[String, Any]): Client

  /** Called before any test. Override it to populate any data you want */
  def setup(couch: Client): Unit = ()

  def withFixture(test: OneArgTest) {
    val couch = makeCouch(test.configMap)
    try {
      setup(couch) // initialize data if needed
      withFixture(test.toNoArgTest(couch))
    } finally {
      couch.shutdown
    }
  }

}

