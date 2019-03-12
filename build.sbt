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
    console := (console in Compile in coreJVM).value,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
  )
  .aggregate(
    coreJVM,
    coreJS,
    interopSharedJVM,
    interopSharedJS,
    interopCatsJVM,
    interopCatsJS,
    interopFutureJVM,
//  interopMonixJVM,
//  interopMonixJS,
    interopScalaz7xJVM,
    interopScalaz7xJS,
    interopJavaJVM,
    interopReactiveStreamsJVM,
//  benchmarks,
    microsite,
    testkitJVM
  )
  .enablePlugins(ScalaJSPlugin)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .in(file("core"))
  .settings(stdSettings("zio"))
  .settings(buildInfoSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.specs2" %%% "specs2-core"          % "4.4.0" % Test,
      "org.specs2" %%% "specs2-scalacheck"    % "4.4.0" % Test,
      "org.specs2" %%% "specs2-matcher-extra" % "4.4.0" % Test
    )
  )
  .enablePlugins(BuildInfoPlugin)

lazy val coreJVM = core.jvm
  .configure(_.enablePlugins(JCStressPlugin))
  .settings(replSettings)
lazy val coreJS = core.js

lazy val interopShared = crossProject(JSPlatform, JVMPlatform)
  .in(file("interop-shared"))
  .settings(stdSettings("zio-interop-shared"))
  .dependsOn(core % "test->test;compile->compile")

lazy val interopSharedJVM = interopShared.jvm
lazy val interopSharedJS  = interopShared.js

lazy val interopCats = crossProject(JSPlatform, JVMPlatform)
  .in(file("interop-cats"))
  .settings(stdSettings("zio-interop-cats"))
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % "1.2.0" % Optional,
      "co.fs2"        %%% "fs2-core"    % "1.0.3" % Test
    )
  )
  .dependsOn(core % "test->test;compile->compile")

val CatsScalaCheckVersion = Def.setting {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 12 =>
      "1.13"
    case _ =>
      "1.14"
  }
}

val ScalaCheckVersion = Def.setting {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 12 =>
      "1.13.5"
    case _ =>
      "1.14.0"
  }
}

def majorMinor(version: String) = version.split('.').take(2).mkString(".")

val CatsScalaCheckShapelessVersion = Def.setting {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 12 =>
      "1.1.8"
    case _ =>
      "1.2.0-1+7-a4ed6f38-SNAPSHOT" // TODO: Stable version
  }
}

lazy val interopCatsJVM = interopCats.jvm
  .dependsOn(interopSharedJVM)
  // Below is for the cats law spec
  // Separated due to binary incompatibility in scalacheck 1.13 vs 1.14
  // TODO remove it when https://github.com/typelevel/discipline/issues/52 is closed
  .settings(
    resolvers += Resolver
      .sonatypeRepo("snapshots"), // TODO: Remove once scalacheck-shapeless has a stable version for 2.13.0-M5
    libraryDependencies ++= Seq(
      "org.typelevel"              %% "cats-effect-laws"                                                 % "1.2.0"                              % Test,
      "org.typelevel"              %% "cats-testkit"                                                     % "1.6.0"                              % Test,
      "com.github.alexarchambault" %% s"scalacheck-shapeless_${majorMinor(CatsScalaCheckVersion.value)}" % CatsScalaCheckShapelessVersion.value % Test
    ),
    dependencyOverrides += "org.scalacheck" %% "scalacheck" % ScalaCheckVersion.value % Test
  )
  .dependsOn(interopSharedJVM)

lazy val interopCatsJS = interopCats.js.dependsOn(interopSharedJS)

lazy val interopFuture = crossProject(JSPlatform, JVMPlatform)
  .in(file("interop-future"))
  .settings(stdSettings("zio-interop-future"))
  .dependsOn(core % "test->test;compile->compile")

lazy val interopFutureJVM = interopFuture.jvm.dependsOn(interopSharedJVM)

lazy val interopMonix = crossProject(JSPlatform, JVMPlatform)
  .in(file("interop-monix"))
  .settings(stdSettings("zio-interop-monix"))
  .settings(
    libraryDependencies ++= Seq(
      "io.monix" %%% "monix" % "3.0.0-RC2" % Optional
    )
  )
  .dependsOn(core % "test->test;compile->compile")

lazy val interopMonixJVM = interopMonix.jvm.dependsOn(interopSharedJVM)
lazy val interopMonixJS  = interopMonix.js.dependsOn(interopSharedJS)

lazy val interopScalaz7x = crossProject(JSPlatform, JVMPlatform)
  .in(file("interop-scalaz7x"))
  .settings(stdSettings("zio-interop-scalaz7x"))
  .settings(
    libraryDependencies ++= Seq(
      "org.scalaz" %%% "scalaz-core"               % "7.2.+" % Optional,
      "org.scalaz" %%% "scalaz-scalacheck-binding" % "7.2.+" % Test
    )
  )
  .dependsOn(core % "test->test;compile->compile")

lazy val interopScalaz7xJVM = interopScalaz7x.jvm.dependsOn(interopSharedJVM)
lazy val interopScalaz7xJS  = interopScalaz7x.js.dependsOn(interopSharedJS)

lazy val interopJava = crossProject(JVMPlatform)
  .in(file("interop-java"))
  .settings(stdSettings("zio-interop-java"))
  .dependsOn(core % "test->test;compile->compile")

lazy val interopJavaJVM = interopJava.jvm.dependsOn(interopSharedJVM)

val akkaVersion = "2.5.21"
lazy val interopReactiveStreams = crossProject(JVMPlatform)
  .in(file("interop-reactiveStreams"))
  .settings(stdSettings("zio-interop-reactiveStreams"))
  .settings(
    libraryDependencies ++= Seq(
      "org.reactivestreams" % "reactive-streams"     % "1.0.2",
      "org.reactivestreams" % "reactive-streams-tck" % "1.0.2" % "test",
      "org.scalatest"       %% "scalatest"           % "3.0.6" % "test",
      "com.typesafe.akka"   %% "akka-stream"         % akkaVersion % "test",
      "com.typesafe.akka"   %% "akka-stream-testkit" % akkaVersion % "test"
    )
  )
  .dependsOn(core % "test->test;compile->compile")

lazy val interopReactiveStreamsJVM = interopReactiveStreams.jvm.dependsOn(interopSharedJVM)

lazy val testkit = crossProject(JVMPlatform)
  .in(file("testkit"))
  .settings(stdSettings("zio-testkit"))
  .dependsOn(core % "test->test;compile->compile")

lazy val testkitJVM = testkit.jvm

lazy val benchmarks = project.module
  .dependsOn(coreJVM)
  .enablePlugins(JmhPlugin)
  .settings(replSettings)
  .settings(
    skip in publish := true,
    libraryDependencies ++=
      Seq(
        "org.scala-lang"           % "scala-reflect"    % scalaVersion.value,
        "org.scala-lang"           % "scala-compiler"   % scalaVersion.value % Provided,
        "io.monix"                 %% "monix"           % "3.0.0-RC2",
        "org.typelevel"            %% "cats-effect"     % "1.2.0",
        "co.fs2"                   %% "fs2-core"        % "1.0.3",
        "com.typesafe.akka"        %% "akka-stream"     % "2.5.20",
        "io.reactivex.rxjava2"     % "rxjava"           % "2.2.6",
        "com.twitter"              %% "util-collection" % "19.1.0",
        "io.projectreactor"        % "reactor-core"     % "3.2.5.RELEASE",
        "com.google.code.findbugs" % "jsr305"           % "3.0.2"
      ),
    unusedCompileDependenciesFilter -= libraryDependencies.value
      .map(moduleid => moduleFilter(organization = moduleid.organization, name = moduleid.name))
      .reduce(_ | _),
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
  .dependsOn(coreJVM, interopCatsJVM, interopFutureJVM, interopScalaz7xJVM, interopJavaJVM, interopReactiveStreamsJVM)
  .enablePlugins(MicrositesPlugin)
  .settings(
    scalacOptions -= "-Yno-imports",
    scalacOptions ~= { _ filterNot (_ startsWith "-Ywarn") },
    scalacOptions ~= { _ filterNot (_ startsWith "-Xlint") },
    skip in publish := true,
    libraryDependencies ++= Seq(
      "com.github.ghik" %% "silencer-lib" % "1.3.1" % Tut,
      "commons-io"      % "commons-io"    % "2.6"   % Tut
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
