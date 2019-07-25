import NativePackagerHelper._
import com.typesafe.sbt.packager.docker._

name := "StarChat"

organization := "com.getjenny"

crossScalaVersions := Seq("2.12.6")

resolvers ++= Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.bintrayRepo("hseeberger", "maven"))

libraryDependencies ++= {
  val AkkaHttpVersion	= "10.1.5"
  val AkkaVersion	= "2.5.18"
  val BreezeVersion	= "0.13.2"
  val ESClientVersion	= "6.2.4"
  val Log4JVersion	= "2.9.1"
  val LogbackVersion	= "1.2.3"
  val ParboiledVersion	= "2.1.4"
  val RoundeightsHasherVersion	= "1.2.0"
  val ScalatestVersion	= "3.0.1"
  val ScalazVersion	= "7.2.18"
  val ScoptVersion	= "3.7.0"
  val TikaVersion	= "1.17"
  Seq(
    "ch.qos.logback" % "logback-classic" % LogbackVersion,
    "com.github.scopt" %% "scopt" % ScoptVersion,
    "com.roundeights" %% "hasher" % RoundeightsHasherVersion,
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-contrib" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-core" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "org.apache.logging.log4j" % "log4j-api" % Log4JVersion,
    "org.apache.logging.log4j" % "log4j-core" % Log4JVersion,
    "org.apache.tika" % "tika-app" % TikaVersion,
    "org.apache.tika" % "tika-core" % TikaVersion,
    "org.apache.tika" % "tika-parsers" % TikaVersion,
    //"org.elasticsearch.client" % "elasticsearch-rest-client" % ESClientVersion,
    //"org.elasticsearch.client" % "elasticsearch-rest-high-level-client" % ESClientVersion,
    "org.elasticsearch.client" % "transport" % ESClientVersion,
    "org.elasticsearch" % "elasticsearch" % ESClientVersion,
    "org.parboiled" %% "parboiled" % ParboiledVersion,
    "org.scalanlp" %% "breeze" % BreezeVersion,
    "org.scalanlp" %% "breeze-natives" % BreezeVersion,
    "org.scalatest" %% "scalatest" % ScalatestVersion % "test",
    "org.scalaz" %% "scalaz-core" % ScalazVersion
   )
}

scalacOptions += "-deprecation"
scalacOptions += "-feature"
//scalacOptions += "-Ylog-classpath"
testOptions in Test += Tests.Argument("-oF")

enablePlugins(GitVersioning)
enablePlugins(GitBranchPrompt)
enablePlugins(JavaServerAppPackaging)
enablePlugins(UniversalPlugin)
enablePlugins(DockerPlugin)
enablePlugins(DockerComposePlugin)

git.useGitDescribe := true

//http://www.scala-sbt.org/sbt-native-packager/formats/docker.html
dockerCommands := Seq(
  Cmd("FROM", "openjdk:8-jre-alpine"),
  Cmd("RUN", "apk", "update"),
  Cmd("RUN", "apk", "add", "bash"),
  Cmd("RUN", "apk", "add", "curl"),
  Cmd("RUN", "addgroup", "-S", "starchat", "&&", "adduser", "-S", "starchat", "-G", "starchat"),
  Cmd("USER", "starchat:starchat"),
  Cmd("LABEL", "maintainer=\"Angelo Leto <angelo@getjenny.com>\""),
  Cmd("LABEL", "description=\"Docker container for StarChat\""),
  Cmd("WORKDIR", "/"),
  Cmd("ADD", "--chown=starchat:starchat", "/opt/docker", "/starchat"),
  Cmd("VOLUME", "/starchat/config"),
  Cmd("VOLUME", "/starchat/log")
)

packageName in Docker := packageName.value
version in Docker := version.value
dockerRepository := Some("getjenny")

//dockerImageCreationTask := (publishLocal in Docker).value
composeNoBuild := true
composeFile := "docker-starchat/docker-compose.test.yml"

// Assembly settings
mainClass in Compile := Some("com.getjenny.starchat.Main")

fork in Test := true
javaOptions in Test ++= Seq("-Dconfig.file=./src/test/resources/application.conf")

// do not buffer test output
logBuffered in Test := false

mappings in Universal ++= {
  // copy configuration files to config directory
  directory("scripts") ++
  contentOf("src/main/resources").toMap.mapValues("config/" + _).toSeq
}

scriptClasspath := Seq("../config/") ++ scriptClasspath.value

licenses := Seq(("GPLv2", url("https://www.gnu.org/licenses/old-licenses/gpl-2.0.md")))

