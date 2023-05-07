import BuildHelper._

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
  "dev.zio"       %% "zio"                 % "2.0.10",
  "dev.zio"       %% "zio-json"            % "0.4.2",
  "dev.zio"       %% "zio-http"            % "0.0.5",
  "io.getquill"   %% "quill-zio"           % "4.6.0",
  "io.getquill"   %% "quill-jdbc-zio"      % "4.6.0",
  "com.h2database" % "h2"                  % "2.1.214",
  "dev.zio"       %% "zio-config"          % "4.0.0-RC14",
  "dev.zio"       %% "zio-config-typesafe" % "4.0.0-RC14",
  "dev.zio"       %% "zio-config-magnolia" % "4.0.0-RC14"
)

enablePlugins(MdocPlugin)
