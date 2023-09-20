import BuildHelper._
import MimaSettings.mimaSettings
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import sbt.Keys

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev")),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
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

addCommandAlias("build", "; fmt; rootJVM/test")
addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll")
addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll")
addCommandAlias(
  "check",
  "; scalafmtSbtCheck; scalafmtCheckAll"
)

addCommandAlias(
  "compileJVM",
  ";coreTestsJVM/test:compile;stacktracerJVM/test:compile;streamsTestsJVM/test:compile;testTestsJVM/test:compile;testMagnoliaTestsJVM/test:compile;testRefinedJVM/test:compile;testRunnerJVM/test:compile;examplesJVM/test:compile;macrosTestsJVM/test:compile;concurrentJVM/test:compile;managedTestsJVM/test:compile"
)
addCommandAlias(
  "testNative",
  ";coreTestsNative/test;stacktracerNative/test;streamsTestsNative/test;testTestsNative/test;examplesNative/Test/compile;macrosTestsNative/test;concurrentNative/test"
)
addCommandAlias(
  "testJVM",
  ";coreTestsJVM/test;stacktracerJVM/test;streamsTestsJVM/test;testTestsJVM/test;testMagnoliaTestsJVM/test;testRefinedJVM/test;testRunnerJVM/test;testRunnerJVM/test:run;examplesJVM/test:compile;benchmarks/test:compile;macrosTestsJVM/test;testJunitRunnerTestsJVM/test;concurrentJVM/test;managedTestsJVM/test"
)
addCommandAlias(
  "testJVMNoBenchmarks",
  ";coreTestsJVM/test;stacktracerJVM/test;streamsTestsJVM/test;testTestsJVM/test;testMagnoliaTestsJVM/test;testRefinedJVM/test:compile;testRunnerJVM/test:run;examplesJVM/test:compile;concurrentJVM/test;managedTestsJVM/test"
)
addCommandAlias(
  "testJVM3",
  ";coreTestsJVM/test;stacktracerJVM/test:compile;streamsTestsJVM/test;testTestsJVM/test;testMagnoliaTestsJVM/test;testRefinedJVM/test;testRunnerJVM/test:run;examplesJVM/test:compile;concurrentJVM/test;managedTestsJVM/test"
)
addCommandAlias(
  "testJS3",
  ";coreTestsJS/test;stacktracerJS/test;streamsTestsJS/test;testTestsJS/test;testMagnoliaTestsJS/test;testRefinedJS/test;examplesJS/test:compile;concurrentJS/test"
)
addCommandAlias(
  "testJS",
  ";coreTestsJS/test;stacktracerJS/test;streamsTestsJS/test;testTestsJS/test;testMagnoliaTestsJS/test;testRefinedJS/test;examplesJS/test:compile;macrosTestsJS/test;concurrentJS/test"
)
addCommandAlias(
  "mimaChecks",
  "all coreJVM/mimaReportBinaryIssues streamsJVM/mimaReportBinaryIssues testsJVM/mimaReportBinaryIssues"
)

lazy val projectsCommon = List(
  concurrent,
  core,
  coreTests,
  examples,
  internalMacros,
  macros,
  macrosTests,
  managed,
  managedTests,
  stacktracer,
  streams,
  streamsTests,
  tests,
  testRunner,
  testTests
)

lazy val rootJVM = project.in(file("target/rootJVM")).settings(publish / skip := true).aggregate(rootJVM213)

lazy val rootJVM212 = project.in(file("target/rootJVM212")).settings(publish / skip := true).aggregate(rootJVM213)

lazy val rootJVM213 = project
  .in(file("target/rootJVM213"))
  .settings(publish / skip := true)
  .aggregate(projectsCommon.map(p => p.jvm: ProjectReference): _*)
  .aggregate(
    List[ProjectReference](
      benchmarks,
      scalafixTests,
      testJunitRunner,
      testJunitRunnerTests,
      testMagnolia.jvm,
      testMagnoliaTests.jvm,
      testRefined.jvm,
      testScalaCheck.jvm
    ): _*
  )

lazy val rootJVM3 = project
  .in(file("target/rootJVM3"))
  .settings(publish / skip := true)
  .aggregate(projectsCommon.map(p => p.jvm: ProjectReference): _*)
  .aggregate(
    List[ProjectReference](
      testJunitRunner,
//      testJunitRunnerTests, TODO: fix test
      testMagnolia.jvm,
      testMagnoliaTests.jvm,
      testRefined.jvm,
      testScalaCheck.jvm
    ): _*
  )

lazy val rootJS = project
  .in(file("target/rootJS"))
  .settings(publish / skip := true)
  .aggregate(projectsCommon.map(p => p.js: ProjectReference): _*)
  .aggregate(
    List[ProjectReference](
      testMagnolia.js,
      testMagnoliaTests.js,
      testRefined.js,
      testScalaCheck.js
    ): _*
  )

lazy val rootNative = project
  .in(file("target/rootNative"))
  .settings(publish / skip := true)
  .aggregate(projectsCommon.map(_.native: ProjectReference): _*)
  .aggregate(
    List[ProjectReference](
      testScalaCheck.native
    ): _*
  )

lazy val root212 = project.in(file("target/root212")).settings(publish / skip := true).aggregate(root213)

lazy val root213 = project
  .in(file("target/root213"))
  .settings(publish / skip := true)
  .aggregate(
    (projectsCommon.flatMap(p => List[ProjectReference](p.jvm, p.js, p.native)) ++
      List(
        testScalaCheck
      ).flatMap(p => List[ProjectReference](p.jvm, p.js, p.native)) ++
      List(
        testMagnolia,
        testMagnoliaTests,
        testRefined
      ).flatMap(p => List[ProjectReference](p.jvm, p.js)) ++
      List[ProjectReference](
        benchmarks,
        scalafixTests,
        testJunitRunner,
        testJunitRunnerTests
      )): _*
  )

lazy val root3 = project
  .in(file("target/root3"))
  .settings(publish / skip := true)
  .aggregate(
    (projectsCommon.flatMap(p => List[ProjectReference](p.jvm, p.js, p.native)) ++
      List(
        testScalaCheck
      ).flatMap(p => List[ProjectReference](p.jvm, p.js, p.native)) ++
      List(
        testMagnolia,
        testMagnoliaTests,
        testRefined
      ).flatMap(p => List[ProjectReference](p.jvm, p.js)) ++
      List[ProjectReference](
        testJunitRunner,
        testJunitRunnerTests
      )): _*
  )

val catsEffectVersion = "3.5.1"
val fs2Version        = "3.9.2"

lazy val root = project
  .in(file("."))
  .settings(
    name           := "zio",
    publish / skip := true,
    console        := (core.jvm / Compile / console).value,
    unusedCompileDependenciesFilter -= moduleFilter(
      "org.scala-js",
      "scalajs-library"
    ),
    welcomeMessage
  )
  .aggregate(root213)
  .enablePlugins(ScalaJSPlugin)

lazy val core = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("core"))
  .dependsOn(internalMacros, stacktracer)
  .settings(stdSettings("zio"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio"))
  .settings(libraryDependencies += "dev.zio" %%% "izumi-reflect" % "2.3.8")
  .enablePlugins(BuildInfoPlugin)
  .settings(macroDefinitionSettings)
  .settings(scalacOptions += "-Wconf:msg=[zio.stacktracer.TracingImplicits.disableAutoTrace]:silent")
  .jvmSettings(
    replSettings,
    mimaSettings(failOnProblem = true)
  )
  .jsSettings(
    jsSettings,
    libraryDependencies ++= List(
      "org.scala-js"  %%% "scala-js-macrotask-executor" % "1.1.1",
      ("org.scala-js" %%% "scalajs-weakreferences"      % "1.0.0").cross(CrossVersion.for3Use2_13),
      "org.scala-js"  %%% "scalajs-dom"                 % "2.7.0"
    ),
    scalacOptions ++= {
      if (scalaVersion.value == Scala3) {
        List()
      } else {
        // Temporarily disable warning to use `MacrotaskExecutor` https://github.com/zio/zio/issues/6308
        List("-P:scalajs:nowarnGlobalExecutionContext")
      }
    }
  )
  .nativeSettings(
    nativeSettings,
    libraryDependencies ++= Seq(
      "com.github.lolgab" %%% "native-loop-core" % "0.2.1"
    )
  )

lazy val coreTests = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("core-tests"))
  .dependsOn(core)
  .dependsOn(tests)
  .settings(stdSettings("core-tests"))
  .settings(crossProjectSettings)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner)
  .settings(buildInfoSettings("zio"))
  .settings(publish / skip := true)
  .settings(
    Compile / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
  )
  .enablePlugins(BuildInfoPlugin)
  .jvmConfigure(_.enablePlugins(JCStressPlugin))
  .jvmSettings(replSettings)
  .jsSettings(
    jsSettings,
    scalacOptions ++= {
      if (scalaVersion.value == Scala3) {
        List()
      } else {
        List("-P:scalajs:nowarnGlobalExecutionContext")
      }
    }
  )
  .nativeSettings(nativeSettings)

lazy val managed = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("managed"))
  .dependsOn(core, streams)
  .settings(stdSettings("zio-managed"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.managed"))
  .settings(streamReplSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(macroDefinitionSettings)
  .settings(scalacOptions += "-Wconf:msg=[zio.stacktracer.TracingImplicits.disableAutoTrace]:silent")
  .settings(scalacOptions += "-Wconf:msg=[@nowarn annotation does not suppress any warnings]:silent")
  .jvmSettings(
    mimaSettings(failOnProblem = false)
  )
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)

lazy val managedTests = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("managed-tests"))
  .dependsOn(managed)
  .dependsOn(tests)
  .settings(stdSettings("managed-tests"))
  .settings(crossProjectSettings)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner)
  .settings(buildInfoSettings("zio"))
  .settings(publish / skip := true)
  .settings(
    Compile / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
  )
  .enablePlugins(BuildInfoPlugin)
  .jvmConfigure(_.enablePlugins(JCStressPlugin))
  .jvmSettings(replSettings)
  .jsSettings(
    jsSettings,
    scalacOptions ++= {
      if (scalaVersion.value == Scala3) {
        List()
      } else {
        List("-P:scalajs:nowarnGlobalExecutionContext")
      }
    }
  )
  .nativeSettings(nativeSettings)

lazy val macros = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("macros"))
  .dependsOn(core, managed)
  .settings(stdSettings("zio-macros"))
  .settings(crossProjectSettings)
  .settings(macroDefinitionSettings)
  .settings(macroExpansionSettings)
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)
  .settings(scalacOptions += "-Wconf:msg=[@nowarn annotation does not suppress any warnings]:silent")

lazy val macrosTests = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("macros-tests"))
  .dependsOn(macros)
  .settings(stdSettings("macros-tests"))
  .settings(crossProjectSettings)
  .settings(macroDefinitionSettings)
  .settings(macroExpansionSettings)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner)
  .settings(buildInfoSettings("zio"))
  .settings(publish / skip := true)
  .enablePlugins(BuildInfoPlugin)
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)

lazy val internalMacros = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("internal-macros"))
  .settings(stdSettings("zio-internal-macros"))
  .settings(crossProjectSettings)
  .settings(macroDefinitionSettings)
  .settings(macroExpansionSettings)
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)

lazy val streams = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("streams"))
  .dependsOn(core)
  .settings(stdSettings("zio-streams"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.stream"))
  .settings(streamReplSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(macroDefinitionSettings)
  .settings(scalacOptions += "-Wconf:msg=[zio.stacktracer.TracingImplicits.disableAutoTrace]:silent")
  .settings(scalacOptions += "-Wconf:msg=[@nowarn annotation does not suppress any warnings]:silent")
  .jvmSettings(mimaSettings(failOnProblem = true))
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)

lazy val streamsTests = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("streams-tests"))
  .dependsOn(streams)
  .dependsOn(coreTests % "test->test;compile->compile")
  .settings(stdSettings("streams-tests"))
  .settings(crossProjectSettings)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner)
  .settings(buildInfoSettings("zio.stream"))
  .settings(publish / skip := true)
  .settings(
    Compile / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.AllLibraryJars
  )
  .enablePlugins(BuildInfoPlugin)
  .jvmConfigure(_.dependsOn(coreTests.jvm % "test->compile"))
  .jsSettings(
    jsSettings,
    scalacOptions ++= {
      if (scalaVersion.value == Scala3) {
        List()
      } else {
        List("-P:scalajs:nowarnGlobalExecutionContext")
      }
    }
  )
  .nativeSettings(nativeSettings)

lazy val tests = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("test"))
  .dependsOn(core, streams)
  .settings(stdSettings("zio-test"))
  .settings(crossProjectSettings)
  .settings(macroDefinitionSettings)
  .settings(macroExpansionSettings)
  .settings(
    libraryDependencies ++= Seq(
      ("org.portable-scala" %%% "portable-scala-reflect" % "1.1.2")
        .cross(CrossVersion.for3Use2_13)
    )
  )
  .settings(scalacOptions += "-Wconf:msg=[zio.stacktracer.TracingImplicits.disableAutoTrace]:silent")
  .settings(scalacOptions += "-Wconf:msg=[@nowarn annotation does not suppress any warnings]:silent")
  .jvmSettings(mimaSettings(failOnProblem = true))
  .jsSettings(
    jsSettings,
    libraryDependencies ++= List(
      "io.github.cquiroz" %%% "scala-java-time"      % "2.5.0",
      "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.5.0"
    )
  )
  .nativeSettings(
    nativeSettings,
    libraryDependencies ++= List(
      "io.github.cquiroz" %%% "scala-java-time"      % "2.5.0",
      "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.5.0",
      "com.github.lolgab" %%% "scala-native-crypto"  % "0.0.4"
    )
  )

lazy val testTests = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("test-tests"))
  .dependsOn(tests)
  .settings(stdSettings("test-tests"))
  .settings(crossProjectSettings)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner)
  .settings(buildInfoSettings("zio.test"))
  .settings(publish / skip := true)
  .settings(macroExpansionSettings)
  .enablePlugins(BuildInfoPlugin)
  .jsSettings(
    jsSettings,
    libraryDependencies ++= List(
      ("org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0").cross(CrossVersion.for3Use2_13)
    )
  )
  .nativeSettings(nativeSettings)

lazy val testMagnolia = crossProject(JVMPlatform, JSPlatform)
  .in(file("test-magnolia"))
  .dependsOn(tests)
  .settings(stdSettings("zio-test-magnolia"))
  .settings(crossProjectSettings)
  .settings(macroDefinitionSettings)
  .settings(
    scalacOptions ++= {
      if (scalaVersion.value == Scala3)
        Seq.empty
      else
        Seq("-language:experimental.macros")
    },
    libraryDependencies ++= {
      if (scalaVersion.value == Scala3)
        Seq(
          ("com.softwaremill.magnolia1_3" %%% "magnolia" % "1.3.3")
            .exclude("org.scala-lang", "scala-compiler")
        )
      else
        Seq(
          ("com.softwaremill.magnolia1_2" %%% "magnolia" % "1.1.6")
            .exclude("org.scala-lang", "scala-compiler")
        )
    }
  )
  .jsSettings(jsSettings)

lazy val testMagnoliaTests = crossProject(JVMPlatform, JSPlatform)
  .in(file("test-magnolia-tests"))
  .dependsOn(testMagnolia)
  .dependsOn(testTests % "test->test;compile->compile")
  .settings(stdSettings("test-magnolia-tests"))
  .settings(crossProjectSettings)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner)
  .settings(buildInfoSettings("zio.test"))
  .settings(
    publish / skip := true
  )
  .jsSettings(jsSettings)
  .enablePlugins(BuildInfoPlugin)

lazy val testRefined = crossProject(JVMPlatform, JSPlatform)
  .in(file("test-refined"))
  .dependsOn(testMagnolia)
  .settings(stdSettings("zio-test-refined"))
  .settings(crossProjectSettings)
  .settings(macroDefinitionSettings)
  .settings(
    libraryDependencies ++=
      Seq(
        ("eu.timepit" %% "refined" % "0.11.0").cross(CrossVersion.for3Use2_13)
      )
  )
  .jsSettings(jsSettings)

lazy val testScalaCheck = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("test-scalacheck"))
  .dependsOn(tests)
  .settings(stdSettings("zio-test-scalacheck"))
  .settings(crossProjectSettings)
  .settings(
    libraryDependencies ++= Seq(
      ("org.scalacheck" %%% "scalacheck" % "1.17.0")
    )
  )
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)

lazy val stacktracer = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("stacktracer"))
  .settings(stdSettings("zio-stacktracer"))
  .settings(crossProjectSettings)
  .settings(macroDefinitionSettings)
  .settings(buildInfoSettings("zio.internal.stacktracer"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(replSettings)
  .jsSettings(jsSettings)
  .nativeSettings(
    nativeSettings,
    scalacOptions -= "-Xfatal-warnings" // Issue 3112
  )

lazy val testRunner = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("test-sbt"))
  .settings(stdSettings("zio-test-sbt"))
  .settings(crossProjectSettings)
  .settings(Test / run / mainClass := Some("zio.test.sbt.TestMain"))
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .settings(scalacOptions += "-Wconf:msg=[zio.stacktracer.TracingImplicits.disableAutoTrace]:silent")
  .settings(scalacOptions += "-Wconf:msg=[@nowarn annotation does not suppress any warnings]:silent")
  .dependsOn(core)
  .dependsOn(tests)
  .jvmSettings(libraryDependencies ++= Seq("org.scala-sbt" % "test-interface" % "1.0"))
  .jsSettings(
    jsSettings,
    libraryDependencies ++= Seq(
      ("org.scala-js" %% "scalajs-test-interface" % scalaJSVersion).cross(CrossVersion.for3Use2_13)
    )
  )
  .nativeSettings(
    nativeSettings,
    libraryDependencies ++= Seq("org.scala-native" %%% "test-interface" % nativeVersion)
  )

lazy val testJunitRunner = crossProject(JVMPlatform) // TODO: make plain project, nothing cross about this
  .in(file("test-junit"))
  .settings(stdSettings("zio-test-junit"))
  .settings(crossProjectSettings)
  .settings(libraryDependencies ++= Seq("junit" % "junit" % "4.13.2"))
  .dependsOn(tests)
  .jvm

lazy val testJunitRunnerTests = crossProject(JVMPlatform) // TODO: make plain project, nothing cross about this
  .in(file("test-junit-tests"))
  .settings(stdSettings("test-junit-tests"))
  .settings(crossProjectSettings)
  .settings(Test / fork := true)
  .settings(Test / javaOptions ++= {
    Seq(
      s"-Dproject.dir=${baseDirectory.value}",
      s"-Dproject.version=${version.value}",
      s"-Dscala.version=${scalaVersion.value}",
      s"-Dscala.compat.version=${scalaBinaryVersion.value}"
    )
  })
  .settings(publish / skip := true)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .settings(
    libraryDependencies ++= Seq(
      "junit"                   % "junit"     % "4.13.2" % Test,
      "org.scala-lang.modules" %% "scala-xml" % "2.2.0"  % Test,
      // required to run embedded maven in the tests
      "org.apache.maven"       % "maven-embedder"         % "3.9.4"  % Test,
      "org.apache.maven"       % "maven-compat"           % "3.9.4"  % Test,
      "org.apache.maven.wagon" % "wagon-http"             % "3.5.3"  % Test,
      "org.eclipse.aether"     % "aether-connector-basic" % "1.1.0"  % Test,
      "org.eclipse.aether"     % "aether-transport-wagon" % "1.1.0"  % Test,
      "org.slf4j"              % "slf4j-simple"           % "1.7.36" % Test
    )
  )
  .dependsOn(
    tests,
    testRunner
  )
  // publish locally so embedded maven runs against locally compiled zio
  .settings(
    Test / Keys.test :=
      (Test / Keys.test)
        .dependsOn(testJunitRunner / publishM2)
        .dependsOn(tests.jvm / publishM2)
        .dependsOn(core.jvm / publishM2)
        .dependsOn(streams.jvm / publishM2)
        .dependsOn(stacktracer.jvm / publishM2)
        .value
  )
  .jvm

lazy val concurrent = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("concurrent"))
  .dependsOn(core)
  .settings(stdSettings("zio-concurrent"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.concurrent"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(testRunner % Test)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)
  .settings(scalacOptions += "-Wconf:msg=[@nowarn annotation does not suppress any warnings]:silent")

/**
 * Examples sub-project that is not included in the root project.
 *
 * To run tests: `sbt "examplesJVM/test"`
 */
lazy val examples = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("examples"))
  .settings(stdSettings("examples"))
  .settings(crossProjectSettings)
  .settings(macroExpansionSettings)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .settings(publish / skip := true)
  .settings(Test / test := (Test / compile).value)
  .dependsOn(macros, testRunner)
  .jvmConfigure(_.dependsOn(testJunitRunner))
  .jsSettings(
    jsSettings,
    libraryDependencies ++= List(
      ("org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0").cross(CrossVersion.for3Use2_13)
    )
  )
  .nativeSettings(nativeSettings)

lazy val benchmarks = project.module
  .dependsOn(core.jvm, streams.jvm, tests.jvm)
  .enablePlugins(JmhPlugin)
  .settings(replSettings)
  .settings(
    crossScalaVersions --= List(Scala3),
    publish / skip := true,
    libraryDependencies ++=
      Seq(
        "co.fs2"                    %% "fs2-core"        % fs2Version,
        "com.google.code.findbugs"   % "jsr305"          % "3.0.2",
        "com.twitter"               %% "util-core"       % "22.12.0",
        "com.typesafe.akka"         %% "akka-stream"     % "2.8.4",
        "io.github.timwspence"      %% "cats-stm"        % "0.13.4",
        "io.projectreactor"          % "reactor-core"    % "3.5.10",
        "io.reactivex.rxjava2"       % "rxjava"          % "2.2.21",
        "org.jctools"                % "jctools-core"    % "4.0.1",
        "org.ow2.asm"                % "asm"             % "9.5",
        "org.scala-lang"             % "scala-compiler"  % scalaVersion.value % Provided,
        "org.scala-lang"             % "scala-reflect"   % scalaVersion.value,
        "org.typelevel"             %% "cats-effect"     % catsEffectVersion,
        "org.typelevel"             %% "cats-effect-std" % catsEffectVersion,
        "org.scalacheck"            %% "scalacheck"      % "1.17.0",
        "qa.hedgehog"               %% "hedgehog-core"   % "0.10.1",
        "com.github.japgolly.nyaya" %% "nyaya-gen"       % "0.10.0",
        "org.springframework"        % "spring-core"     % "6.0.12"
      ),
    unusedCompileDependenciesFilter -= libraryDependencies.value
      .map(moduleid =>
        moduleFilter(
          organization = moduleid.organization,
          name = moduleid.name
        )
      )
      .reduce(_ | _),
    Compile / console / scalacOptions := Seq(
      "-language:higherKinds",
      "-language:existentials",
      "-Xsource:2.13",
      "-Yrepl-class-based"
    )
  )
  .settings(scalacOptions += "-Wconf:msg=[@nowarn annotation does not suppress any warnings]:silent")

lazy val jsdocs = project
  .settings(libraryDependencies += ("org.scala-js" %%% "scalajs-dom" % "2.7.0").cross(CrossVersion.for3Use2_13))
  .enablePlugins(ScalaJSPlugin)

val http4sV     = "0.23.23"
val doobieV     = "1.0.0-RC2"
val catsEffectV = "3.5.1"
val zioActorsV  = "0.1.0"

lazy val scalafixSettings = List(
  scalaVersion := Scala213,
  addCompilerPlugin(scalafixSemanticdb),
  crossScalaVersions --= List(Scala212, Scala3),
  scalacOptions ++= List(
    "-Yrangepos",
    "-P:semanticdb:synthetics:on"
  )
)

lazy val scalafixRules = project.module
  .in(file("scalafix/rules")) // TODO .in needed when name matches?
  .settings(
    scalafixSettings,
    semanticdbEnabled                      := true, // enable SemanticDB
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % "0.10.4"
  )

val zio1Version = "1.0.18"

lazy val scalafixInput = project
  .in(file("scalafix/input"))
  .settings(
    scalafixSettings,
    publish / skip                   := true,
    libraryDependencies += "dev.zio" %% "zio"         % zio1Version,
    libraryDependencies += "dev.zio" %% "zio-streams" % zio1Version,
    libraryDependencies += "dev.zio" %% "zio-test"    % zio1Version
  )

lazy val scalafixOutput = project
  .in(file("scalafix/output"))
  .settings(
    scalafixSettings,
    publish / skip := true
  )
  .dependsOn(core.jvm, tests.jvm, streams.jvm, managed.jvm)

lazy val scalafixTests = project
  .in(file("scalafix/tests"))
  .settings(
    scalafixSettings,
    publish / skip                        := true,
    libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % "0.10.1" % Test cross CrossVersion.full,
    Compile / compile :=
      (Compile / compile).dependsOn(scalafixInput / Compile / compile).value,
    scalafixTestkitOutputSourceDirectories :=
      (scalafixOutput / Compile / sourceDirectories).value,
    scalafixTestkitInputSourceDirectories :=
      (scalafixInput / Compile / sourceDirectories).value,
    scalafixTestkitInputClasspath :=
      (scalafixInput / Compile / fullClasspath).value
  )
  .dependsOn(scalafixRules)
  .enablePlugins(ScalafixTestkitPlugin)

lazy val docs_make_zio_app_configurable =
  project
    .in(file("documentation/guides/tutorials/make-a-zio-app-configurable"))

lazy val docs = project.module
  .in(file("zio-docs"))
  .settings(
    publish / skip := true,
    moduleName     := "zio-docs",
    scalaVersion   := Scala213,
    unusedCompileDependenciesFilter -= moduleFilter("org.scalameta", "mdoc"),
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    scalacOptions += "-Wconf:any:s",
    Compile / fork := false,
    scalacOptions ~= { _ filterNot (_ startsWith "-Ywarn") },
    scalacOptions ~= { _ filterNot (_ startsWith "-Xlint") },
    crossScalaVersions --= List(Scala212, Scala3),
    mdocIn  := (LocalRootProject / baseDirectory).value / "docs",
    mdocOut := (LocalRootProject / baseDirectory).value / "website" / "docs",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(
      core.jvm,
      streams.jvm,
      tests.jvm,
      testMagnolia.jvm,
      testRefined.jvm,
      testScalaCheck.jvm
    ),
    ScalaUnidoc / unidoc / target := (LocalRootProject / baseDirectory).value / "website" / "static" / "api",
    cleanFiles += (ScalaUnidoc / unidoc / target).value,
    docusaurusCreateSite     := docusaurusCreateSite.dependsOn(Compile / unidoc).value,
    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(Compile / unidoc).value,
    libraryDependencies ++= Seq(
      "commons-io"             % "commons-io"                  % "2.13.0" % "provided",
      "io.d11"                %% "zhttp"                       % "2.0.0-RC10",
      "io.7mind.izumi"        %% "distage-core"                % "1.1.0",
      "io.7mind.izumi"        %% "logstage-core"               % "1.1.0",
      "org.jsoup"              % "jsoup"                       % "1.16.1" % "provided",
      "org.reactivestreams"    % "reactive-streams-examples"   % "1.0.4"  % "provided",
      "org.typelevel"         %% "cats-effect"                 % catsEffectV,
      "dev.zio"               %% "zio-actors"                  % zioActorsV,
      "dev.zio"               %% "zio-akka-cluster"            % "0.3.0",
      "dev.zio"               %% "zio-cache"                   % "0.2.3",
      "dev.zio"               %% "zio-config"                  % "3.0.7",
      "dev.zio"               %% "zio-config-magnolia"         % "3.0.7",
      "dev.zio"               %% "zio-config-typesafe"         % "3.0.7",
      "dev.zio"               %% "zio-config-refined"          % "3.0.7",
      "dev.zio"               %% "zio-ftp"                     % "0.4.1",
      "dev.zio"               %% "zio-json"                    % "0.6.2",
      "dev.zio"               %% "zio-kafka"                   % "2.4.2",
      "dev.zio"               %% "zio-logging"                 % "2.1.14",
      "dev.zio"               %% "zio-logging-slf4j"           % "2.1.14",
      "dev.zio"               %% "zio-metrics-prometheus"      % "1.0.12",
      "dev.zio"               %% "zio-metrics-connectors"      % "2.0.0-RC6",
      "dev.zio"               %% "zio-nio"                     % "2.0.2",
      "dev.zio"               %% "zio-optics"                  % "0.2.1",
      "dev.zio"               %% "zio-prelude"                 % "1.0.0-RC21",
      "dev.zio"               %% "zio-process"                 % "0.7.2",
      "dev.zio"               %% "zio-rocksdb"                 % "0.4.3",
      "dev.zio"               %% "zio-s3"                      % "0.4.2",
      "dev.zio"               %% "zio-schema"                  % "0.4.14",
      "dev.zio"               %% "zio-sqs"                     % "0.6.0",
      "dev.zio"               %% "zio-opentracing"             % "2.0.3",
      "io.laserdisc"          %% "tamer-db"                    % "0.19.0",
      "io.jaegertracing"       % "jaeger-core"                 % "1.8.1",
      "io.jaegertracing"       % "jaeger-client"               % "1.8.1",
      "io.jaegertracing"       % "jaeger-zipkin"               % "1.8.1",
      "io.zipkin.reporter2"    % "zipkin-reporter"             % "2.16.4",
      "io.zipkin.reporter2"    % "zipkin-sender-okhttp3"       % "2.16.4",
      "dev.zio"               %% "zio-interop-cats"            % "23.0.03",
      "dev.zio"               %% "zio-interop-scalaz7x"        % "7.3.3.0",
      "dev.zio"               %% "zio-interop-reactivestreams" % "2.0.2",
      "dev.zio"               %% "zio-interop-twitter"         % "20.10.2",
      "dev.zio"               %% "zio-zmx"                     % "0.0.13",
      "dev.zio"               %% "zio-query"                   % "0.4.0",
      "dev.zio"               %% "zio-mock"                    % "1.0.0-RC11",
      "org.polynote"          %% "uzhttp"                      % "0.3.0-RC1",
      "org.tpolecat"          %% "doobie-core"                 % doobieV,
      "org.tpolecat"          %% "doobie-h2"                   % doobieV,
      "org.tpolecat"          %% "doobie-hikari"               % doobieV,
      "org.http4s"            %% "http4s-blaze-server"         % "0.23.15",
      "org.http4s"            %% "http4s-blaze-client"         % "0.23.15",
      "org.http4s"            %% "http4s-dsl"                  % http4sV,
      "com.github.ghostdogpr" %% "caliban"                     % "2.0.0",
      "com.github.ghostdogpr" %% "caliban-zio-http"            % "2.0.0",
      "org.scalameta"         %% "munit"                       % "1.0.0-M10",
      "com.github.poslegm"    %% "munit-zio"                   % "0.1.1",
      "nl.vroste"             %% "rezilience"                  % "0.9.4",
      "io.github.gaelrenoux"  %% "tranzactio"                  % "4.2.0",
      "io.github.neurodyne"   %% "zio-arrow"                   % "0.2.1",
      "nl.vroste"             %% "zio-amqp"                    % "0.5.0",
//      "dev.zio"                       %% "zio-aws-core"                  % "5.17.102.7",
//      "dev.zio"                       %% "zio-aws-ec2"                   % "5.17.102.7",
//      "dev.zio"                       %% "zio-aws-elasticbeanstalk"      % "5.17.102.7",
//      "dev.zio"                       %% "zio-aws-netty"                 % "5.17.102.7",
      "io.github.neurodyne"           %% "zio-aws-s3"                    % "0.4.13",
      "com.coralogix"                 %% "zio-k8s-client"                % "2.1.0",
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.9.0",
      "nl.vroste"                     %% "zio-kinesis"                   % "0.32.0",
      "com.vladkopanev"               %% "zio-saga-core"                 % "0.6.0",
      "io.scalac"                     %% "zio-slick-interop"             % "0.6.0",
      "com.typesafe.slick"            %% "slick-hikaricp"                % "3.4.1",
      "info.senia"                    %% "zio-test-akka-http"            % "2.0.14",
      "io.getquill"                   %% "quill-jdbc-zio"                % "4.6.1",
      "com.typesafe.akka"             %% "akka-http"                     % "10.5.2",
      "com.typesafe.akka"             %% "akka-cluster-typed"            % "2.8.4",
      "com.typesafe.akka"             %% "akka-cluster-sharding-typed"   % "2.8.4",
      "com.devsisters"                %% "shardcake-core"                % "2.0.0",
      "com.devsisters"                %% "shardcake-storage-redis"       % "2.0.0",
      "com.devsisters"                %% "shardcake-protocol-grpc"       % "2.0.0",
      "com.devsisters"                %% "shardcake-entities"            % "2.0.0",
      "com.devsisters"                %% "shardcake-manager"             % "2.0.0",
      "com.devsisters"                %% "shardcake-serialization-kryo"  % "2.0.0",
      "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-core"                 % "0.6.0-test4",
      "dev.zio"                       %% "zio-http"                      % "0.0.5",
      "dev.zio"                       %% "zio-metrics-connectors"        % "2.0.7"
    ),
    resolvers += "Confluent" at "https://packages.confluent.io/maven",
    fork           := true,
    Compile / fork := false
  )
  .settings(macroDefinitionSettings)
  .settings(mdocJS := Some(jsdocs))
  .dependsOn(
    core.jvm,
    streams.jvm,
    concurrent.jvm,
    tests.jvm,
    testJunitRunner,
    testMagnolia.jvm,
    testRefined.jvm,
    testScalaCheck.jvm,
    core.js,
    macros.jvm
  )
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
  .aggregate(docs_make_zio_app_configurable)
