import Scalaz._

organization in ThisBuild := "org.scalaz"

version in ThisBuild := "0.1-SNAPSHOT"

publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

dynverSonatypeSnapshots in ThisBuild := true

lazy val sonataCredentials = for {
  username <- sys.env.get("SONATYPE_USERNAME")
  password <- sys.env.get("SONATYPE_PASSWORD")
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)

credentials in ThisBuild ++= sonataCredentials.toSeq

lazy val root = project
  .in(file("."))
  .settings(
    skip in publish := true
  )
  .aggregate(coreJVM, coreJS, benchmarks)
  .enablePlugins(ScalaJSPlugin)

lazy val core = crossProject
  .in(file("core"))
  .settings(stdSettings("zio"))
  .settings(
    libraryDependencies ++= Seq("org.specs2" %%% "specs2-core"          % "4.3.0" % Test,
                                "org.specs2" %%% "specs2-scalacheck"    % "4.3.0" % Test,
                                "org.specs2" %%% "specs2-matcher-extra" % "4.3.0" % Test),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

lazy val coreJVM = core.jvm

lazy val coreJS = core.js

lazy val benchmarks = project.module
  .dependsOn(coreJVM)
  .enablePlugins(JmhPlugin)
  .settings(
    skip in publish := true,
    libraryDependencies ++=
      Seq(
        "org.scala-lang" % "scala-reflect"  % scalaVersion.value,
        "org.scala-lang" % "scala-compiler" % scalaVersion.value % Provided,
        "io.monix"       %% "monix"         % "3.0.0-RC1",
        "org.typelevel"  %% "cats-effect"   % "1.0.0-RC"
      )
  )
