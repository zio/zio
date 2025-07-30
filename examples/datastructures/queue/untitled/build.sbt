ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.6"

lazy val root = (project in file("."))
  .settings(
    name := "untitled"
  )

libraryDependencies += "dev.zio" %% "zio" % "2.1.19"
