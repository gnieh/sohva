package sohva

import sbt._
import Keys._
import com.typesafe.sbt.osgi.SbtOsgi._
import com.typesafe.sbt.osgi.OsgiKeys
import sbtunidoc.Plugin._

import java.io.File

object SohvaBuild extends Build {

  val sohvaVersion = "0.5-SNAPSHOT"

  lazy val sohva = (Project(id = "sohva",
    base = file(".")) settings (
    resolvers in ThisBuild += "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
    resolvers in ThisBuild += "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases/",
    organization in ThisBuild := "org.gnieh",
    licenses in ThisBuild += ("The Apache Software License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    homepage in ThisBuild := Some(url("https://github.com/gnieh/sohva")),
    name := "sohva",
    version in ThisBuild := sohvaVersion,
    scalaVersion in ThisBuild := "2.10.2",
    crossScalaVersions in ThisBuild := Seq("2.9.3", "2.10.2"),
    libraryDependencies in ThisBuild ++= globalDependencies,
    parallelExecution in ThisBuild := false,
    compileOptions)
    settings(publishSettings: _*)
    settings(unidocSettings: _*)
  ) aggregate(client, testing)

  lazy val globalDependencies = Seq(
    "org.scalatest" %% "scalatest" % "2.0.M5b" % "test",
    "com.jsuereth" %% "scala-arm" % "1.3" % "test" cross CrossVersion.binaryMapped {
      case "2.9.3" => "2.9.2"
      case v       => v
    }
  )

  lazy val compileOptions = scalacOptions in ThisBuild <++= scalaVersion map { v =>
    if(v.startsWith("2.10"))
      Seq("-deprecation", "-language:_")
    else
      Seq("-deprecation")
  }

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
    ) settings(osgiSettings: _*) settings (
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
    "net.databinder.dispatch" %% "dispatch-core" % "0.10.0" exclude("commons-logging", "commons-logging"),
    "org.gnieh" %% "diffson" % "0.2",
    "com.jsuereth" %% "scala-arm" % "1.3" cross CrossVersion.binaryMapped {
      case "2.9.3" => "2.9.2"
      case v => "2.10"
    },
    "net.liftweb" %% "lift-json" % "2.5" cross CrossVersion.binaryMapped {
      case "2.9.3" => "2.9.2"
      case v => "2.10"
    },
    "org.slf4j" % "slf4j-api" % "1.7.2"
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
