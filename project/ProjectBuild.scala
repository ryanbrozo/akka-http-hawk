import sbt.Keys._
import sbt._

object BuildDependencies {
  private val AKKA_VERSION = "2.3.12"
  private val AKKA_STREAMS_VERSION = "2.0-M1"
  val SPRAY_VERSION = "1.3.1"

  // Dependencies
  val akkaStreams =       "com.typesafe.akka"             %% "akka-stream-experimental"                  % AKKA_STREAMS_VERSION
  val akkaHttpCore =      "com.typesafe.akka"             %% "akka-http-core-experimental"               % AKKA_STREAMS_VERSION
  val akkaHttp =          "com.typesafe.akka"             %% "akka-http-experimental"                    % AKKA_STREAMS_VERSION
  val akkaHttpTestKit =   "com.typesafe.akka"             %% "akka-http-testkit-experimental"            % AKKA_STREAMS_VERSION   % "test"
  val akkaHttpSprayJson = "com.typesafe.akka"             %% "akka-http-spray-json-experimental"         % AKKA_STREAMS_VERSION
  val akkaActor =         "com.typesafe.akka"             %% "akka-actor"                                % AKKA_VERSION
  val sprayCaching =      "io.spray"                      %% "spray-caching"                             % SPRAY_VERSION
  val parboiled =         "org.parboiled"                 %% "parboiled-scala"                           % "1.1.7"
  val scalaLogging =      "com.typesafe.scala-logging"    %% "scala-logging"                             % "3.1.0"
  val logback =           "ch.qos.logback"                %  "logback-classic"                           % "1.0.13"
  val scalaTest =         "org.scalatest"                 %% "scalatest"                                 % "2.2.4"                % "test"
}

object BuildSettings {
  import BuildDependencies._

  val SCALA_VERSION = "2.11.7"
  val APP_VERSION = "0.1"

  lazy val commonSettings = Seq(
    organization        := "com.ryanbrozo",
    scalaVersion        := SCALA_VERSION,
    version             := APP_VERSION,
    licenses            := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),
    homepage            := Some(url("https://github.com/ryanbrozo/akka-http-hawk/")),
    scmInfo             := Some(ScmInfo(
      url("https://github.com/ryanbrozo/akka-http-hawk/"),
      "scm:git:git@github.com:ryanbrozo/akka-http-hawk.git"
    )),
    description         := "akka-http-hawk is a library that adds Hawk Authentication to akka-http. " +
      "It can be used for both server-side (via akka-http--routing) and client-side (via akka-http-client)"
  )

  lazy val publishSettings = Seq(
    publishMavenStyle   := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra := (
      <developers>
        <developer>
          <id>ryanbrozo</id>
          <name>Ryan Brozo</name>
          <email>https://github.com/ryanbrozo</email>
        </developer>
      </developers>
      )
  )

  lazy val coreSettings = Seq(
    libraryDependencies ++= Seq(
      parboiled,
      scalaLogging,
      logback,
      scalaTest
    ),
    autoAPIMappings := true,
    scalacOptions in Test ++= Seq("-Yrangepos", "-deprecation")
  )

  lazy val libSettings = Seq(
    libraryDependencies ++= Seq(
      akkaActor,
      akkaStreams % "provided",
      akkaHttpCore % "provided",
      akkaHttp % "provided",
      akkaHttpTestKit,
      sprayCaching,
      scalaLogging,
      logback,
      scalaTest
    ),
    autoAPIMappings := true,
    scalacOptions in Test ++= Seq("-Yrangepos", "-deprecation")
  )

  lazy val serverSettings = Seq(
    libraryDependencies ++= Seq(
      akkaActor,
      akkaStreams,
      akkaHttpCore,
      akkaHttp,
      akkaHttpTestKit,
      scalaLogging,
      logback,
      scalaTest
    ),
    autoAPIMappings := true,
    scalacOptions in Test ++= Seq("-Yrangepos", "-deprecation")
  )

  lazy val clientSettings = Seq(
    libraryDependencies ++= Seq(
      akkaActor,
      scalaLogging,
      logback,
      scalaTest
    ),
    autoAPIMappings := true,
    scalacOptions in Test ++= Seq("-Yrangepos", "-deprecation")
  )
}

object ProjectBuild extends Build {

  import BuildSettings._

  lazy val main = Project(
    id = "akka-http-hawk",
    base = file(".")
  )
    .aggregate(core, lib, server, client)
    .settings(commonSettings: _*)

  lazy val core = Project(
    id = "scala-hawk-core",
    base = file("core")
  )
    .settings(commonSettings: _*)
    .settings(coreSettings: _*)

  lazy val lib = Project(
    id = "akka-http-hawk-lib",
    base = file("lib")
  )
    .dependsOn(core)
    .settings(commonSettings: _*)
    .settings(libSettings: _*)

  lazy val server = Project(
    id = "akka-http-hawk-server",
    base = file("server")
  )
    .dependsOn(lib)
    .settings(commonSettings: _*)
    .settings(serverSettings: _*)

  lazy val client = Project(
    id = "akka-http-hawk-client",
    base = file("client")
  )
    .dependsOn(lib)
    .settings(commonSettings: _*)
    .settings(clientSettings: _*)
}