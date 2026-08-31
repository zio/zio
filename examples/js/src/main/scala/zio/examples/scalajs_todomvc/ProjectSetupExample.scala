package zio.examples.scalajs_todomvc

/**
 * ProjectSetupExample
 *
 * This example demonstrates the sbt configuration required to set up Scala.js
 * with ZIO.
 *
 * Add the following to `project/plugins.sbt`:
 *
 * {{{
 *   addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
 *   addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.4.0")
 * }}}
 *
 * Add the following to `build.sbt`:
 *
 * {{{
 *   lazy val app = crossProject(JSPlatform, JVMPlatform)
 *     .in(file("app"))
 *     .settings(
 *       scalaVersion := "3.5.0",
 *       libraryDependencies ++= Seq(
 *         "dev.zio"        %%% "zio"           % "2.1.13",
 *         "org.scala-js"   %%% "scalajs-dom"   % "2.8.1"
 *       )
 *     )
 *     .jsSettings(
 *       scalaJSUseMainModuleInitializer := true,
 *       scalacOptions += "-P:scalajs:nowarnGlobalExecutionContext"
 *     )
 *
 *   lazy val appJS = app.js
 *   lazy val appJVM = app.jvm
 * }}}
 *
 * The key flag `scalaJSUseMainModuleInitializer := true` tells the Scala.js
 * compiler to generate a `main()` function that runs immediately in the
 * browser, starting your ZIO runtime.
 *
 * Confirm compilation succeeds: sbt appJS/fastLinkJS
 *
 * You should see no errors and a compiled JS file at
 * `target/scala-3.5.0/app-fastopt/main.js`.
 */
object ProjectSetupExample {
  // This file is documentation-only. See the package documentation comments above
  // for required sbt configuration.
}
