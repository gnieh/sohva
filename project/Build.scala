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

  val sohvaVersion = "1.0.0-SNAPSHOT"

  lazy val sohva = (Project(id = "sohva",
    base = file(".")) settings (
    resolvers in ThisBuild += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
    resolvers in ThisBuild += "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
    resolvers in ThisBuild += "Spray Repository" at "http://repo.spray.io",
    organization in ThisBuild := "org.gnieh",
    licenses in ThisBuild += ("The Apache Software License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    homepage in ThisBuild := Some(url("https://github.com/gnieh/sohva")),
    name := "sohva",
    version in ThisBuild := sohvaVersion,
    scalaVersion in ThisBuild := "2.11.1",
    crossScalaVersions in ThisBuild := Seq("2.11.1", "2.10.4"),
    libraryDependencies in ThisBuild ++= globalDependencies,
    parallelExecution in ThisBuild := false,
    scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature", "-language:higherKinds,implicitConversions,reflectiveCalls"))
    settings(publishSettings: _*)
    settings(unidocSettings: _*)
  ) aggregate(client, testing, entities)

  lazy val scalariformSettings = defaultScalariformSettings ++ Seq(
    ScalariformKeys.preferences :=
      ScalariformKeys.preferences.value
        .setPreference(AlignSingleLineCaseStatements, true)
        .setPreference(DoubleIndentClassDeclaration, true)
        .setPreference(PreserveDanglingCloseParenthesis, true)
        .setPreference(MultilineScaladocCommentsStartOnFirstLine, true)
  )

  lazy val globalDependencies = Seq(
    "org.scalatest" %% "scalatest" % "2.2.0" % "test",
    "com.typesafe.akka" %% "akka-osgi" % "2.3.3" % "test"
  )

  lazy val publishSettings = Seq(
    publishMavenStyle in ThisBuild := true,
    publishArtifact in Test := false,
    // The Nexus repo we're publishing to.
    publishTo in ThisBuild <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
        if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
        else Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    pomIncludeRepository in ThisBuild := { x => false },
    pomExtra in ThisBuild := (
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

  lazy val client = Project(id = "sohva-client",
    base = file("sohva-client")) settings (
      description := "Couchdb client library",
      libraryDependencies <++= scalaVersion(clientDependencies _),
      fork in test := true,
      resourceDirectories in Compile := List()
    ) settings(osgiSettings: _*) settings(scalariformSettings: _*) settings (
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

  def clientDependencies(v: String) = {
    val (spraySuffix, sprayVersion) =
      if(v.startsWith("2.11"))
        ("_2.11", "1.3.1-20140423")
      else
        ("", "1.3.1")
    val jsonVersion =
      if(v.startsWith("2.11"))
        "2.6-M4"
      else
        "2.5"
    Seq(
      "io.spray" % s"spray-client$spraySuffix" % sprayVersion,
      "com.typesafe.akka" %% "akka-actor" % "2.3.3" % "provided",
      "org.gnieh" %% "diffson" % "0.3-SNAPSHOT",
      "com.jsuereth" %% "scala-arm" % "1.4",
      "net.liftweb" %% "lift-json" % jsonVersion,
      "org.slf4j" % "slf4j-api" % "1.7.2",
      "com.netflix.rxjava" % "rxjava-scala" % "0.17.4"
    )
  }

  lazy val testing = Project(id = "sohva-testing",
    base = file("sohva-testing")) settings(
      description := "Couchdb testing library",
      libraryDependencies ++= testingDependencies
    ) dependsOn(client)

  lazy val testingDependencies = Seq(
    "org.scalatest" %% "scalatest" % "2.1.7"
  )

  lazy val entities = Project(id = "sohva-entities",
    base = file("sohva-entities")) settings(
      description := "Entity Component System storing entities in a couchdb instance",
      version := "0.1.0-SNAPSHOT",
      libraryDependencies <++= scalaVersion(entitiesDependencies _)
    ) settings(osgiSettings: _*) settings(scalariformSettings: _*) settings(
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

  def entitiesDependencies(v: String) = clientDependencies(v) ++ Seq(
    "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
    "ch.qos.logback" % "logback-classic" % "1.1.2" % "test"
  )

}
