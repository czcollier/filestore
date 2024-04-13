import sbt._
import sbt.librarymanagement.URLRepository
import sbt.librarymanagement.MavenRepository
object Favorites {
  def unsafe(repo: MavenRepository) = repo.withAllowInsecureProtocol(true)
  object Repositories {
    val typesafe           = unsafe("Typesafe repo"            at "http://repo.typesafe.com/typesafe/releases")
    val scalaTools         = "Scala Tools Releases"     at "https://oss.sonatype.org/content/groups/scala-tools"
    val scalaToolsSnaps    = "Scala Tools Snapshots"    at "https://oss.sonatype.org/content/repositories/snapshots"
    val spray              = ("spray repo"               at "http://repo.spray.io").withAllowInsecureProtocol(true)
    val sprayNightlies     = "spray nightlies"          at "http://nightlies.spray.io"
    val novus              = "Novus Releases"           at "http://repo.novus.com/releases"
    val gamlor             = "Gamlor-Repo"              at "https://github.com/gamlerhart/gamlor-mvn/raw/master/snapshots"
  }

  object Versions {
    val akka          = "2.5.20"
    val berkeleydb    = "3.2.76"
    val casbah        = "2.4.1"
    val dispatch      = "0.9.2"
    val guava         = "17.0"
    val jodaTime      = "1.6"
    val lift          = "2.4"
    val logback       = "1.1.2"
    val reactiveMongo = "0.9"
    val rogue         = "1.1.8"
    val salat         = "1.9.2-SNAPSHOT"
    val scalaTest     = "2.0"
    val slf4j         = "1.7.7"
    val specs2        = "2.3.13"
    val spray         = "1.3.1"
    val sprayJson     = "1.2.6"
    val squeryl       = "0.9.5-2"
    val sprayFunnel   = "1.0-RC4-spray1.3"
  }

  object Libraries {

    val scalaIOCore     = "com.github.scala-incubator.io" %% "scala-io-core"   % "0.4.3"
    val scalaIOFile     = "com.github.scala-incubator.io" %% "scala-io-file"   % "0.4.3"
    val gamlorAsyncIO   = "info.gamlor.akkaasync"         %% "akka-io"         % "1.0-SNAPSHOT"

    // akka
    val akkaActor       = "com.typesafe.akka"         %% "akka-actor"          % Versions.akka          % "compile"
    val akkaAgent       = "com.typesafe.akka"         %% "akka-agent"          % Versions.akka          % "compile"
    val akkaSlf4j       = "com.typesafe.akka"         %% "akka-slf4j"          % Versions.akka
    val akkaRemote      = "com.typesafe.akka"         %% "akka-remote"         % Versions.akka

    // spray
    val sprayCan        = "io.spray"                  %%  "spray-can"           % Versions.spray         % "compile"
    val sprayRouting    = "io.spray"                  %%  "spray-routing"       % Versions.spray         % "compile"
    val sprayCaching    = "io.spray"                  %%  "spray-caching"       % Versions.spray         % "compile"
    val sprayClient     = "io.spray"                  %%  "spray-client"        % Versions.spray         % "compile"
    val sprayJson       = "io.spray"                  %% "spray-json"           % Versions.sprayJson     % "compile"

    // MongoDB access
    val casbah          = "org.mongodb"               %% "casbah"              % Versions.casbah        % "compile"
    val liftMongo       = "net.liftweb"               %% "lift-mongodb"        % Versions.lift          % "compile"
    val liftMongoRecord = "net.liftweb"               %% "lift-mongodb-record" % Versions.lift          % "compile"
    val liftJson        = "net.liftweb"               %% "lift-json"           % Versions.lift          % "compile"
    val rogue           = "com.foursquare"            %% "rogue"               % Versions.rogue         % "compile" intransitive()
    val salat           = "com.novus"                 %% "salat"               % Versions.salat         % "compile"
    val reactiveMongo   = "org.reactivemongo"         %% "reactivemongo"       % Versions.reactiveMongo % "compile"

    // RDBMS access
    val squeryl         = "org.squeryl"               %% "squeryl"             % Versions.squeryl       % "compile"

    val berkeleydb      = "berkeleydb"                % "je"                   % Versions.berkeleydb    % "compile"

    // HTTP (async)
    val dispatch        = "net.databinder.dispatch"   %% "dispatch-core"       % Versions.dispatch      % "compile"

    // misc
    val jodaTime        = "joda-time"                 %  "joda-time"           % Versions.jodaTime      % "compile"
    val guava           = "com.google.guava"          % "guava"                % Versions.guava

    //logging
    val slf4j           = "org.slf4j"                 %  "slf4j-api"           % Versions.slf4j
    val logback         = "ch.qos.logback"            %  "logback-classic"     % Versions.logback
    
    // testing
    val akkaTestKit     = "com.typesafe.akka"         %% "akka-testkit"        % Versions.akka          % "test"
    val scalaTest       = "org.scalatest"             %% "scalatest"           % Versions.scalaTest     % "test"
    val specs2          = "org.specs2"                %% "specs2"              % Versions.specs2        % "test"
    val sprayTest       = "io.spray"                  %  "spray-testkit"       % Versions.spray         % "test"
  }
}
