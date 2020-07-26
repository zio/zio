import sbt._
import Keys._
import explicitdeps.ExplicitDepsPlugin.autoImport._
import sbtcrossproject.CrossPlugin.autoImport._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbtbuildinfo._
import dotty.tools.sbtplugin.DottyPlugin.autoImport._
import BuildInfoKeys._
import scalafix.sbt.ScalafixPlugin.autoImport.{ scalafixConfig, scalafixSemanticdb }
import DottyFullCompat._

object BuildHelper {

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked"
  )

  private val std2xOptions = Seq(
    "-language:higherKinds",
    "-language:existentials",
    "-explaintypes",
    "-Yrangepos",
    "-Xlint:_,-missing-interpolator,-type-parameter-shadow",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  ) ++ customOptions

  private def optimizerOptions(optimize: Boolean) =
    if (optimize)
      Seq(
        "-opt:l:inline",
        "-opt-inline-from:zio.internal.**"
      )
    else Nil

  private def propertyFlag(property: String, default: Boolean) =
    sys.props.get(property).map(_.toBoolean).getOrElse(default)

  private def customOptions =
    if (propertyFlag("fatal.warnings", true)) {
      Seq("-Xfatal-warnings")
    } else {
      Nil
    }

  def buildInfoSettings(packageName: String) =
    Seq(
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
      buildInfoPackage := packageName,
      buildInfoObject := "BuildInfo"
    )

  val dottyVersion = "0.25.0"

  val dottySettings = Seq(
    // Keep this consistent with the version in .circleci/config.yml
    crossScalaVersions += dottyVersion,
    scalacOptions ++= {
      if (isDotty.value)
        Seq("-noindent")
      else
        Seq()
    },
    sources in (Compile, doc) := {
      val old = (Compile / doc / sources).value
      if (isDotty.value) {
        Nil
      } else {
        old
      }
    },
    parallelExecution in Test := {
      val old = (Test / parallelExecution).value
      if (isDotty.value) {
        false
      } else {
        old
      }
    }
  )

  val scalaReflectSettings = Seq(
    libraryDependencies ++= Seq("dev.zio" %%% "izumi-reflect" % "1.0.0-M2")
  )

  // Keep this consistent with the version in .core-tests/shared/src/test/scala/REPLSpec.scala
  val replSettings = makeReplSettings {
    """|import zio._
       |import zio.console._
       |import zio.duration._
       |import zio.Runtime.default._
       |implicit class RunSyntax[A](io: ZIO[ZEnv, Any, A]){ def unsafeRun: A = Runtime.default.unsafeRun(io.provideLayer(ZEnv.live)) }
    """.stripMargin
  }

  // Keep this consistent with the version in .streams-tests/shared/src/test/scala/StreamREPLSpec.scala
  val streamReplSettings = makeReplSettings {
    """|import zio._
       |import zio.console._
       |import zio.duration._
       |import zio.stream._
       |import zio.Runtime.default._
       |implicit class RunSyntax[A](io: ZIO[ZEnv, Any, A]){ def unsafeRun: A = Runtime.default.unsafeRun(io.provideLayer(ZEnv.live)) }
    """.stripMargin
  }

  def makeReplSettings(initialCommandsStr: String) = Seq(
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
    initialCommands in Compile in console := initialCommandsStr
  )

  def extraOptions(scalaVersion: String, optimize: Boolean) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((0, _)) =>
        Seq(
          "-language:implicitConversions",
          "-Xignore-scala2-macros"
        )
      case Some((2, 13)) =>
        Seq(
          "-Ywarn-unused:params,-implicits"
        ) ++ std2xOptions ++ optimizerOptions(optimize)
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-Ypartial-unification",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
          "-Ywarn-unused:params,-implicits",
          "-Xfuture",
          "-Xsource:2.13",
          "-Xmax-classfile-name",
          "242"
        ) ++ std2xOptions ++ optimizerOptions(optimize)
      case Some((2, 11)) =>
        Seq(
          "-Ypartial-unification",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
          "-Xexperimental",
          "-Ywarn-unused-import",
          "-Xfuture",
          "-Xsource:2.13",
          "-Xmax-classfile-name",
          "242"
        ) ++ std2xOptions
      case _ => Seq.empty
    }

  def platformSpecificSources(platform: String, conf: String, baseDirectory: File)(versions: String*) =
    List("scala" :: versions.toList.map("scala-" + _): _*).map { version =>
      baseDirectory.getParentFile / platform.toLowerCase / "src" / conf / version
    }.filter(_.exists)

  def crossPlatformSources(scalaVer: String, platform: String, conf: String, baseDir: File, isDotty: Boolean) =
    CrossVersion.partialVersion(scalaVer) match {
      case Some((2, x)) if x <= 11 =>
        platformSpecificSources(platform, conf, baseDir)("2.11", "2.x")
      case Some((2, x)) if x >= 12 =>
        platformSpecificSources(platform, conf, baseDir)("2.12+", "2.12", "2.x")
      case _ if isDotty =>
        platformSpecificSources(platform, conf, baseDir)("2.12+", "dotty")
      case _ =>
        Nil
    }

  lazy val crossProjectSettings = Seq(
    Compile / unmanagedSourceDirectories ++= {
      val platform = crossProjectPlatform.value.identifier
      val baseDir  = baseDirectory.value
      val scalaVer = scalaVersion.value
      val isDot    = isDotty.value

      crossPlatformSources(scalaVer, platform, "main", baseDir, isDot)
    },
    Test / unmanagedSourceDirectories ++= {
      val platform = crossProjectPlatform.value.identifier
      val baseDir  = baseDirectory.value
      val scalaVer = scalaVersion.value
      val isDot    = isDotty.value

      crossPlatformSources(scalaVer, platform, "test", baseDir, isDot)
    }
  )

  def stdSettings(prjName: String) = Seq(
    name := s"$prjName",
    crossScalaVersions := Seq("2.12.12", "2.11.12", "2.13.3"),
    scalaVersion in ThisBuild := crossScalaVersions.value.head,
    scalafixConfig := {
      if (scalaBinaryVersion.value != "2.13") None
      else Some(file(".scalafix213.conf"))
    },
    scalacOptions := stdOptions ++ extraOptions(scalaVersion.value, optimize = !isSnapshot.value),
    libraryDependencies ++= {
      def silencer(name: String) =
        "com.github.ghik" %% s"silencer-$name" % "1.7.1" withDottyFullCompat scalaVersion.value

      Seq(
        silencer("lib") % Provided
      ) ++ {
        if (isDotty.value) Nil
        else
          Seq(
            compilerPlugin(silencer("plugin")),
            compilerPlugin(scalafixSemanticdb)
          )
      }
    },
    parallelExecution in Test := true,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings := true,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library"),
    Compile / unmanagedSourceDirectories ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, x)) if x <= 11 =>
          Seq(
            Seq(file(sourceDirectory.value.getPath + "/main/scala-2.11")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.11")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + "-2.11")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.x")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.11-2.12"))
          ).flatten
        case Some((2, x)) if x == 12 =>
          Seq(
            Seq(file(sourceDirectory.value.getPath + "/main/scala-2.12")),
            Seq(file(sourceDirectory.value.getPath + "/main/scala-2.12+")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.12+")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + "-2.12+")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.x")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.12-2.13")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.11-2.12"))
          ).flatten
        case Some((2, x)) if x >= 13 =>
          Seq(
            Seq(file(sourceDirectory.value.getPath + "/main/scala-2.12")),
            Seq(file(sourceDirectory.value.getPath + "/main/scala-2.12+")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.12+")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + "-2.12+")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.x")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.12-2.13")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.13+"))
          ).flatten
        case _ =>
          if (isDotty.value)
            Seq(
              Seq(file(sourceDirectory.value.getPath + "/main/scala-2.12")),
              Seq(file(sourceDirectory.value.getPath + "/main/scala-2.12+")),
              CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.12+")),
              CrossType.Full.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + "-2.12+")),
              CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-dotty")),
              CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.13+"))
            ).flatten
          else
            Nil
      }
    },
    Test / unmanagedSourceDirectories ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, x)) if x <= 11 =>
          Seq(
            Seq(file(sourceDirectory.value.getPath + "/test/scala-2.11")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + "-2.x"))
          ).flatten
        case Some((2, x)) if x >= 12 =>
          Seq(
            Seq(file(sourceDirectory.value.getPath + "/test/scala-2.12")),
            Seq(file(sourceDirectory.value.getPath + "/test/scala-2.12+")),
            CrossType.Full.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + "-2.x"))
          ).flatten
        case _ =>
          if (isDotty.value)
            Seq(
              Seq(file(sourceDirectory.value.getPath + "/test/scala-2.12+")),
              CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.12+")),
              CrossType.Full.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + "-dotty"))
            ).flatten
          else
            Nil
      }

    }
  )

  def macroExpansionSettings = Seq(
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 13)) => Seq("-Ymacro-annotations")
        case _             => Seq.empty
      }
    },
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, x)) if x <= 12 =>
          Seq(compilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full)))
        case _ => Seq.empty
      }
    }
  )

  def macroDefinitionSettings = Seq(
    scalacOptions += "-language:experimental.macros",
    libraryDependencies ++= {
      if (isDotty.value) Seq()
      else
        Seq(
          "org.scala-lang" % "scala-reflect"  % scalaVersion.value % "provided",
          "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
        )
    }
  )

  def testJsSettings = Seq(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0" % Test
  )

  def welcomeMessage = onLoadMessage := {
    import scala.Console

    def header(text: String): String = s"${Console.RED}$text${Console.RESET}"

    def item(text: String): String    = s"${Console.GREEN}> ${Console.CYAN}$text${Console.RESET}"
    def subItem(text: String): String = s"  ${Console.YELLOW}> ${Console.CYAN}$text${Console.RESET}"

    s"""|${header(" ________ ___")}
        |${header("|__  /_ _/ _ \\")}
        |${header("  / / | | | | |")}
        |${header(" / /_ | | |_| |")}
        |${header(s"/____|___\\___/   ${version.value}")}
        |
        |Useful sbt tasks:
        |${item("build")} - Prepares sources, compiles and runs tests.
        |${item("prepare")} - Prepares sources by applying both scalafix and scalafmt
        |${item("fix")} - Fixes sources files using scalafix
        |${item("fmt")} - Formats source files using scalafmt
        |${item("~compileJVM")} - Compiles all JVM modules (file-watch enabled)
        |${item("testJVM")} - Runs all JVM tests
        |${item("testJS")} - Runs all ScalaJS tests
        |${item("testOnly *.YourSpec -- -t \"YourLabel\"")} - Only runs tests with matching term e.g.
        |${subItem("coreTestsJVM/testOnly *.ZIOSpec -- -t \"happy-path\"")}
        |${item("docs/docusaurusCreateSite")} - Generates the ZIO microsite
      """.stripMargin
  }

  implicit class ModuleHelper(p: Project) {
    def module: Project = p.in(file(p.id)).settings(stdSettings(p.id))
  }
}
