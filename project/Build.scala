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
    resolvers in ThisBuild += "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
    resolvers in ThisBuild += "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases/",
    organization in ThisBuild := "org.gnieh",
    licenses in ThisBuild += ("The Apache Software License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    homepage in ThisBuild := Some(url("https://github.com/gnieh/sohva")),
    name := "sohva",
    version in ThisBuild := sohvaVersion,
    scalaVersion in ThisBuild := "2.10.4",
    libraryDependencies in ThisBuild ++= globalDependencies,
    parallelExecution in ThisBuild := false,
    scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature", "-language:higherKinds,implicitConversions,reflectiveCalls"))
    settings(publishSettings: _*)
    settings(unidocSettings: _*)
  ) aggregate(client, testing)

  lazy val scalariformSettings = defaultScalariformSettings ++ Seq(
    ScalariformKeys.preferences :=
      ScalariformKeys.preferences.value
        .setPreference(AlignSingleLineCaseStatements, true)
        .setPreference(DoubleIndentClassDeclaration, true)
        .setPreference(PreserveDanglingCloseParenthesis, true)
        .setPreference(MultilineScaladocCommentsStartOnFirstLine, true)
  )

  lazy val globalDependencies = Seq(
    "org.scalatest" %% "scalatest" % "2.1.6" % "test",
    "com.jsuereth" %% "scala-arm" % "1.3" % "test",
    "com.typesafe.akka" %% "akka-osgi" % "2.3.0" % "test"
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

  lazy val clientDependencies = Seq(
    "io.spray" % "spray-client" % "1.3.1",
    "com.typesafe.akka" %% "akka-actor" % "2.3.0" % "provided",
    "org.gnieh" %% "diffson" % "0.2",
    "com.jsuereth" %% "scala-arm" % "1.3",
    "net.liftweb" %% "lift-json" % "2.5",
    "org.slf4j" % "slf4j-api" % "1.7.2",
    "com.netflix.rxjava" % "rxjava-scala" % "0.17.4"
  )

  lazy val testing = Project(id = "sohva-testing",
    base = file("sohva-testing")) settings(
      description := "Couchdb testing library",
      libraryDependencies ++= testingDependencies
    ) dependsOn(client)

  lazy val testingDependencies = Seq(
    "org.scalatest" %% "scalatest" % "2.0.M5b"
  )

}
