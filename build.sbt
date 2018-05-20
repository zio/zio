import Scalaz._

lazy val root = project
  .in(file("."))
  .settings(
    skip in publish := true
  )
  .aggregate(ioJVM, ioJS)
  .enablePlugins(ScalaJSPlugin)

lazy val io = crossProject
  .in(file("io"))
  .settings(stdSettings("effect"))
  .settings(
    libraryDependencies ++=
      Seq("org.specs2" %%% "specs2-core"          % "4.2.0" % "test",
          "org.specs2" %%% "specs2-matcher-extra" % "4.2.0" % "test"),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

lazy val ioJVM = io.jvm

lazy val ioJS = io.js
