import Favorites._
import sbt._
import Keys._

import spray.revolver.RevolverPlugin._
import com.github.retronym.SbtOneJar._
//import com.typesafe.sbt.SbtAtmos._

import net.virtualvoid.sbt.graph.Plugin.graphSettings

object Build extends sbt.Build {

  def standardSettings = Seq(
    organization      := "bullhorn",
    scalaVersion      := "2.11.2",
    scalacOptions     := Seq("-deprecation", "-encoding", "utf8"),
    artifact in oneJar <<= moduleName(Artifact(_, "dist")),
    exportJars    := true
  ) ++ Seq(oneJarSettings: _*)


  lazy val filestore = Project(
    id = "filestore",
    base = file("."),
    settings = standardSettings
        ++ graphSettings
        ++ Seq(Revolver.settings: _*)
        //++ Seq(atmosSettings: _*)
        ++ Seq(
      version           := "0.1.0",
      resolvers         ++= Seq(
        Repositories.typesafe,
        Repositories.spray
      ),
      libraryDependencies ++= Seq(
        Libraries.akkaActor,
        Libraries.akkaAgent,
        Libraries.berkeleydb,
        Libraries.guava,
        Libraries.sprayCan,
        Libraries.sprayRouting,
        Libraries.sprayCaching,
        Libraries.sprayClient,
        Libraries.sprayJson,
        Libraries.specs2,
        Libraries.slf4j,
        Libraries.akkaSlf4j,
        Libraries.logback
      )
    )
  )
  
  lazy val testClient = Project(
    id = "testClient",
    base = file("testclient"),
    settings = standardSettings
        ++ Seq(Revolver.settings: _*)
        //++ Seq(atmosSettings: _*)
        ++ Seq(
      version           := "0.1.0",
      resolvers         ++= Seq(
        Repositories.spray
      ),
      libraryDependencies ++= Seq(
        Libraries.akkaActor,
        Libraries.guava,
        Libraries.sprayClient,
        Libraries.sprayJson,
        Libraries.specs2,
        Libraries.slf4j,
        Libraries.akkaSlf4j,
        Libraries.logback
      )
    )
  )
}
