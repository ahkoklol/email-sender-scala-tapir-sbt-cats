val tapirVersion = "1.11.50"

lazy val rootProject = (project in file(".")).settings(
  Seq(
    name := "mdistributions-back-cats",
    version := "0.1.0-SNAPSHOT",
    organization := "com.ahkoklol",
    scalaVersion := "3.7.3",
    Global / semanticdbEnabled := true,
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-cats-effect" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server-cats" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
      "ch.qos.logback" % "logback-classic" % "1.5.18",
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % tapirVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )
)
