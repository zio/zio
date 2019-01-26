// shadow sbt-scalajs' crossProject from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import Scalaz._
import xerial.sbt.Sonatype._

name := "scalaz-zio"

inThisBuild(
  List(
    organization := "org.scalaz",
    homepage := Some(url("https://scalaz.github.io/scalaz-zio/")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("http://degoes.net")
      )
    )
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root = project
  .in(file("."))
  .settings(
    skip in publish := true,
    console := (console in Compile in coreJVM).value
  )
  .aggregate(
    coreJVM,
    coreJS,
    interopSharedJVM,
    interopSharedJS,
    interopCatsJVM,
    interopCatsJS,
    interopFutureJVM,
    interopFutureJS,
    interopMonixJVM,
    interopMonixJS,
    interopScalaz7xJVM,
    interopScalaz7xJS,
    interopJavaJVM,
    benchmarks,
    microsite
  )
  .enablePlugins(ScalaJSPlugin)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .in(file("core"))
  .settings(stdSettings("zio"))
  .settings(
    libraryDependencies ++= Seq(
      "org.specs2" %%% "specs2-core"          % "4.4.0" % Test,
      "org.specs2" %%% "specs2-scalacheck"    % "4.4.0" % Test,
      "org.specs2" %%% "specs2-matcher-extra" % "4.4.0" % Test
    ),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
    buildInfoPackage := "scalaz.zio",
    buildInfoObject := "BuildInfo"
  )

lazy val coreJVM = core.jvm
  .configure(_.enablePlugins(JCStressPlugin))
  .settings(
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
                                               |object replRTS extends RTS {}
                                               |import replRTS._
                                               |implicit class RunSyntax[E, A](io: IO[E, A]){ def unsafeRun: A = replRTS.unsafeRun(io) }
    """.stripMargin
  )

lazy val coreJS = core.js

lazy val interopShared = crossProject(JSPlatform, JVMPlatform)
  .in(file("interop-shared"))
  .settings(stdSettings("zio-interop-shared"))
  .dependsOn(core % "test->test;compile->compile")
  .settings(
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

lazy val interopSharedJVM = interopShared.jvm

lazy val interopSharedJS = interopShared.js

lazy val interopCats = crossProject(JSPlatform, JVMPlatform)
  .in(file("interop-cats"))
  .settings(stdSettings("zio-interop-cats"))
  .dependsOn(core % "test->test;compile->compile")
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % "1.2.0" % Optional,
      "co.fs2"        %%% "fs2-core"    % "1.0.2" % Test
    ),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

lazy val interopCatsJVM = interopCats.jvm
  .dependsOn(interopSharedJVM)
  // Below is for the cats law spec
  // Separated due to binary incompatibility in scalacheck 1.13 vs 1.14
  // TODO remove it when https://github.com/typelevel/discipline/issues/52 is closed
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel"              %% "cats-effect-laws"          % "1.2.0" % Test,
      "org.typelevel"              %% "cats-testkit"              % "1.5.0" % Test,
      "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % "1.1.8" % Test
    ),
    dependencyOverrides += "org.scalacheck" %% "scalacheck" % "1.13.5" % Test
  )

lazy val interopCatsJS = interopCats.js.dependsOn(interopSharedJS)

lazy val interopFuture = crossProject(JSPlatform, JVMPlatform)
  .in(file("interop-future"))
  .settings(stdSettings("zio-interop-future"))
  .dependsOn(core % "test->test;compile->compile")
  .settings(
    scalacOptions in Test ++= Seq("-Yrangepos"),
  )

lazy val interopFutureJVM = interopFuture.jvm.dependsOn(interopSharedJVM)

lazy val interopFutureJS = interopFuture.js.dependsOn(interopSharedJS)

lazy val interopMonix = crossProject(JSPlatform, JVMPlatform)
  .in(file("interop-monix"))
  .settings(stdSettings("zio-interop-monix"))
  .dependsOn(core % "test->test;compile->compile")
  .settings(
    libraryDependencies ++= Seq(
      "io.monix" %%% "monix" % "3.0.0-RC2" % Optional
    ),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

lazy val interopMonixJVM = interopMonix.jvm.dependsOn(interopSharedJVM)

lazy val interopMonixJS = interopMonix.js.dependsOn(interopSharedJS)

lazy val interopScalaz7x = crossProject(JSPlatform, JVMPlatform)
  .in(file("interop-scalaz7x"))
  .settings(stdSettings("zio-interop-scalaz7x"))
  .dependsOn(core % "test->test;compile->compile")
  .settings(
    libraryDependencies ++= Seq(
      "org.scalaz" %%% "scalaz-core"               % "7.2.+" % Optional,
      "org.scalaz" %%% "scalaz-scalacheck-binding" % "7.2.+" % Test
    ),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

lazy val interopScalaz7xJVM = interopScalaz7x.jvm.dependsOn(interopSharedJVM)

lazy val interopScalaz7xJS = interopScalaz7x.js.dependsOn(interopSharedJS)

lazy val interopJava = project.module
  .in(file("interop-java"))
  .dependsOn(coreJVM, interopSharedJVM)
  .settings(
    skip in publish := true,
    libraryDependencies ++= Seq(
      "org.projectlombok" % "lombok" % "1.16.16" % Compile
    )
  )

lazy val interopJavaJVM = interopJava

lazy val benchmarks = project.module
  .dependsOn(coreJVM)
  .enablePlugins(JmhPlugin)
  .settings(
    skip in publish := true,
    libraryDependencies ++=
      Seq(
        "org.scala-lang"    % "scala-reflect"  % scalaVersion.value,
        "org.scala-lang"    % "scala-compiler" % scalaVersion.value % Provided,
        "io.monix"          %% "monix"         % "3.0.0-RC2",
        "org.typelevel"     %% "cats-effect"   % "1.2.0",
        "co.fs2"            %% "fs2-core"      % "1.0.2",
        "com.typesafe.akka" %% "akka-stream"   % "2.5.19"
      ),
    scalacOptions in Compile in console := Seq(
      "-Ypartial-unification",
      "-language:higherKinds",
      "-language:existentials",
      "-Yno-adapted-args",
      "-Xsource:2.13",
      "-Yrepl-class-based"
    )
  )

lazy val microsite = project.module
  .dependsOn(coreJVM, interopCatsJVM, interopFutureJVM, interopScalaz7xJVM, interopJavaJVM)
  .enablePlugins(MicrositesPlugin)
  .settings(
    scalacOptions -= "-Yno-imports",
    scalacOptions ~= { _ filterNot (_ startsWith "-Ywarn") },
    scalacOptions ~= { _ filterNot (_ startsWith "-Xlint") },
    skip in publish := true,
    libraryDependencies ++= Seq(
      "com.github.ghik" %% "silencer-lib" % "1.3.1",
      "commons-io"      % "commons-io"    % "2.6"
    ),
    micrositeFooterText := Some(
      """
        |<p>&copy; 2018 <a href="https://github.com/scalaz/scalaz-zio">ZIO Maintainers</a></p>
        |""".stripMargin
    ),
    micrositeName := "",
    micrositeDescription := "Type-safe, composable asynchronous and concurrent programming for Scala",
    micrositeAuthor := "ZIO contributors",
    micrositeOrganizationHomepage := "https://github.com/scalaz/scalaz-zio",
    micrositeGitterChannelUrl := "scalaz/scalaz-zio",
    micrositeGitHostingUrl := "https://github.com/scalaz/scalaz-zio",
    micrositeGithubOwner := "scalaz",
    micrositeGithubRepo := "scalaz-zio",
    micrositeFavicons := Seq(microsites.MicrositeFavicon("favicon.png", "512x512")),
    micrositeDocumentationUrl := s"https://javadoc.io/doc/org.scalaz/scalaz-zio_2.12/${(version in Compile).value}",
    micrositeDocumentationLabelDescription := "Scaladoc",
    micrositeBaseUrl := "/scalaz-zio",
    micrositePalette := Map(
      "brand-primary"   -> "#990000",
      "brand-secondary" -> "#000000",
      "brand-tertiary"  -> "#990000",
      "gray-dark"       -> "#453E46",
      "gray"            -> "#837F84",
      "gray-light"      -> "#E3E2E3",
      "gray-lighter"    -> "#F4F3F4",
      "white-color"     -> "#FFFFFF"
    )
  )
