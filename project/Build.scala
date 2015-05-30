package sohva

import sbt._
import Keys._
import com.typesafe.sbt.osgi.SbtOsgi._
import com.typesafe.sbt.osgi.OsgiKeys
import com.typesafe.sbt.SbtScalariform._
import sbtunidoc.Plugin._
import scalariform.formatter.preferences._

import java.io.File

object SohvaBuild extends Build {

  lazy val globalSettings = Seq(
    resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
    resolvers += "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
    organization := "org.gnieh",
    licenses += ("The Apache Software License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    homepage := Some(url("https://github.com/gnieh/sohva")),
    version := "2.0.0-SNAPSHOT",
    scalaVersion := "2.11.6",
    crossScalaVersions := Seq("2.10.4", "2.11.6"),
    libraryDependencies ++= globalDependencies,
    parallelExecution := false,
    fork in Test := true,
    scalacOptions ++= Seq("-deprecation", "-feature")
  ) ++ publishSettings

  lazy val sohva = (Project(id = "sohva",
    base = file("."))
    settings(globalSettings: _*)
    settings (
      name := "sohva",
      packagedArtifacts :=  Map()
    )
    settings(unidocSettings: _*)
  ) aggregate(client, testing, entities, dm)

  lazy val scalariform = scalariformSettings ++ Seq(
    ScalariformKeys.preferences :=
      ScalariformKeys.preferences.value
        .setPreference(AlignSingleLineCaseStatements, true)
        .setPreference(DoubleIndentClassDeclaration, true)
        .setPreference(PreserveDanglingCloseParenthesis, true)
        .setPreference(MultilineScaladocCommentsStartOnFirstLine, true)
  )

  lazy val globalDependencies = Seq(
    "org.scalatest" %% "scalatest" % "2.2.5" % "test",
    "com.typesafe.akka" %% "akka-osgi" % "2.3.11" % "test"
  )

  lazy val publishSettings = Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    // The Nexus repo we're publishing to.
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
        if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
        else Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    pomIncludeRepository := { x => false },
    pomExtra := (
      <scm>
        <url>https://github.com/gnieh/sohva</url>
        <connection>scm:git:git://github.com/gnieh/sohva.git</connection>
        <developerConnection>scm:git:git@github.com:gnieh/sohva.git</developerConnection>
        <tag>HEAD</tag>
      </scm>
      <developers>
        <developer>
          <id>satabin</id>
          <name>Lucas Satabin</name>
          <email>lucas.satabin@gnieh.org</email>
        </developer>
      </developers>
      <ciManagement>
        <system>travis</system>
        <url>https://travis-ci.org/#!/gnieh/sohva</url>
      </ciManagement>
      <issueManagement>
        <system>github</system>
        <url>https://github.com/gnieh/sohva/issues</url>
      </issueManagement>
    )
  )

  lazy val json = Project(id = "sohva-json",
    base = file("sohva-json")) settings(globalSettings: _*) settings(
      libraryDependencies ++= clientDependencies,
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _),
      libraryDependencies := {
        CrossVersion.partialVersion(scalaVersion.value) match {
          // if scala 2.11+ is used, quasiquotes are merged into scala-reflect
          case Some((2, scalaMajor)) if scalaMajor >= 11 =>
            libraryDependencies.value
          // in Scala .10, quasiquotes are provided by macro paradise
          case Some((2, 10)) =>
            libraryDependencies.value ++ Seq(
              compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full),
              "org.scalamacros" %% "quasiquotes" % "2.0.0" cross CrossVersion.binary)
        }
      })

  lazy val client = Project(id = "sohva-client",
    base = file("sohva-client")) dependsOn(json % "compile-internal, test-internal") settings(globalSettings: _*) settings(
      description := "Couchdb client library",
      libraryDependencies ++= clientDependencies,
      fork in test := true,
      mappings in (Compile, packageBin) ++= mappings.in(json, Compile, packageBin).value,
      mappings in (Compile, packageSrc) ++= mappings.in(json, Compile, packageSrc).value,
      resourceDirectories in Compile := List()
    ) settings(osgiSettings: _*) settings(scalariform: _*) settings (
      OsgiKeys.exportPackage := Seq(
        "gnieh.sohva",
        "gnieh.sohva.*"
      ),
      OsgiKeys.additionalHeaders := Map (
        "Bundle-Name" -> "Sohva CouchDB Client"
      ),
      OsgiKeys.bundleSymbolicName := "org.gnieh.sohva",
      OsgiKeys.privatePackage := Seq()
    )

  lazy val clientDependencies = globalDependencies ++ Seq(
    "io.spray" %% "spray-client" % "1.3.3",
    "com.typesafe.akka" %% "akka-actor" % "2.3.11" % "provided",
    "org.gnieh" %% "diffson" % "1.0.0",
    "com.jsuereth" %% "scala-arm" % "1.4",
    "io.spray" %% "spray-json" % "1.3.2",
    "org.slf4j" % "slf4j-api" % "1.7.12",
    "com.netflix.rxjava" % "rxjava-scala" % "0.20.7"
  )

  lazy val testing = Project(id = "sohva-testing",
    base = file("sohva-testing")) settings(globalSettings: _*) settings(
      description := "Couchdb testing library",
      libraryDependencies ++= testingDependencies
    ) dependsOn(client)

  lazy val testingDependencies = Seq(
    "org.scalatest" %% "scalatest" % "2.2.5"
  )

  lazy val entities = Project(id = "sohva-entities",
    base = file("sohva-entities")) settings(globalSettings: _*) settings(
      description := "Entity Component System storing entities in a couchdb instance",
      libraryDependencies ++= entitiesDependencies(scalaVersion.value)
    ) settings(osgiSettings: _*) settings(scalariform: _*) settings(
      OsgiKeys.exportPackage := Seq(
        "gnieh.sohva.async.entities",
        "gnieh.sohva.sync.entities",
        "gnieh.sohva.control.entities",
        "gnieh.sohva.entities"
      ),
      OsgiKeys.additionalHeaders := Map (
        "Bundle-Name" -> "Sohva Entity Component System"
      ),
      OsgiKeys.bundleSymbolicName := "org.gnieh.sohva.entities",
      OsgiKeys.privatePackage := Seq("gnieh.sohva.async.entities.impl")
    ) dependsOn(client)

  def entitiesDependencies(scalaVersion: String) = clientDependencies ++ Seq(
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 10)) =>
        "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3"
      case _ =>
        "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3-1"
    },
    "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"
  )

  lazy val dm = Project(id = "sohva-dm",
    base = file("sohva-dm")) settings(globalSettings: _*) settings(
      description := "Design documents manager based on Sohva",
      libraryDependencies ++= dmDependencies(scalaVersion.value)
    ) settings(osgiSettings: _*) settings(scalariform: _*) settings(
      OsgiKeys.exportPackage := Seq(
        "gnieh.sohva.dm",
        "gnieh.sohva.async.dm",
        "gnieh.sohva.sync.dm",
        "gnieh.sohva.control.dm"
      ),
      OsgiKeys.additionalHeaders := Map (
        "Bundle-Name" -> "Sohva Design Manager"
      ),
      OsgiKeys.bundleSymbolicName := "org.gnieh.sohva.dm"
    ) dependsOn(client)

  def dmDependencies(scalaVersion: String) = clientDependencies ++ Seq(
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 10)) =>
        "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3"
      case _ =>
        "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3-1"
    },
    "ch.qos.logback" % "logback-classic" % "1.1.3" % "test",
    "com.typesafe" % "config" % "1.3.0"
  )

}
