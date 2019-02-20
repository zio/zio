import sbt._
import Keys._

import explicitdeps.ExplicitDepsPlugin.autoImport._
import sbtcrossproject.CrossPlugin.autoImport.CrossType

import sbtbuildinfo._
import BuildInfoKeys._

object Scalaz {
  val testDeps        = Seq("org.scalacheck"  %% "scalacheck"   % "1.14.0" % "test")
  val compileOnlyDeps = Seq("com.github.ghik" %% "silencer-lib" % "1.3.1"  % "provided")

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-explaintypes",
    "-Yrangepos",
    "-feature",
    "-Xfuture",
    "-language:higherKinds",
    "-language:existentials",
    "-unchecked",
    "-Xlint:_,-type-parameter-shadow",
    "-Xsource:2.13",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfatal-warnings"
  )

  val buildInfoSettings = Seq(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
    buildInfoPackage := "scalaz.zio",
    buildInfoObject := "BuildInfo"
  )

  val replSettings = Seq(
    // In the repl most warnings are useless or worse.
    // This is intentionally := as it's more direct to enumerate the few
    // options we do want than to try to subtract off the ones we don't.
    // One of -Ydelambdafy:inline or -Yrepl-class-based must be given to
    // avoid deadlocking on parallel operations, see
    //   https://issues.scala-lang.org/browse/SI-9076
    scalacOptions in Compile in console := Seq(
      "-Ypartial-unification",
      "-language:higherKinds",
      "-language:existentials",
      "-Yno-adapted-args",
      "-Xsource:2.13",
      "-Yrepl-class-based"
    ),
    initialCommands in Compile in console := """
                                               |import scalaz._
                                               |import scalaz.zio._
                                               |import scalaz.zio.console._
                                               |import scalaz.zio.stream._
                                               |object replRTS extends DefaultRuntime {}
                                               |import replRTS._
                                               |implicit class RunSyntax[E, A](io: IO[E, A]){ def unsafeRun: A = replRTS.unsafeRun(io) }
    """.stripMargin
  )

  def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 13)) =>
        Seq()
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-opt:l:inline",
          "-opt-inline-from:<source>",
          "-Ypartial-unification",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit"
        )
      case Some((2, 11)) =>
        Seq(
          "-Ypartial-unification",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
          "-Xexperimental",
          "-Ywarn-unused-import"
        )
      case _ => Seq.empty
    }

  def stdSettings(prjName: String) = Seq(
    name := s"scalaz-$prjName",
    scalacOptions := stdOptions,
    crossScalaVersions := Seq("2.12.8", "2.11.12", "2.13.0-M5"),
    scalaVersion in ThisBuild := crossScalaVersions.value.head,
    scalacOptions := stdOptions ++ extraOptions(scalaVersion.value),
    libraryDependencies ++= compileOnlyDeps ++ testDeps ++ Seq(
      compilerPlugin("org.spire-math"  %% "kind-projector"  % "0.9.9"),
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.3.1")
    ),
    parallelExecution in Test := true,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings := true,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library"),
    Compile / unmanagedSourceDirectories ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, x)) if x <= 11 =>
          CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.11"))
        case Some((2, x)) if x >= 12 =>
          CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.12+"))
        case _ => Nil
      }
    }
  )

  implicit class ModuleHelper(p: Project) {
    def module: Project = p.in(file(p.id)).settings(stdSettings(p.id))
  }
}
