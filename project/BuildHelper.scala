import explicitdeps.ExplicitDepsPlugin.autoImport.*
import mdoc.MdocPlugin.autoImport.{mdocIn, mdocOut}
import sbt.*
import sbt.Keys.*
import sbtbuildinfo.*
import sbtbuildinfo.BuildInfoKeys.*
import sbtcrossproject.CrossPlugin.autoImport.*

object BuildHelper {
  val Scala212: String = "2.12.19"
  val Scala213: String = "2.13.13"
  val Scala3: String   = "3.3.3"

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked"
  ) ++ {
    if (true) {
      Seq("-Xfatal-warnings")
    } else {
      Nil
    }
  }

  private val std2xOptions = Seq(
    "-language:higherKinds",
    "-language:existentials",
    "-explaintypes",
    "-Yrangepos",
    "-Xlint:_,-missing-interpolator,-type-parameter-shadow,-infer-any",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  )

  private def optimizerOptions(optimize: Boolean) =
    if (optimize)
      Seq(
        "-opt:l:inline",
        "-opt-inline-from:zio.internal.**"
      )
    else Nil

  def buildInfoSettings(packageName: String) =
    Seq(
      buildInfoKeys    := Seq[BuildInfoKey](organization, moduleName, name, version, scalaVersion, sbtVersion, isSnapshot),
      buildInfoPackage := packageName
    )

  // Keep this consistent with the version in .core-tests/shared/src/test/scala/REPLSpec.scala
  val replSettings = makeReplSettings {
    """|import zio._
       |implicit class RunSyntax[A](io: ZIO[Any, Any, A]) {
       |  def unsafeRun: A =
       |    Unsafe.unsafe { implicit unsafe =>
       |      Runtime.default.unsafe.run(io).getOrThrowFiberFailure()
       |    }
       |}
    """.stripMargin
  }

  // Keep this consistent with the version in .streams-tests/shared/src/test/scala/StreamREPLSpec.scala
  val streamReplSettings = makeReplSettings {
    """|import zio._
       |implicit class RunSyntax[A](io: ZIO[Any, Any, A]) {
       |  def unsafeRun: A =
       |    Unsafe.unsafe { implicit unsafe =>
       |      Runtime.default.unsafe.run(io).getOrThrowFiberFailure()
       |    }
       |}
    """.stripMargin
  }

  def makeReplSettings(initialCommandsStr: String) = Seq(
    // In the repl most warnings are useless or worse.
    // This is intentionally := as it's more direct to enumerate the few
    // options we do want than to try to subtract off the ones we don't.
    // One of -Ydelambdafy:inline or -Yrepl-class-based must be given to
    // avoid deadlocking on parallel operations, see
    //   https://issues.scala-lang.org/browse/SI-9076
    Compile / console / scalacOptions := Seq(
      "-language:higherKinds",
      "-language:existentials",
      "-Xsource:2.13",
      "-Yrepl-class-based"
    ),
    Compile / console / initialCommands := initialCommandsStr
  )

  def extraOptions(scalaVersion: String, optimize: Boolean) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, _)) =>
        Seq(
          "-language:implicitConversions",
          "-Xignore-scala2-macros",
          "-Xmax-inlines:64",
          "-noindent"
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
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
          "-Ywarn-unused:params,-implicits",
          "-Xfuture",
          "-Xsource:2.13",
          "-Xmax-classfile-name",
          "242"
        ) ++ std2xOptions ++ optimizerOptions(optimize)
      case _ => Seq.empty
    }

  def platformSpecificSources(platform: String, conf: String, baseDirectory: File)(versions: String*) = for {
    platform <- List("shared", platform)
    version  <- "scala" :: versions.toList.map("scala-" + _)
    result    = baseDirectory.getParentFile / platform.toLowerCase / "src" / conf / version
    if result.exists
  } yield result

  def crossPlatformSources(scalaVer: String, platform: String, conf: String, baseDir: File) = {
    val versions = CrossVersion.partialVersion(scalaVer) match {
      case Some((2, 12)) =>
        List("2.12-2.13")
      case Some((2, 13)) =>
        List("2.13+", "2.12-2.13")
      case Some((3, _)) =>
        List("2.13+")
      case _ =>
        List()
    }
    platformSpecificSources(platform, conf, baseDir)(versions: _*)
  }

  lazy val crossProjectSettings = Seq(
    Compile / unmanagedSourceDirectories ++= {
      crossPlatformSources(
        scalaVersion.value,
        crossProjectPlatform.value.identifier,
        "main",
        baseDirectory.value
      )
    },
    Test / unmanagedSourceDirectories ++= {
      crossPlatformSources(
        scalaVersion.value,
        crossProjectPlatform.value.identifier,
        "test",
        baseDirectory.value
      )
    }
  )

  def stdSettings(prjName: String) = Seq(
    name                     := s"$prjName",
    crossScalaVersions       := Seq(Scala212, Scala213, Scala3),
    ThisBuild / scalaVersion := Scala213,
    scalacOptions ++= stdOptions ++ extraOptions(scalaVersion.value, optimize = !isSnapshot.value),
    scalacOptions --= {
      if (scalaVersion.value == Scala3)
        List("-Xfatal-warnings")
      else
        List()
    },
    Test / parallelExecution := false,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    // autoAPIMappings := true,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library"),
    Compile / fork := false, // if true, Xmx value isn't picked up by the forked JVM
    Test / fork    := true,
    // For compatibility with Java 9+ module system;
    // without Automatic-Module-Name, the module name is derived from the jar file which is invalid because of the scalaVersion suffix.
    Compile / packageBin / packageOptions +=
      Package.ManifestAttributes(
        "Automatic-Module-Name" -> s"${organization.value}.$prjName".replaceAll("-", ".")
      )
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
      if (scalaVersion.value == Scala3) Seq()
      else
        Seq(
          "org.scala-lang" % "scala-reflect"  % scalaVersion.value % "provided",
          "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
        )
    }
  )

  def nativeSettings = Seq(
    Test / fork := crossProjectPlatform.value == JVMPlatform // set fork to `true` on JVM to improve log readability, JS and Native need `false`
  )

  def jsSettings: List[Def.Setting[_]] = List(
    Test / fork := crossProjectPlatform.value == JVMPlatform // set fork to `true` on JVM to improve log readability, JS and Native need `false`
  )

  def welcomeMessage = onLoadMessage := {

    def header(text: String): String = s"${scala.Console.RED}$text${scala.Console.RESET}"

    def item(text: String): String    = s"${scala.Console.GREEN}> ${scala.Console.CYAN}$text${scala.Console.RESET}"
    def subItem(text: String): String = s"  ${scala.Console.YELLOW}> ${scala.Console.CYAN}$text${scala.Console.RESET}"

    s"""|${header(" ________ ___")}
        |${header("|__  /_ _/ _ \\")}
        |${header("  / / | | | | |")}
        |${header(" / /_ | | |_| |")}
        |${header(s"/____|___\\___/   ${version.value}")}
        |
        |Useful sbt tasks:
        |${item("build")} - Prepares sources, compiles and runs tests.
        |${item("fmt")} - Formats source files using scalafmt
        |${item("~compileJVM")} - Compiles all JVM modules (file-watch enabled)
        |${item("testJVM")} - Runs all JVM tests
        |${item("testJS")} - Runs all ScalaJS tests
        |${item("testOnly *.YourSpec -- -t \"YourLabel\"")} - Only runs tests with matching term e.g.
        |${subItem("coreTestsJVM/testOnly *.ZIOSpec -- -t \"happy-path\"")}
      """.stripMargin
  }

  def mdocSettings(docsDir: String, outDir: String) = Seq[sbt.Def.Setting[_]](
    mdocIn  := baseDirectory.value / docsDir,
    mdocOut := (LocalRootProject / baseDirectory).value / outDir
  )

  implicit class ModuleHelper(p: Project) {
    def module: Project = p.in(file(p.id)).settings(stdSettings(p.id))
  }
}
