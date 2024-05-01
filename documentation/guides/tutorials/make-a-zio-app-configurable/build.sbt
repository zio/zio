import BuildHelper._
import Dependencies._

mdocSettings("docs", "website/docs/guides/tutorials/")
fork           := true
publish / skip := true
scalaVersion   := Scala213
unusedCompileDependenciesFilter -= moduleFilter("org.scalameta", "mdoc")
scalacOptions -= "-Yno-imports"
scalacOptions -= "-Xfatal-warnings"
scalacOptions += "-Wconf:any:s"
Compile / fork := false
scalacOptions ~= { _ filterNot (_ startsWith "-Ywarn") }
scalacOptions ~= { _ filterNot (_ startsWith "-Xlint") }
crossScalaVersions --= List(Scala212, Scala3)

libraryDependencies ++= Seq(
  `zio-json`,
  `zio-http`,
  `zio-config`,
  `zio-config-typesafe`,
  `zio-config-magnolia`,
  "io.getquill"   %% "quill-zio"      % "4.6.0",
  "io.getquill"   %% "quill-jdbc-zio" % "4.6.0",
  "com.h2database" % "h2"             % "2.1.214"
)

enablePlugins(MdocPlugin)

resolvers ++= Resolver.sonatypeOssRepos("snapshots")
import sbt._
dependsOn(LocalProject("docs"))