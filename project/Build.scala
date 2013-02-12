import sbt._
import Keys._

object SohvaBuild extends Build {

  val sohvaVersion = "0.2-SNAPSHOT"

  lazy val sohva = (Project(id = "sohva",
    base = file(".")) settings (
    organization in ThisBuild := "org.gnieh",
    name := "sohva",
    version in ThisBuild := sohvaVersion,
    scalaVersion in ThisBuild := "2.10.0",
    crossScalaVersions in ThisBuild := Seq("2.9.2", "2.10.0"),
    libraryDependencies in ThisBuild ++= globalDependencies,
    compileOptions)
    settings(publishSettings: _*)
  ) aggregate(client, server)

  lazy val globalDependencies = Seq(
    "org.scalatest" %% "scalatest" % "2.0.M5" % "test" cross CrossVersion.binaryMapped {
      case "2.9.2" => "2.9.0"
      case v => v
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
      <licenses>
        <license>
          <name>The Apache Software License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
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
      <organization>
        <name>gnieh.org</name>
        <url>https://github.com/gnieh</url>
      </organization>
      <issueManagement>
        <system>github</system>
        <url>https://github.com/gnieh/sohva/issues</url>
      </issueManagement>
    )
  )

  lazy val client = Project(id = "sohva-client",
    base = file("sohva-client")) settings (
    libraryDependencies ++= clientDependencies
  )

  lazy val clientDependencies = Seq(
    "net.databinder.dispatch" %% "dispatch-core" % "0.9.5" exclude("commons-logging", "commons-logging"),
    "net.liftweb" %% "lift-json" % "2.5-M4",
    "net.sf.mime-util" % "mime-util" % "1.2" excludeAll(
      ExclusionRule(organization = "log4j", name = "log4j"),
      ExclusionRule(organization = "commons-logging", name = "commons-logging")
    ),
    "org.slf4j" % "slf4j-api" % "1.7.2",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.2"
  )

  lazy val server = Project(id = "sohva-server",
    base = file("sohva-server")) settings (
    libraryDependencies ++= serverDependencies
  )

  lazy val serverDependencies = Seq(
    "net.liftweb" %% "lift-json" % "2.5-M4"
  )
}
