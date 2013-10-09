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

import resource._

import java.io.{
  File,
  FileWriter
}

import scala.sys.process._

/** A CouchDB instance that can be started or stopped at will.
 *
 *  @param basedir the base directory in which the instance data will be stored
 *  @param persist should the files persist when the server is stopped
 *  @param overwrite should the files (configuration, databases, ...) be oberwritten if they exist
 *  @param local_ini the local configuration for this instance
 *
 *  @author Lucas Satabin
 */
class CouchInstance(val basedir: File,
                    val persist: Boolean,
                    val overwrite: Boolean,
                    val version: String = "1.4",
                    val local_ini: Configuration = Configuration(Map())) {

  /** The couchdb command that is run. By default it is `couchdb`.
   *  Override it if you need to specifiy another name
   */
  val couchdb: String = "couchdb"

  private val confdir: File = new File(new File(basedir, "etc"), "couchdb")
  private val vardir = new File(basedir, "var")
  private val datadir: File = new File(new File(vardir, "lib"), "couchdb")
  private val logdir: File = new File(new File(vardir, "log"), "couchdb")
  private val rundir: File = new File(new File(vardir, "run"), "couchdb")
  private val default_ini: Configuration =
    if(version.startsWith("1.4"))
      new DefaultConfiguration14(datadir, logdir, rundir)
    else
      new DefaultConfiguration12(datadir, logdir, rundir)
  private val defaultfile = new File(confdir, "default.ini")
  private val localfile = new File(confdir, "local.ini")
  private val pidfile = new File(rundir, "couchdb.pid")
  private val logfile =
    new File(local_ini.sections.get("log").flatMap(_.get("file")).orElse(default_ini.log.get("file")).get)

  val configuration = default_ini.merge(local_ini)

  private var process: Option[Process] = None

  /** Starts this CouchDB instance */
  def start(): Boolean = synchronized {
    if(process.isEmpty) {
      if(overwrite) {
        delete(basedir)
      }
      // create the configuration directory
      confdir.mkdirs
      // create the data directory
      datadir.mkdirs
      // create the log directory
      logdir.mkdirs
      // create the run directory
      rundir.mkdirs
      // create the configuration files if needed
      if(!defaultfile.exists) {
        for(writer <- managed(new FileWriter(defaultfile, false))) {
          writer.write(default_ini.toString)
        }
      }
      if(!localfile.exists) {
        for(writer <- managed(new FileWriter(localfile, false))) {
          writer.write(local_ini.toString)
        }
      }
      // check file permissions
      if(!defaultfile.canRead || !localfile.canRead || !defaultfile.canWrite || (logfile.exists && !logfile.canWrite) || (!logfile.exists &&
        !logdir.canWrite)) {
        println("File permissions do not allow sohva to correctly start the couchdb instance")
        println(defaultfile.getCanonicalPath + " read: " + ok(defaultfile.canRead))
        println(localfile.getCanonicalPath + " read: " + ok(localfile.canRead) + " write: " + ok(localfile.canWrite))
        println(logfile.getCanonicalPath + " write: " + ok(logfile.canWrite))
        println(logdir.getCanonicalPath + " write: " + ok(logdir.canWrite))
      } else {
        val p = Process(
          couchdb + " -b -p " + pidfile.getCanonicalPath +
          " -a " + defaultfile.getCanonicalPath +
          " -a " + localfile.getCanonicalPath,
          basedir).run(new ProcessIO(_ => (), _ => (), _ => ()))
        println(p)
        process = Some(p)
      }
    }
    process.isDefined
  }

  /** Stops this CouchDB instance */
  def stop(): Boolean = synchronized {
    if(process.isDefined) {
      val res = Process(couchdb + " -p " + pidfile.getCanonicalPath + " -d").! == 0
      if(!persist)
        delete(basedir)
      process.foreach(_.destroy)
      process = None
      res
    } else {
      false
    }
  }

  // ========== internals ==========

  private def ok(b: Boolean): String =
    if(b) "OK" else "NOK"

  private def delete(f: File): Unit = {
    if(f.isDirectory) {
      for(file <- wrap(f.listFiles)) {
        delete(file)
      }
      f.delete
    } else if(f.exists) {
      f.delete
    }
  }

  private def wrap(ar: Array[File]): Array[File] =
    if(ar == null)
      Array()
    else
      ar

}

