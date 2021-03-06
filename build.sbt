import sbt.Keys._

organization := "com.github.angiolep"

name := "akka-wamp"

version := "0.3.0"

scalaVersion := "2.11.8"


description := "WAMP - Web Application Messaging Protocol implementation written in Scala with Akka"

mainClass in Compile := Some("akka.wamp.WebSocketRouter")

libraryDependencies ++= Seq (
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.6",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.6",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.7.2",
  "com.typesafe.akka" % "akka-http-testkit_2.11" % "2.4.2" % "test",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)

testOptions in Test += Tests.Setup( () => System.setProperty("akka.loglevel", "warning") )

publishMavenStyle := true

isSnapshot := false

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("Apache 2" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))

homepage := Some(url("http://angiolep.github.io/akka-wamp"))

pomExtra :=
  <scm>
    <url>git://github.com/angiolep/akka-wamp.git</url>
    <connection>scm:git:git@github.com:angiolep/akka-wamp.git</connection>
  </scm>
    <developers>
      <developer>
        <name>Paolo Angioletti</name>
        <email>paolo.angioletti@gmail.com</email>
        <url>http://angiolep.github.io</url>
      </developer>
    </developers>

apiURL := Some(url("http://angiolep.github.io/projects/akka-wamp/doc/index.html"))

credentials += Credentials(Path.userHome / ".ivy2" / "sonatype")

enablePlugins(JavaAppPackaging)
