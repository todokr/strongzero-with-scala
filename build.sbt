ThisBuild / scalaVersion := "2.13.7"
ThisBuild / organization := "io.github.todokr"

val AkkaHttpVersion    = "10.2.7"
val AkkaVersion        = "2.6.17"
val AirframeVersion    = "21.12.0"
val ScalikeJdbcVersion = "3.5.0"

val ServerDeps = Seq(
  "com.typesafe.akka" %% "akka-http"                % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json"     % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-actor-typed"         % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream"              % AkkaVersion,
  "org.scalikejdbc"   %% "scalikejdbc"              % ScalikeJdbcVersion,
  "org.scalikejdbc"   %% "scalikejdbc-config"       % ScalikeJdbcVersion,
  "com.h2database"     % "h2"                       % "1.4.200",
  "ch.qos.logback"     % "logback-classic"          % "1.2.3",
  "com.typesafe.akka" %% "akka-http-testkit"        % AkkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion     % Test,
  "org.scalatest"     %% "scalatest"                % "3.1.4"         % Test
)

lazy val root = (project in file("."))
  .settings(
    name := "StrongZero with Scala"
  ).aggregate(producerApp, consumerApp1, consumerApp2)

lazy val scalangZero = (project in file("scalang-zero"))
  .settings(
    libraryDependencies ++= Seq(
      "org.zeromq"          % "jeromq"           % "0.5.2",
      "org.wvlet.airframe" %% "airframe-codec"   % AirframeVersion,
      "org.wvlet.airframe" %% "airframe-control" % AirframeVersion,
      "org.wvlet.airframe" %% "airframe-jmx"     % AirframeVersion,
      "org.wvlet.airframe" %% "airframe-log"     % AirframeVersion,
      "org.wvlet.airframe" %% "airframe-ulid"    % AirframeVersion
    )
  )

lazy val producerApp = (project in file("producer-app"))
  .settings(
    libraryDependencies ++= ServerDeps
  )

lazy val consumerApp1 = (project in file("consumer-app1"))
  .settings(
    libraryDependencies ++= ServerDeps
  )

lazy val consumerApp2 = (project in file("consumer-app2"))
  .settings(
    libraryDependencies ++= ServerDeps
  )
