package zio.examples.scalajs_todomvc

/**
 * ProjectSetupExample
 *
 * This example demonstrates the sbt configuration required to set up a
 * standalone Scala.js project with ZIO. Create a new, empty directory for your
 * project (do not add this inside the ZIO library's own checkout).
 *
 * Add the following to `project/plugins.sbt`:
 *
 * {{{
 *   addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
 * }}}
 *
 * Add the following to `build.sbt`:
 *
 * {{{
 *   lazy val todomvc = project
 *     .in(file("."))
 *     .enablePlugins(ScalaJSPlugin)
 *     .settings(
 *       scalaVersion := "2.13.18",
 *       libraryDependencies ++= Seq(
 *         "dev.zio"        %%% "zio"           % "2.0.12",
 *         "org.scala-js"   %%% "scalajs-dom"   % "2.8.1"
 *       ),
 *       scalaJSUseMainModuleInitializer := true,
 *       scalacOptions += "-P:scalajs:nowarnGlobalExecutionContext"
 *     )
 * }}}
 *
 * The key flag `scalaJSUseMainModuleInitializer := true` tells the Scala.js
 * compiler to generate a `main()` function that runs immediately in the
 * browser, starting your ZIO runtime. It only works when your project has
 * exactly one object extending `ZIOAppDefault` (or another `App`-like entry
 * point) at a time — keep a single `src/main/scala/Main.scala` and replace its
 * content as you go, rather than accumulating multiple entry-point objects side
 * by side.
 *
 * There's nothing to compile yet — `scalaJSUseMainModuleInitializer` requires a
 * real entry point to link against, so the first build happens once you've
 * added `src/main/scala/Main.scala` in the next section.
 */
object ProjectSetupExample {
  // This file is documentation-only. See the package documentation comments above
  // for required sbt configuration.
}
