import Scalaz._

lazy val root = project
  .in(file("."))
  .settings(
    skip in publish := true
  )
  .aggregate(coreJVM, coreJS, benchmarks)
  .enablePlugins(ScalaJSPlugin)

lazy val core = crossProject
  .in(file("core"))
  .settings(stdSettings("effect"))
  .settings(
    libraryDependencies ++= Seq("org.specs2" %%% "specs2-core"          % "4.2.0" % Test,
                                "org.specs2" %%% "specs2-matcher-extra" % "4.2.0" % Test),
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
