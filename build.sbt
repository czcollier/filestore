import Favorites._

def standardSettings = Seq(
  ) 

lazy val core = (project in file("."))
 .settings(
    organization      := "xorf",
    scalaVersion      := "2.11.12",
    scalacOptions     := Seq("-deprecation", "-encoding", "utf8"),
    exportJars        := true,
    mainClass in (Compile, run) := Some("net.xorf.filestore.Boot"),
    version := "0.1.0",
    resolvers ++= Seq(
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
 
lazy val testClient = (project in file("testclient"))
.settings(
    organization      := "xorf",
    scalaVersion      := "2.11.12",
    scalacOptions     := Seq("-deprecation", "-encoding", "utf8"),
    exportJars        := true,
    mainClass in (Compile, run) := Some("net.xorf.filestore.FileStoreClient"),
    resolvers ++= Seq(
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