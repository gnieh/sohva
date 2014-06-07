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
package dm

import java.io.File

/** A design manager allows people to easily manage design documents stored in a CouchDB
 *  database.
 *  It allows for storing, updating, deleting designs, views and more from a bunch of
 *  files stored in a directory.
 *  The structure of the directory is as follows:
 *  {{{
 *  base
 *  |-- design1
 *  |   |-- revision.json
 *  |   |-- views
 *  |   |   |-- view1
 *  |   |   |   `-- map.js
 *  |   |   `-- view2
 *  |   |       |-- map.js
 *  |   |       `-- reduce.js
 *  `-- design2
 *      |-- filters
 *      |   |-- filter1.js
 *      |   `-- filter2.js
 *      |-- language.json
 *      |-- lists
 *      |   |-- list1.js
 *      |   `-- list2.js
 *      |-- rewrites
 *      |   |-- rewrite1.json
 *      |   |-- rewrite2.json
 *      |   `-- rewrite3.json
 *      |-- shows
 *      |   `-- show.js
 *      |-- updates
 *      |   `-- update.js
 *      |-- validate_doc_update.js
 *      `-- views
 *          `-- view
 *              `-- map.js
 *  }}}
 *
 *  The `language.json` file must contain a json object with two fields:
 *   - `name`: the design language (e.g. `javascript` or `coffeescript`),
 *   - `extension`: the file extension (e.g. `js` or `coffee`).
 *  If it is not present, javascript is assumed as language and file extension is `js`.
 *  The list of default extensions based in language name can be configured via the
 *  [typesafe config engine](https://github.com/typesafehub/config/). To add a new language binding, you must add them in the
 *  `sohva.dm.extensions` configuration object. For example some of the default extensions look like this:
 *  {{{
 *  sohva {
 *    dm {
 *      extensions {
 *        javascript = js
 *        coffeescript = coffee
 *      }
 *    }
 *  }
 *  }}}
 *
 *  All js (or whatever the language is) files must contain the definition of a single function corresponding
 *  to the document field.
 *  It also allows for bi-directional synchronization between design documents in the database and the ones
 *  described in the directory.
 *  The rewrite objects are sorted alphabetically by the file name in the array.
 *
 *  The `revision.json` file, if present, must contain a string specifying the last known revision
 *  in the database that corresponds to this version of the design document.
 *  This allows for more efficient synchronization of design documents. It is automatically added or updated when
 *  a design document is imported from a database or synchronized if `trackRevisions` is set to `true`.
 *  If it is not present, and the document also exists in the database, upon synchronization, the local version of
 *  the design document is taken as reference in case of concurrent modifications and is added if missing in the database.
 *
 *  @author Lucas Satabin
 */
trait DesignManager[Result[_]] {

  val basedir: File

  val dbName: String

  val trackRevisions: Boolean

  /** Creates the base directory if it does not exists.
   *  Returns `true` iff the base directory was actually created, `false` otherwise.
   *  If a file with the given path exists, or it is impossible to create the base directory,
   *  an exception is thrown
   */
  def createBasedir(): Result[Boolean]

  /** Returns the list of design document names that are currently knwown
   *  and managed. Every managed design document has its counterpart in the
   *  base directory.
   *  All directory directly located under the base directory are considered
   *  to be managed designs.
   */
  def managedDesigns: List[String]

  /** Returns the list of design documents that currently exists in the database */
  def databaseDesigns: Result[List[String]]

  /** Synchronize managed design documents with the database.
   *  This performs a bi-directional synchronization and creates or updates design
   *  documents as needed.
   *  This can only work when revision tracking is enabled.
   *  In case of conflict, a structural merge is performed, with the local version
   *  winning concurrent modifications.
   */
  def synchronize(): Result[Unit]

  /** Download the design documents from the database into the base
   *  directory.
   *  If a conflict occurs between local and remote version, the remote
   *  version takes precedence and replaces the local version.
   *  If this manager is configured to track revisions, the revision file is
   *  created or updated to the current revision. Otherwise, it is removed if
   *  it exists.
   */
  def download(): Result[Unit]

  /** Upload the design documents to the database from the base
   *  directory.
   *  If a conflict occurs between local and remote version, the local
   *  version takes precedence and replaces the remote version.
   *  If this manager is configured to track revisions, the revision file is
   *  created or updated to the current revision. Otherwise, it is removed if
   *  it exists.
   *  If the `force` parameter is set to `true`, upload is performed even if
   *  the designs didn't change since last synchronization.
   */
  def upload(force: Boolean = false): Result[Unit]

}
