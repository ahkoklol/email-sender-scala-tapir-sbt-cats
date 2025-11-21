val tapirVersion = "1.11.50"
val doobieVersion = "1.0.0-RC5"
val flywayVersion = "10.10.0"

lazy val rootProject = (project in file(".")).settings(
  Seq(
    name := "mdistributions-back-cats",
    version := "0.1.0-SNAPSHOT",
    organization := "com.ahkoklol",
    scalaVersion := "3.7.3",
    Global / semanticdbEnabled := true,
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion,
      "org.http4s" %% "http4s-ember-server" % "0.23.30",
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
      "ch.qos.logback" % "logback-classic" % "1.5.18",
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % tapirVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.tpolecat" %% "doobie-core"      % doobieVersion,
      "org.tpolecat" %% "doobie-hikari"    % doobieVersion,
      "org.tpolecat" %% "doobie-postgres"  % doobieVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.6",
      "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.41.0" % Test,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.41.0" % Test,
      "org.mindrot" % "jbcrypt" % "0.4",
      "com.google.apis" % "google-api-services-sheets" % "v4-rev20220927-2.0.0",
      "com.google.auth" % "google-auth-library-oauth2-http" % "1.19.0",
      "com.sun.mail" % "javax.mail" % "1.6.2",
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.4",
      "com.github.jwt-scala" %% "jwt-circe" % "9.4.5",
      "org.flywaydb" % "flyway-core"                % flywayVersion,
      "org.flywaydb" % "flyway-database-postgresql" % flywayVersion,
      "org.apache.poi" % "poi-ooxml" % "5.2.5",
    )
  )
)
