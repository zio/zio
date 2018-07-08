import sbt._
import sbt.Keys._

object Scalaz {
  val testDeps        = Seq("org.scalacheck"  %% "scalacheck"   % "1.14.0" % "test")
  val compileOnlyDeps = Seq("com.github.ghik" %% "silencer-lib" % "1.0"    % "provided")

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-explaintypes",
    "-Yrangepos",
    "-feature",
    "-Xfuture",
    "-Ypartial-unification",
    "-language:higherKinds",
    "-language:existentials",
    "-unchecked",
    "-Yno-adapted-args",
    "-Xlint:_,-type-parameter-shadow",
    "-Xsource:2.13",
    "-Ywarn-dead-code",
    "-Ywarn-inaccessible",
    "-Ywarn-infer-any",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfatal-warnings"
  )

  def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-opt:l:inline",
          "-opt-inline-from:<source>"
        )
      case _ =>
        Seq(
          "-Xexperimental",
          "-Ywarn-unused-import"
        )
    }

  def stdSettings(prjName: String) = Seq(
    name := s"scalaz-$prjName",
    scalacOptions := stdOptions,
    crossScalaVersions := Seq("2.12.6", "2.11.12"),
    scalaVersion in ThisBuild := crossScalaVersions.value.head,
    scalacOptions := stdOptions ++ extraOptions(scalaVersion.value),
    libraryDependencies ++= compileOnlyDeps ++ testDeps ++ Seq(
      compilerPlugin("org.spire-math"         %% "kind-projector"  % "0.9.7"),
      compilerPlugin("com.github.tomasmikula" %% "pascal"          % "0.2.1"),
      compilerPlugin("com.github.ghik"        %% "silencer-plugin" % "1.0")
    ),
    incOptions ~= (_.withLogRecompileOnMacro(false))
  )
}
