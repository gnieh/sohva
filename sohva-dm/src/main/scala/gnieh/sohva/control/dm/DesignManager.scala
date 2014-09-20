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
package control
package dm

import java.io.File

import gnieh.sohva.async.dm.{ DesignManager => ADesignManager }

import scala.util.Try

class DesignManager private[dm] (val wrapped: ADesignManager)
    extends gnieh.sohva.dm.DesignManager[Try] {

  def this(basedir: File, dbName: String, couch: CouchDB, trackRevisions: Boolean) =
    this(new ADesignManager(basedir: File, dbName: String, couch.wrapped, trackRevisions))

  val basedir: File =
    wrapped.basedir

  val dbName: String =
    wrapped.dbName

  val trackRevisions: Boolean =
    wrapped.trackRevisions

  def createBasedir(): Try[Boolean] =
    synced(wrapped.createBasedir)

  def managedDesigns: List[String] =
    wrapped.managedDesigns

  def databaseDesigns: Try[List[String]] =
    synced(wrapped.databaseDesigns)

  def synchronize(): Try[Unit] =
    synced(wrapped.synchronize())

  def download(): Try[Unit] =
    synced(wrapped.download())

  def upload(force: Boolean = false): Try[Unit] =
    synced(wrapped.upload(force))

}
