lazy val V = _root_.scalafix.sbt.BuildInfo
inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("http://degoes.net")
      )
    ),
    scalaVersion := V.scala213,
    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions ++= List(
      "-Yrangepos",
      "-P:semanticdb:synthetics:on"
    )
  )
)

skip in publish := true

val zio1Version = "1.0.12"
val zio2Version = "2.0.0-M5"

lazy val rules = project.settings(
  moduleName                             := "scalafix",
  libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % V.scalafixVersion
)

lazy val input = project.settings(
  skip in publish                  := true,
  libraryDependencies += "dev.zio" %% "zio"         % zio1Version,
  libraryDependencies += "dev.zio" %% "zio-streams" % zio1Version,
  libraryDependencies += "dev.zio" %% "zio-test"    % zio1Version
)

lazy val output = project.settings(
  skip in publish                  := true,
  libraryDependencies += "dev.zio" %% "zio"         % zio2Version,
  libraryDependencies += "dev.zio" %% "zio-streams" % zio2Version,
  libraryDependencies += "dev.zio" %% "zio-test"    % zio2Version
)

lazy val tests = project
  .settings(
    skip in publish                       := true,
    libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % V.scalafixVersion % Test cross CrossVersion.full,
    compile.in(Compile) :=
      compile.in(Compile).dependsOn(compile.in(input, Compile)).value,
    scalafixTestkitOutputSourceDirectories :=
      sourceDirectories.in(output, Compile).value,
    scalafixTestkitInputSourceDirectories :=
      sourceDirectories.in(input, Compile).value,
    scalafixTestkitInputClasspath :=
      fullClasspath.in(input, Compile).value
  )
  .dependsOn(rules)
  .enablePlugins(ScalafixTestkitPlugin)
