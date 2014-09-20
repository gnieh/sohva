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
    version := "1.0.0",
    scalaVersion := "2.11.2",
    crossScalaVersions := Seq("2.10.4", "2.11.2"),
    libraryDependencies ++= globalDependencies,
    parallelExecution := false,
    scalacOptions ++= Seq("-deprecation", "-feature", "-language:higherKinds,implicitConversions,reflectiveCalls")
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

  lazy val scalariformSettings = defaultScalariformSettings ++ Seq(
    ScalariformKeys.preferences :=
      ScalariformKeys.preferences.value
        .setPreference(AlignSingleLineCaseStatements, true)
        .setPreference(DoubleIndentClassDeclaration, true)
        .setPreference(PreserveDanglingCloseParenthesis, true)
        .setPreference(MultilineScaladocCommentsStartOnFirstLine, true)
  )

  lazy val globalDependencies = Seq(
    "org.scalatest" %% "scalatest" % "2.2.2" % "test",
    "com.typesafe.akka" %% "akka-osgi" % "2.3.6" % "test"
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

  lazy val client = Project(id = "sohva-client",
    base = file("sohva-client")) settings(globalSettings: _*) settings(
      description := "Couchdb client library",
      libraryDependencies ++= clientDependencies,
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

  lazy val clientDependencies = globalDependencies ++ Seq(
    "io.spray" %% "spray-client" % "1.3.1",
    "com.typesafe.akka" %% "akka-actor" % "2.3.6" % "provided",
    "org.gnieh" %% "diffson" % "0.3",
    "com.jsuereth" %% "scala-arm" % "1.4",
    "net.liftweb" %% "lift-json" % "2.6-RC1",
    "org.slf4j" % "slf4j-api" % "1.7.2",
    "com.netflix.rxjava" % "rxjava-scala" % "0.17.4"
  )

  lazy val testing = Project(id = "sohva-testing",
    base = file("sohva-testing")) settings(globalSettings: _*) settings(
      description := "Couchdb testing library",
      libraryDependencies ++= testingDependencies
    ) dependsOn(client)

  lazy val testingDependencies = Seq(
    "org.scalatest" %% "scalatest" % "2.2.2"
  )

  lazy val entities = Project(id = "sohva-entities",
    base = file("sohva-entities")) settings(globalSettings: _*) settings(
      description := "Entity Component System storing entities in a couchdb instance",
      libraryDependencies ++= entitiesDependencies
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

  lazy val entitiesDependencies = clientDependencies ++ Seq(
    "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
    "ch.qos.logback" % "logback-classic" % "1.1.2" % "test"
  )

  lazy val dm = Project(id = "sohva-dm",
    base = file("sohva-dm")) settings(globalSettings: _*) settings(
      description := "Design documents manager based on Sohva",
      libraryDependencies ++= dmDependencies
    ) settings(osgiSettings: _*) settings(scalariformSettings: _*) settings(
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

  lazy val dmDependencies = clientDependencies ++ Seq(
    "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3",
    "ch.qos.logback" % "logback-classic" % "1.1.2" % "test",
    "com.typesafe" % "config" % "1.2.1"
  )

}
