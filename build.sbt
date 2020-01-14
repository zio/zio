// shadow sbt-scalajs' crossProject from Scala.js 0.6.x
import BuildHelper._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import sbtcrossproject.CrossPlugin.autoImport.crossProject

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
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    scmInfo := Some(
      ScmInfo(url("https://github.com/zio/zio/"), "scm:git:git@github.com:zio/zio.git")
    )
  )
)

ThisBuild / publishTo := sonatypePublishToBundle.value

addCommandAlias("build", "prepare; testJVM")
addCommandAlias("prepare", "fix; fmt")
addCommandAlias("fix", "all compile:scalafix test:scalafix")
addCommandAlias(
  "fixCheck",
  "; compile:scalafix --check ; test:scalafix --check"
)
addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll")
addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll")
addCommandAlias(
  "compileJVM",
  ";coreTestsJVM/test:compile;stacktracerJVM/test:compile;streamsTestsJVM/test:compile;testTestsJVM/test:compile;testRunnerJVM/test:compile;examplesJVM/test:compile"
)
addCommandAlias("compileNative", ";coreNative/compile")
addCommandAlias(
  "testJVM",
  ";coreTestsJVM/test;stacktracerJVM/test;streamsTestsJVM/test;testTestsJVM/test;testRunnerJVM/test:run;examplesJVM/test:compile;benchmarks/test:compile"
)
addCommandAlias(
  "testJVMNoBenchmarks",
  ";coreTestsJVM/test;stacktracerJVM/test;streamsTestsJVM/test;testTestsJVM/test;testRunnerJVM/test:run;examplesJVM/test:compile"
)
addCommandAlias(
  "testJVMDotty",
  ";coreTestsJVM/test;stacktracerJVM/test:compile;streamsTestsJVM/test;testTestsJVM/test;testRunnerJVM/test:run;examplesJVM/test:compile"
)
addCommandAlias(
  "testJS",
  ";coreTestsJS/test;stacktracerJS/test;streamsTestsJS/test;testTestsJS/test;examplesJS/test:compile"
)

lazy val root = project
  .in(file("."))
  .settings(
    skip in publish := true,
    console := (console in Compile in coreJVM).value,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library"),
    welcomeMessage
  )
  .aggregate(
    coreJVM,
    coreJS,
    coreTestsJVM,
    coreTestsJS,
    docs,
    streamsJVM,
    streamsJS,
    streamsTestsJVM,
    streamsTestsJS,
    benchmarks,
    testJVM,
    testJS,
    testTestsJVM,
    testTestsJS,
    stacktracerJS,
    stacktracerJVM,
    testRunnerJS,
    testRunnerJVM
  )
  .enablePlugins(ScalaJSPlugin)

lazy val core = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("core"))
  .dependsOn(stacktracer)
  .settings(stdSettings("zio"))
  .settings(buildInfoSettings("zio"))
  .enablePlugins(BuildInfoPlugin)

lazy val coreJVM = core.jvm
  .settings(dottySettings)
  .settings(replSettings)

lazy val coreJS = core.js

lazy val coreNative = core.native
  .settings(scalaVersion := "2.11.12")
  .settings(skip in Test := true)
  .settings(skip in doc := true)
  .settings(sources in (Compile, doc) := Seq.empty)
  .settings(
    libraryDependencies ++= Seq(
      "dev.whaling" %%% "native-loop-core"      % "0.1.1",
      "dev.whaling" %%% "native-loop-js-compat" % "0.1.1"
    )
  )

lazy val coreTests = crossProject(JSPlatform, JVMPlatform)
  .in(file("core-tests"))
  .dependsOn(core)
  .dependsOn(test)
  .settings(stdSettings("core-tests"))
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner)
  .settings(buildInfoSettings("zio"))
  .settings(skip in publish := true)
  .settings(Compile / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat)
  .enablePlugins(BuildInfoPlugin)

lazy val coreTestsJVM = coreTests.jvm
  .settings(dottySettings)
  .configure(_.enablePlugins(JCStressPlugin))
  .settings(replSettings)

lazy val coreTestsJS = coreTests.js
  .settings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0-RC3" % Test
  )

lazy val streams = crossProject(JSPlatform, JVMPlatform)
  .in(file("streams"))
  .dependsOn(core)
  .settings(stdSettings("zio-streams"))
  .settings(buildInfoSettings("zio.stream"))
  .settings(streamReplSettings)
  .enablePlugins(BuildInfoPlugin)

lazy val streamsJVM = streams.jvm.settings(dottySettings)
lazy val streamsJS  = streams.js

lazy val streamsTests = crossProject(JSPlatform, JVMPlatform)
  .in(file("streams-tests"))
  .dependsOn(streams)
  .dependsOn(coreTests % "test->test;compile->compile")
  .settings(stdSettings("core-tests"))
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner)
  .settings(buildInfoSettings("zio.stream"))
  .settings(skip in publish := true)
  .settings(Compile / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.AllLibraryJars)
  .enablePlugins(BuildInfoPlugin)

lazy val streamsTestsJVM = streamsTests.jvm
  .dependsOn(coreTestsJVM % "test->compile")
  .settings(dottySettings)

lazy val streamsTestsJS = streamsTests.js
  .settings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0-RC3" % Test
  )

lazy val test = crossProject(JSPlatform, JVMPlatform)
  .in(file("test"))
  .dependsOn(core, streams)
  .settings(stdSettings("zio-test"))
  .settings(
    scalacOptions += "-language:experimental.macros",
    libraryDependencies ++=
      Seq("org.portable-scala" %%% "portable-scala-reflect" % "0.1.1") ++ {
        if (isDotty.value) Seq()
        else Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided")
      }
  )

lazy val testJVM = test.jvm.settings(dottySettings)
lazy val testJS  = test.js

lazy val testTests = crossProject(JSPlatform, JVMPlatform)
  .in(file("test-tests"))
  .dependsOn(test)
  .settings(stdSettings("test-tests"))
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner)
  .settings(buildInfoSettings("zio.test"))
  .settings(skip in publish := true)
  .enablePlugins(BuildInfoPlugin)

lazy val testTestsJVM = testTests.jvm.settings(dottySettings)
lazy val testTestsJS = testTests.js.settings(
  libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0-RC3"
)

lazy val stacktracer = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("stacktracer"))
  .settings(stdSettings("zio-stacktracer"))
  .settings(buildInfoSettings("zio.internal.stacktracer"))

lazy val stacktracerJS = stacktracer.js
lazy val stacktracerJVM = stacktracer.jvm
  .settings(dottySettings)
  .settings(replSettings)

lazy val stacktracerNative = stacktracer.native
  .settings(scalaVersion := "2.11.12")
  .settings(skip in Test := true)
  .settings(skip in doc := true)
lazy val testRunner = crossProject(JVMPlatform, JSPlatform)
  .in(file("test-sbt"))
  .settings(stdSettings("zio-test-sbt"))
  .settings(
    libraryDependencies ++= Seq(
      "org.portable-scala" %%% "portable-scala-reflect" % "0.1.1"
    ),
    mainClass in (Test, run) := Some("zio.test.sbt.TestMain")
  )
  .jsSettings(libraryDependencies ++= Seq("org.scala-js" %% "scalajs-test-interface" % "0.6.31"))
  .jvmSettings(libraryDependencies ++= Seq("org.scala-sbt" % "test-interface" % "1.0"))
  .dependsOn(core)
  .dependsOn(test)

lazy val testRunnerJVM = testRunner.jvm.settings(dottySettings)
lazy val testRunnerJS = testRunner.js
  .settings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0-RC3" % Test
  )

/**
 * Examples sub-project that is not included in the root project.
 * To run tests :
 * `sbt "examplesJVM/test"`
 */
lazy val examples = crossProject(JVMPlatform, JSPlatform)
  .in(file("examples"))
  .settings(stdSettings("examples"))
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .jsSettings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0-RC3" % Test)
  .dependsOn(testRunner)

lazy val examplesJS  = examples.js
lazy val examplesJVM = examples.jvm.settings(dottySettings)

lazy val isScala211 = Def.setting {
  scalaVersion.value.startsWith("2.11")
}

lazy val benchmarks = project.module
  .dependsOn(coreJVM, streamsJVM, testJVM)
  .enablePlugins(JmhPlugin)
  .settings(replSettings)
  .settings(
    // skip 2.13 benchmarks until twitter-util publishes for 2.13
    crossScalaVersions -= "2.13.1",
    //
    skip in publish := true,
    libraryDependencies ++=
      Seq(
        "co.fs2"                   %% "fs2-core"        % "2.1.0",
        "com.google.code.findbugs" % "jsr305"           % "3.0.2",
        "com.twitter"              %% "util-collection" % "19.1.0",
        "com.typesafe.akka"        %% "akka-stream"     % "2.5.27",
        "io.monix"                 %% "monix"           % "3.1.0",
        "io.projectreactor"        % "reactor-core"     % "3.3.1.RELEASE",
        "io.reactivex.rxjava2"     % "rxjava"           % "2.2.16",
        "org.ow2.asm"              % "asm"              % "7.2",
        "org.scala-lang"           % "scala-compiler"   % scalaVersion.value % Provided,
        "org.scala-lang"           % "scala-reflect"    % scalaVersion.value,
        "org.typelevel"            %% "cats-effect"     % "2.0.0",
        "org.scalacheck"           %% "scalacheck"      % "1.14.3",
        "hedgehog"                 %% "hedgehog-core"   % "0.1.0"
      ),
    libraryDependencies ++= {
      if (isScala211.value) Nil
      else Seq("com.github.japgolly.nyaya" %% "nyaya-gen" % "0.9.0-RC1")
    },
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
    ),
    resolvers += Resolver.url("bintray-scala-hedgehog", url("https://dl.bintray.com/hedgehogqa/scala-hedgehog"))(
      Resolver.ivyStylePatterns
    )
  )

lazy val docs = project.module
  .in(file("zio-docs"))
  .settings(
    // skip 2.13 mdoc until mdoc is available for 2.13
    crossScalaVersions -= "2.13.1",
    //
    skip.in(publish) := true,
    moduleName := "zio-docs",
    unusedCompileDependenciesFilter -= moduleFilter("org.scalameta", "mdoc"),
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    scalacOptions ~= { _ filterNot (_ startsWith "-Ywarn") },
    scalacOptions ~= { _ filterNot (_ startsWith "-Xlint") },
    libraryDependencies ++= Seq(
      "com.github.ghik"     % "silencer-lib"                 % "1.4.4" % Provided cross CrossVersion.full,
      "commons-io"          % "commons-io"                   % "2.6" % "provided",
      "org.jsoup"           % "jsoup"                        % "1.12.1" % "provided",
      "org.reactivestreams" % "reactive-streams-examples"    % "1.0.3" % "provided",
      "dev.zio"             %% "zio-interop-cats"            % "2.0.0.0-RC10",
      "dev.zio"             %% "zio-interop-future"          % "2.12.8.0-RC6",
      "dev.zio"             %% "zio-interop-monix"           % "3.0.0.0-RC7",
      "dev.zio"             %% "zio-interop-scalaz7x"        % "7.2.27.0-RC7",
      "dev.zio"             %% "zio-interop-java"            % "1.1.0.0-RC6",
      "dev.zio"             %% "zio-interop-reactivestreams" % "1.0.3.5-RC2",
      "dev.zio"             %% "zio-interop-twitter"         % "19.7.0.0-RC2",
      "dev.zio"             %% "zio-macros-core"             % "0.6.0",
      "dev.zio"             %% "zio-macros-test"             % "0.6.0"
    )
  )
  .settings(macroSettings)
  .dependsOn(
    coreJVM,
    streamsJVM,
    coreTestsJVM
  )
  .enablePlugins(MdocPlugin, DocusaurusPlugin)

scalafixDependencies in ThisBuild += "com.nequissimus" %% "sort-imports" % "0.3.1"
