// shadow sbt-scalajs' crossProject from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import BuildHelper._
import xerial.sbt.Sonatype._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue

name := "zio"

inThisBuild(
  List(
    organization := "dev.zio",
    homepage := Some(url("https://zio.dev")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("http://degoes.net")
      )
    ),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    releaseEarlyWith := SonatypePublisher,
    scmInfo := Some(
      ScmInfo(url("https://github.com/zio/zio/"), "scm:git:git@github.com:zio/zio.git")
    )
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
addCommandAlias("compileJVM", ";coreJVM/test:compile;stacktracerJVM/test:compile")
addCommandAlias("testJVM", ";coreJVM/test;stacktracerJVM/test;streamsJVM/test;testkitJVM/test")
addCommandAlias("testJS", ";coreJS/test;stacktracerJS/test;streamsJS/test")

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
    docs,
    streamsJVM,
    streamsJS,
    benchmarks,
    testkitJVM,
    stacktracerJS,
    stacktracerJVM
  )
  .enablePlugins(ScalaJSPlugin)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .in(file("core"))
  .dependsOn(stacktracer)
  .settings(stdSettings("zio"))
  .settings(buildInfoSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.specs2" %%% "specs2-core"          % "4.6.0" % Test,
      "org.specs2" %%% "specs2-scalacheck"    % "4.6.0" % Test,
      "org.specs2" %%% "specs2-matcher-extra" % "4.6.0" % Test
    ),
    publishArtifact in (Test, packageBin) := true
  )
  .enablePlugins(BuildInfoPlugin)

lazy val coreJVM = core.jvm
  .configure(_.enablePlugins(JCStressPlugin))
  .settings(dottySettings)
  .settings(replSettings)

lazy val coreJS = core.js
  .settings(
    libraryDependencies += "org.scala-js" %%% "scalajs-java-time" % "0.2.5" % Test
  )

lazy val streams = crossProject(JSPlatform, JVMPlatform)
  .in(file("streams"))
  .settings(stdSettings("zio-streams"))
  .settings(buildInfoSettings)
  .settings(replSettings)
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(core % "test->test;compile->compile")

lazy val streamsJVM = streams.jvm
lazy val streamsJS  = streams.js

lazy val testkit = crossProject(JVMPlatform)
  .in(file("testkit"))
  .settings(stdSettings("zio-testkit"))
  .dependsOn(core % "test->test;compile->compile")

lazy val testkitJVM = testkit.jvm

lazy val stacktracer = crossProject(JSPlatform, JVMPlatform)
  .in(file("stacktracer"))
  .settings(stdSettings("zio-stacktracer"))
  .settings(buildInfoSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.specs2" %%% "specs2-core"          % "4.6.0" % Test,
      "org.specs2" %%% "specs2-scalacheck"    % "4.6.0" % Test,
      "org.specs2" %%% "specs2-matcher-extra" % "4.6.0" % Test
    )
  )

lazy val stacktracerJS = stacktracer.js
lazy val stacktracerJVM = stacktracer.jvm
  .settings(dottySettings)
  .settings(replSettings)

lazy val benchmarks = project.module
  .dependsOn(coreJVM, streamsJVM)
  .enablePlugins(JmhPlugin)
  .settings(replSettings)
  .settings(
    skip in publish := true,
    libraryDependencies ++=
      Seq(
        "co.fs2"                   %% "fs2-core"        % "1.0.5",
        "com.google.code.findbugs" % "jsr305"           % "3.0.2",
        "com.twitter"              %% "util-collection" % "19.1.0",
        "com.typesafe.akka"        %% "akka-stream"     % "2.5.23",
        "io.monix"                 %% "monix"           % "3.0.0-RC2",
        "io.projectreactor"        % "reactor-core"     % "3.2.10.RELEASE",
        "io.reactivex.rxjava2"     % "rxjava"           % "2.2.10",
        "org.ow2.asm"              % "asm"              % "7.1",
        "org.scala-lang"           % "scala-compiler"   % scalaVersion.value % Provided,
        "org.scala-lang"           % "scala-reflect"    % scalaVersion.value,
        "org.typelevel"            %% "cats-effect"     % "1.3.1"
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

lazy val docs = project.module
  .in(file("zio-docs"))
  .settings(
    skip.in(publish) := true,
    moduleName := "zio-docs",
    unusedCompileDependenciesFilter -= moduleFilter("org.scalameta", "mdoc"),
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    scalacOptions ~= { _ filterNot (_ startsWith "-Ywarn") },
    scalacOptions ~= { _ filterNot (_ startsWith "-Xlint") },
    libraryDependencies ++= Seq(
      "com.github.ghik"     %% "silencer-lib"                % "1.4.1" % "provided",
      "commons-io"          % "commons-io"                   % "2.6" % "provided",
      "org.jsoup"           % "jsoup"                        % "1.12.1" % "provided",
      "org.reactivestreams" % "reactive-streams-examples"    % "1.0.2" % "provided",
      "dev.zio"             %% "zio-interop-cats"            % "1.3.1.0-RC2",
      "dev.zio"             %% "zio-interop-future"          % "2.12.8.0-RC1",
      "dev.zio"             %% "zio-interop-monix"           % "3.0.0.0-RC2",
      "dev.zio"             %% "zio-interop-scalaz7x"        % "7.2.27.0-RC1",
      "dev.zio"             %% "zio-interop-java"            % "1.1.0.0-RC1",
      "dev.zio"             %% "zio-interop-reactivestreams" % "1.0.2.0-RC2",
      "dev.zio"             %% "zio-interop-twitter"         % "19.6.0.0-RC2"
    )
  )
  .dependsOn(
    coreJVM,
    streamsJVM
  )
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
