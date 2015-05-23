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
package dm

import scala.util.Try

import strategy.{
  BarneyStinsonStrategy,
  StructuralMergeStrategy
}

import java.io.{
  File,
  IOException,
  FileNotFoundException
}

import scalax.file.Path
import scalax.file.PathMatcher.{
  IsFile,
  IsDirectory
}
import scalax.io.Codec

import org.slf4j.LoggerFactory

import scala.concurrent.Future

import spray.json._

import com.typesafe.config.ConfigFactory

class DesignManager(val basedir: File, val dbName: String, val couch: CouchDB, val trackRevisions: Boolean)
    extends gnieh.sohva.dm.DesignManager[Future] {

  import DmProtocol._

  private val basepath = Path(basedir)

  private val database = couch.database(dbName)

  private val config = ConfigFactory.load()

  private val logger = LoggerFactory.getLogger(getClass)

  import database.ec

  def createBasedir(): Future[Boolean] =
    if (basepath.exists && basepath.isDirectory)
      Future.successful(false)
    else if (basepath.exists)
      Future.failed(new IOException(s"a file at path $basepath already exists"))
    else
      Future {
        basepath.createDirectory()
        true
      }

  def managedDesigns: List[String] =
    (basepath * IsDirectory).toList.map(_.name)

  def databaseDesigns: Future[List[String]] =
    for (designs <- database._all_docs(startkey = Some("_design/"), endkey = Some("_design0")))
      yield designs.map(_.substring(8))

  def synchronize(): Future[Unit] =
    for {
      dbDesigns <- databaseDesigns
      // these designs are new from the local storage point of view
      newDesigns = dbDesigns.diff(managedDesigns)
      // first synchronize the locally known designs
      database = couch.database(dbName, credit = 1, strategy = StructuralMergeStrategy)
      () <- allCompleted(changedManagedDesigns.map(d => database.saveDoc(fromFiles(d)).map(d => toFiles(d))))
      // then download the new designs
      designs <- database.builtInView("_all_docs").query[String, Rev, DesignDoc](keys = newDesigns.map(d => s"_design/$d"), include_docs = true)
    } yield {
      toFiles(designs.docs.map(_._2))
      saveLastSync()
    }

  def download(): Future[Unit] =
    for {
      designs <- database.builtInView("_all_docs").query[String, Rev, DesignDoc](startkey = Some("_design/"), endkey = Some("_design0"), include_docs = true)
    } yield toFiles(designs.docs.map(_._2))

  def upload(force: Boolean = false): Future[Unit] = {
    val toUpload = if (force) managedDesigns else changedManagedDesigns
    if (toUpload.nonEmpty) {
      // we give credit of one to allow for one retry in case of conflict,
      // and use the "new is always better" strategy to make local design documents
      // take precedence over documents in the database.
      val database = couch.database(dbName, credit = 1, strategy = BarneyStinsonStrategy)

      val futures =
        for (design <- toUpload)
          yield database.saveDoc(fromFiles(design)).map(d => saveRev(design, d._rev))
      allCompleted(futures).map(_ => saveLastSync())
    } else {
      Future.successful(saveLastSync())
    }
  }

  private def fromFiles(name: String): DesignDoc = {
    val designpath = basepath / name
    val Language(language, ext) = loadLanguage(designpath)
    val views = loadViews(designpath, ext)
    val shows = loadShows(designpath, ext)
    val updates = loadUpdates(designpath, ext)
    val lists = loadLists(designpath, ext)
    val filters = loadFilters(designpath, ext)
    val rewrites = loadRewrites(designpath)
    val validate_doc_update = loadValidateDocUpdate(designpath, ext)
    val rev = loadRev(designpath)
    DesignDoc(s"_design/$name",
      language,
      views = views,
      validate_doc_update = validate_doc_update,
      updates = updates,
      filters = filters,
      shows = shows,
      lists = lists,
      rewrites = rewrites).withRev(rev)
  }

  private def toFiles(designs: List[DesignDoc]): Unit = {
    for (design <- designs)
      toFiles(design)
    saveLastSync()
  }

  private def toFiles(design: DesignDoc): Unit = {
    val name = design._id.substring(8)
    logger.info(s"Downloading design $name")
    if (!trackRevisions || isNewer(name, design._rev)) {
      val designpath = basepath / name
      val revpath = designpath / "revision.json"
      val ext = extensionFor(design.language)
      // simply replace all local design documents by the ones from the database
      if (designpath.exists)
        designpath.deleteRecursively()
      designpath.createDirectory()
      // save the language file
      saveLanguage(name, Language(design.language, ext))
      // save the views
      saveViews(designpath / "views", ext, design.views)
      // save the shows
      saveShows(designpath / "shows", ext, design.shows)
      // save the updates
      saveUpdates(designpath / "updates", ext, design.updates)
      // save the lists
      saveLists(designpath / "lists", ext, design.lists)
      // save the filters
      saveFilters(designpath / "filters", ext, design.filters)
      // save the rewrites
      saveRewrites(designpath / "rewrites", design.rewrites)
      // save the validate_doc_update
      saveValidateDocUpdate(designpath, ext, design.validate_doc_update)
      // if tracking, save the revision
      if (trackRevisions)
        saveRev(name, design._rev)
    }
  }

  private def loadViews(path: Path, extension: String): Map[String, ViewDoc] =
    (for {
      viewpath <- (path * "views" * IsDirectory).toList
      view <- loadView(viewpath, extension)
    } yield (viewpath.name, view)).toMap

  private def saveViews(path: Path, extension: String, views: Map[String, ViewDoc]): Unit =
    for ((name, view) <- views)
      saveView(path, extension, name, view)

  private def loadView(path: Path, extension: String): Option[ViewDoc] =
    for {
      map <- loadOptionalFile(path / s"map.$extension")
      reduce = loadOptionalFile(path / s"reduce.$extension")
    } yield ViewDoc(map, reduce)

  private def saveView(path: Path, extension: String, name: String, view: ViewDoc): Unit = {
    val viewpath = path / name
    viewpath.createDirectory(createParents = true)
    val map = viewpath / s"map.$extension"
    map.createFile()
    map.write(view.map)(Codec.UTF8)
    for (red <- view.reduce) {
      val reduce = viewpath / s"reduce.$extension"
      reduce.createFile()
      reduce.write(red)(Codec.UTF8)
    }
  }

  private def loadShows(path: Path, extension: String): Map[String, String] =
    (for (show <- (path * "shows" * "*.$extension").toList)
      yield (dropExt(show.name), loadFile(show))).toMap

  private def saveShows(path: Path, extension: String, shows: Map[String, String]): Unit =
    for ((name, fun) <- shows)
      saveShow(path, extension, name, fun)

  private def saveShow(path: Path, extension: String, name: String, fun: String): Unit = {
    val showpath = path / s"$name.$extension"
    showpath.createFile(createParents = true)
    showpath.write(fun)(Codec.UTF8)
  }

  private def loadUpdates(path: Path, extension: String): Map[String, String] =
    (for (update <- (path * "updates" * "*.$extension").toList)
      yield (dropExt(update.name), loadFile(update))).toMap

  private def saveUpdates(path: Path, extension: String, updates: Map[String, String]): Unit =
    for ((name, fun) <- updates)
      saveUpdate(path, extension, name, fun)

  private def saveUpdate(path: Path, extension: String, name: String, fun: String): Unit = {
    val updatepath = path / s"$name.$extension"
    updatepath.createFile(createParents = true)
    updatepath.write(fun)(Codec.UTF8)
  }

  private def loadLists(path: Path, extension: String): Map[String, String] =
    (for (list <- (path * "lists" * "*.$extension").toList)
      yield (dropExt(list.name), loadFile(list))).toMap

  private def saveLists(path: Path, extension: String, lists: Map[String, String]): Unit =
    for ((name, fun) <- lists)
      saveList(path, extension, name, fun)

  private def saveList(path: Path, extension: String, name: String, fun: String): Unit = {
    val listpath = path / s"$name.$extension"
    listpath.createFile(createParents = true)
    listpath.write(fun)(Codec.UTF8)
  }

  private def loadFilters(path: Path, extension: String): Map[String, String] =
    (for (filter <- (path * "filters" * "*.$extension").toList)
      yield (dropExt(filter.name), loadFile(filter))).toMap

  private def saveFilters(path: Path, extension: String, filters: Map[String, String]): Unit =
    for ((name, fun) <- filters)
      saveFilter(path, extension, name, fun)

  private def saveFilter(path: Path, extension: String, name: String, fun: String): Unit = {
    val filterpath = path / s"$name.$extension"
    filterpath.createFile(createParents = true)
    filterpath.write(fun)(Codec.UTF8)
  }

  private def loadRewrites(path: Path): List[RewriteRule] =
    for {
      rewrite <- (path * "rewrites" * "*.json").toList.sorted
      rw <- Try(JsonParser(loadFile(rewrite)).convertTo[RewriteRule]).toOption
    } yield rw

  private def saveRewrites(path: Path, rules: List[RewriteRule]): Unit =
    for ((rule, idx) <- rules.zipWithIndex) {
      val rulepath = path / s"$idx.json"
      rulepath.createFile(createParents = true)
      rulepath.write(rule.toJson.prettyPrint)(Codec.UTF8)
    }

  private def loadValidateDocUpdate(path: Path, extension: String): Option[String] =
    loadOptionalFile(path / s"validate_doc_update.$extension")

  private def saveValidateDocUpdate(path: Path, extension: String, fun: Option[String]): Unit =
    for (f <- fun) {
      val validatepath = path / s"validate_doc_update.$extension"
      validatepath.createFile()
      validatepath.write(f)(Codec.UTF8)
    }

  private def loadLanguage(path: Path): Language =
    (for {
      content <- loadOptionalFile(path / "language.json")
      language <- Try(JsonParser(content).convertTo[Language]).toOption
    } yield language).getOrElse(Language("javascript", "js"))

  private def loadRev(path: Path): Option[String] =
    for {
      content <- loadOptionalFile(path / "revision.json")
      rev <- Try(JsonParser(content).convertTo[String]).toOption
    } yield rev

  private def loadFile(path: Path): String =
    if (path.exists)
      path.string(Codec.UTF8)
    else
      throw new FileNotFoundException(path.toString)

  private def loadOptionalFile(path: Path): Option[String] =
    if (path.exists)
      Some(path.string(Codec.UTF8))
    else
      None

  private def dropExt(name: String): String =
    name.substring(0, name.lastIndexOf('.'))

  private def saveRev(design: String, rev: Option[String]): Unit = {
    val revpath = basepath / design / "revision.json"
    if (trackRevisions) {
      rev match {
        case Some(rev) =>
          revpath.createFile(failIfExists = false)
          revpath.write(s""""$rev"""")(Codec.UTF8)
        case None =>
          revpath.deleteIfExists()
      }
    } else {
      // if not tracking, remove any existing revision file to avoid
      // confusion if starting to track again later
      revpath.deleteIfExists()
    }
  }

  private def saveLastSync(): Unit = {
    val lastpath = basepath / "last-sync.txt"
    lastpath.createFile(failIfExists = false)
    lastpath.write(System.currentTimeMillis.toString)(Codec.UTF8)
  }

  private def loadLastSync: Option[Long] =
    for (content <- loadOptionalFile(basepath / "last-sync.txt"))
      yield content.toLong

  private def hasChanged: Boolean =
    loadLastSync
      .map(lastSync => managedDesigns.exists(lastModified(_) > lastSync))
      .getOrElse(managedDesigns.nonEmpty)

  private def changedManagedDesigns: List[String] =
    loadLastSync
      .map(lastSync => managedDesigns.filter(lastModified(_) > lastSync))
      .getOrElse(managedDesigns)

  private def isNewer(design: String, rev: Option[String]): Boolean =
    loadRev(basepath / design) != rev

  private def saveLanguage(design: String, language: Language): Unit = {
    val langpath = basepath / design / "language.json"
    langpath.createFile(failIfExists = false)
    langpath.write(language.toJson.prettyPrint)(Codec.UTF8)
  }

  private def extensionFor(language: String): String = {
    val path = s"sohva.dm.extensions.$language"
    if (config.hasPath(path))
      config.getString(path)
    else
      config.getString("sohva.dm.extensions.default")
  }

  private def lastModified(design: String): Long =
    (basepath / design ** IsFile).toList.map(_.lastModified).max

  private def allCompleted[T](futures: Seq[Future[T]]): Future[Unit] =
    Future.fold(futures)(()) { case ((), _) => () }

}

case class Language(language: String, extension: String)
case class Rev(rev: String)
