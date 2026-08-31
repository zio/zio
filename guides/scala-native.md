# Build and Run a ZIO Application on Scala Native

> Configure sbt-crossproject to compile and link a ZIO application as a Scala Native binary without the JVM.

## Introduction

By the end of this guide we will hold a compiled, linked, executable Scala Native binary running a real ZIO application — no JVM required on the target machine, no GraalVM `native-image` configuration to maintain. The approach is to add two sbt plugins, convert a `project` definition to a `crossProject(JVMPlatform, NativePlatform)`, and apply the same `nativeSettings` block that ZIO's own multi-platform build uses.

## The Problem

A standard ZIO project today looks like this:

```scala
// build.sbt — today, JVM only
lazy val myApp = project
  .in(file("my-app"))
  .settings(
    scalaVersion := "2.13.18",
    libraryDependencies += "dev.zio" %% "zio"        % "2.1.26",
    libraryDependencies += "dev.zio" %% "zio-streams" % "2.1.26"
  )
```

This build has no `nativeLink` task. Without it, the only path to a self-contained executable is GraalVM `native-image`, which requires hand-crafting three JSON configuration files alongside the `build.sbt`:

```json
// src/main/resources/META-INF/native-image/reflect-config.json
[
  { "name": "zio.Runtime$", "allDeclaredMethods": true, "allDeclaredFields": true },
  { "name": "zio.ZLayer$", "allDeclaredMethods": true, "allDeclaredFields": true },
  { "name": "zio.internal.FiberRuntime", "allDeclaredMethods": true, "allDeclaredFields": true }
]
```

Every entry is a best guess from failed link attempts. Rename `FiberRuntime` in a ZIO upgrade — or add a new reflective access — and the binary links but panics at startup. The `resource-config.json` and `serialization-config.json` carry the same hidden coupling. A refactor that is invisible at compile time breaks a production binary.

## Prerequisites

Scala Native's linker uses the Boehm garbage collector at link time. Install it on Debian or Ubuntu with:

```bash
sudo apt-get update && sudo apt-get install -y libgc-dev
```

You also need sbt 1.9 or later. Scala Native supports Scala 2.12, 2.13, and 3; this guide uses Scala 2.13 throughout, which is the version ZIO's CI validates by default.

The ZIO dependencies use sbt-crossproject's `%%%` operator rather than `%%`. This operator resolves to `%%` on JVM and appends the `_native0.5` classifier on Scala Native, so the same `build.sbt` line covers both platforms:

```scala
libraryDependencies += "dev.zio" %%% "zio"        % "2.1.26"
libraryDependencies += "dev.zio" %%% "zio-streams" % "2.1.26"
```

The base imports shared across all code examples in this guide are:

```scala
import zio._
import zio.stream._
```

This guide assumes you already write ZIO effects, compose [`ZLayer`](../reference/contextual/zlayer.md)s, and run `zio-test` suites. If you need a primer on any of those, see [ZIOApp](../reference/core/zioapp.md) before continuing.

## The Core Model

We work with a single cross-compiled module that produces two sub-projects from one source tree. The `Job` type defined here is the work unit both the application step and the test step share:

```scala
case class Job(id: Int, name: String)
```

`crossProject(JVMPlatform, NativePlatform)` splits the build into `myAppJVM` and `myAppNative`. One source tree compiles for both platforms; each platform's sub-project produces a distinct artifact through a platform-specific output step:

```
my-app/src/main/scala/        (shared source)
          │
          ├──► myAppJVM  ──(scalac)──────────────────► my-app.jar
          │
          └──► myAppNative ──(scalac + LLVM)──(nativeLink)──► my-app-out
```

Source files under `my-app/src/main/scala/` compile for both platforms; any platform-specific code lives under `my-app/jvm/src/` or `my-app/native/src/`. Every step from here assembles the settings that make `myAppNative` link to an executable binary.

## Add the sbt Plugins

Add the following two plugins to `project/plugins.sbt`:

```scala
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.4.0")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.12")
```

`sbt-scala-native-crossproject` provides the `crossProject`, `NativePlatform`, and `nativeSettings(...)` DSL that lets a single `build.sbt` source tree target multiple platforms. `sbt-scala-native` provides the `nativeLink`, `nativeConfig`, and related tasks that compile Scala to LLVM IR and link it into a native binary.

After running `sbt reload`, confirm the new task is visible:

```text
$ sbt myAppNative/help nativeLink
Link a native project.
```

## Configure the Cross Build

Replace the single `project` definition with a `crossProject`. The imports at the top of `build.sbt` must include the Scala Native build API:

```scala
import scala.scalanative.build.Mode
```

The full cross-build definition, including the `nativeSettings` block ZIO's own build uses, looks like this:

```scala
// build.sbt
lazy val myApp = crossProject(JVMPlatform, NativePlatform)
  .in(file("my-app"))
  .settings(
    name         := "my-app",
    scalaVersion := "2.13.18",
    libraryDependencies += "dev.zio" %%% "zio"        % "2.1.26",
    libraryDependencies += "dev.zio" %%% "zio-streams" % "2.1.26"
  )
  .nativeSettings(
    nativeConfig ~= { _.withMode(Mode.releaseFast) },
    scalacOptions += "-P:scalanative:genStaticForwardersForNonTopLevelObjects",
    Test / fork := false,
    bspEnabled  := false
  )

lazy val myAppJVM    = myApp.jvm
lazy val myAppNative = myApp.native
```

The four lines inside `nativeSettings` each address a real problem. `nativeConfig ~= { _.withMode(Mode.releaseFast) }` produces an optimised binary while keeping link times short — `Mode.debug` is faster to link but slow to run; `Mode.releaseSize` and `Mode.release` go further but take much longer. The `scalacOptions` flag `-P:scalanative:genStaticForwardersForNonTopLevelObjects` is required because ZIO places core logic in Scala `object` companions that sit inside other objects; without static forwarders the Scala Native code generator cannot call them from the C bridge. `Test / fork := false` is mandatory: the linked native binary IS the test process itself, so forking a child JVM process is impossible. `bspEnabled := false` disables the Build Server Protocol integration for the native sub-project; sbt's BSP support does not understand the native link step and causes confusing IDE errors if left enabled.

After reloading and running the link step, sbt prints the path to the produced binary:

```text
$ sbt myAppNative/nativeLink
...
[info] Linking (5323 ms)
[info] Total (5987 ms)
[success] my-app/.native/target/scala-2.13/my-app-out
```

Run that binary directly — no JVM needed:

```text
$ ./my-app/.native/target/scala-2.13/my-app-out
```

## Write and Run a ZIO Program

A [`ZIOAppDefault`](../reference/core/zioapp.md) compiled for Scala Native works identically to its JVM counterpart. The following program processes a list of jobs through a [`ZStream`](../reference/stream/zstream/index.md), records them via a `ZLayer`-provided `Recorder` service, and prints a completion summary:

```scala
import zio._
import zio.stream._

case class Job(id: Int, name: String)

trait Recorder {
  def record(name: String): UIO[Unit]
  def total: UIO[Int]
}

object Recorder {
  val inMemory: ZLayer[Any, Nothing, Recorder] =
    ZLayer.fromZIO(
      Ref.make(0).map(counter =>
        new Recorder {
          def record(name: String): UIO[Unit] = counter.update(_ + 1)
          def total: UIO[Int]                 = counter.get
        }
      )
    )
}

object Main extends ZIOAppDefault {

  val jobs: List[Job] = List(
    Job(1, "compile"),
    Job(2, "test"),
    Job(3, "package")
  )

  def processJob(job: Job): ZIO[Recorder, Nothing, Unit] =
    for {
      now      <- Clock.currentDateTime
      _        <- Console.printLine(s"[$now] Processing job ${job.id}: ${job.name}").orDie
      recorder <- ZIO.service[Recorder]
      _        <- recorder.record(job.name)
    } yield ()

  val program: ZIO[Recorder, Nothing, Unit] =
    ZStream
      .fromIterable(jobs)
      .mapZIO(processJob)
      .runDrain *>
      ZIO.serviceWithZIO[Recorder](_.total).flatMap(n =>
        Console.printLine(s"Completed $n jobs").orDie
      )

  def run: ZIO[Any, Any, Any] =
    program.provide(Recorder.inMemory)
}
```

Build and run with `sbt myAppNative/run`. The terminal shows each job with its timestamp, then the total — no JVM startup banner, no classpath:

```text
$ sbt myAppNative/run
[2026-08-28T10:14:03.221+00:00] Processing job 1: compile
[2026-08-28T10:14:03.224+00:00] Processing job 2: test
[2026-08-28T10:14:03.225+00:00] Processing job 3: package
Completed 3 jobs
```

:::warning[Shutdown Hooks Are No-ops on Scala Native]
`addShutdownHook` and `addSignalHandler` are explicitly documented no-ops on Scala Native — the underlying implementations discard their arguments via `blackhole(...)` and return immediately. The entire `gracefulShutdownTimeout` logic in `ZIOAppDefault` is registered inside the callback passed to `Platform.addShutdownHook`; because that callback is silently discarded on Scala Native, `gracefulShutdownTimeout` is never registered and never fires. Graceful shutdown is effectively unavailable on Scala Native.
:::

## Run Tests on Native

Add the test dependencies to your build. The cross-platform test deps (`zio-test`, `zio-test-sbt`, `testFrameworks`) go in the `settings` block; the Native-only extras (`scala-java-time`, `scala-native-crypto`) go in `nativeSettings`:

```scala
// build.sbt — updated with test support
lazy val myApp = crossProject(JVMPlatform, NativePlatform)
  .in(file("my-app"))
  .settings(
    name         := "my-app",
    scalaVersion := "2.13.18",
    libraryDependencies += "dev.zio" %%% "zio"         % "2.1.26",
    libraryDependencies += "dev.zio" %%% "zio-streams"  % "2.1.26",
    libraryDependencies += "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
    libraryDependencies += "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
  .nativeSettings(
    nativeConfig ~= { _.withMode(Mode.releaseFast) },
    scalacOptions += "-P:scalanative:genStaticForwardersForNonTopLevelObjects",
    Test / fork := false,
    bspEnabled  := false,
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time"      % "2.7.0" % Test,
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.7.0" % Test,
    libraryDependencies += "com.github.lolgab" %%% "scala-native-crypto"  % "0.3.0" % Test
  )
```

`scala-java-time` is required because the Scala Native standard library does not include `java.time.*`. ZIO's test internals (timestamps, durations) use `java.time`, so without this shim the test binary will fail to link. `scala-native-crypto` provides a secure-random source; the Scala Native stdlib offers no equivalent to `SecureRandom`, which `zio-test`'s property-based generators need.

Write a spec that marks expensive tests with [`TestAspect.exceptNative`](../reference/test/aspects/environment-specific-tests.md) to keep native test runs within reasonable time budgets:

```scala
import zio._
import zio.test._
import zio.test.TestAspect._

case class Job(id: Int, name: String)

object JobQueueSpec extends ZIOSpecDefault {
  def spec = suite("JobQueueSpec")(
    test("processes a short job list in order") {
      val jobs = List(Job(1, "compile"), Job(2, "test"), Job(3, "package"))
      for {
        results <- ZIO.foreach(jobs)(job => ZIO.succeed(job.name))
      } yield assertTrue(results == List("compile", "test", "package"))
    },
    test("handles a large workload concurrently") {
      // Excluded on Scala Native to keep native test runs under the 120-second timeout
      val jobs = List.fill(500)(Job(1, "work"))
      for {
        fibers  <- ZIO.foreach(jobs)(j => ZIO.succeed(j.id).fork)
        results <- ZIO.foreach(fibers)(_.join)
      } yield assertTrue(results.length == 500)
    } @@ exceptNative
  )
}
```

ZIO's own test suite runs with `TestAspect.size(10)` and `TestAspect.samples(50)` on Scala Native (down from larger JVM values) to keep the linked-binary test run under 120 seconds. If you write property-based tests, apply the same aspects via `@@ size(10) @@ samples(50)` on Native to match that profile.

Run the native tests with:

```text
$ sbt myAppNative/test
[info] + JobQueueSpec
[info]   + processes a short job list in order
[info]   - handles a large workload concurrently - ignored
[info] Passed: Total 1, Failed 0, Errors 0, Passed 1, Ignored 1
```

## Know What Works and What Doesn't

The following table records what ZIO's CI validates on Scala Native, what is absent from the published artifact set, and what compiles but silently does nothing:

| Feature                                                                     | Status           |
| --------------------------------------------------------------------------- | ---------------- |
| `ZIO` core effects, `ZIO.attempt`, `ZIO.async` (shared test suite)          | ✅ Confirmed      |
| Fibers, fork/join, supervision (`FiberSpec` passes)                         | ✅ Confirmed      |
| `ZLayer`, dependency injection (`ZLayerSpec` passes)                        | ✅ Confirmed      |
| Multi-threaded `ZScheduler`, work-stealing (same code as JVM)               | ✅ Confirmed      |
| `ZIO.blocking`, `ZIO.attemptBlocking` (`BlockingSpec` in `jvm-native/`)     | ✅ Confirmed      |
| `ZStream`, `ZSink` (streamsTestsNative in CI)                               | ✅ Confirmed      |
| `Scope`, `ZIO.acquireRelease`                                                | ✅ Confirmed      |
| `Console`, `Clock`, `Random`, `System`                                      | ✅ Confirmed      |
| `CompletionStage` / `CompletableFuture` interop                             | ✅ Confirmed      |
| `zio-streams`, `zio-concurrent`, `zio-macros`, `zio-managed`               | ✅ Confirmed      |
| `zio-test-scalacheck`                                                       | ✅ Confirmed      |
| `zio-test-magnolia`                                                         | ❌ Not Published  |
| `zio-test-refined`                                                          | ❌ Not Published  |
| `zio-test-junit` (JUnit 4 runner)                                           | ❌ Not Published  |
| `zio-test-junit-engine` (JUnit 5 engine)                                    | ❌ Not Published  |
| `addShutdownHook`                                                           | ⚠️ No-op          |
| `addSignalHandler`                                                          | ⚠️ No-op          |

The multi-threaded scheduler deserves a note about blocking. Both JVM and Scala Native default to `autoBlocking = false` in `Executor.makeDefault`, which means the default executor does not automatically shift blocking work to a separate pool. `ZIO.blocking` routes work to a dedicated `blockingExecutor` backed by an unbounded cached `ThreadPoolExecutor` (core size 0, max size `Int.MaxValue`, `SynchronousQueue`) — exactly the same implementation on both platforms — which does spawn a new thread for every submitted task that has no idle thread available. If you want the main executor to automatically redirect blocking work to this pool, add `Runtime.enableAutoBlockingExecutor` as a `ZLayer` in your `provide` call:

```scala
import zio._

object Main extends ZIOAppDefault {
  def run: ZIO[Any, Any, Any] =
    ZIO
      .blocking(ZIO.attempt(Thread.sleep(100)))
      .provide(Runtime.enableAutoBlockingExecutor)
}
```

## Cross-Publish JVM and Native Artifacts

To publish both sub-projects from a single `sbt publish` command, aggregate them under a root project:

```scala
// build.sbt — root aggregate
lazy val root = project
  .in(file("."))
  .aggregate(myAppJVM, myAppNative)
  .settings(publish / skip := true)
```

`aggregate` causes `compile`, `test`, and `publish` commands issued on `root` to cascade to both `myAppJVM` and `myAppNative`. `publish / skip := true` on `root` itself prevents sbt from publishing a spurious empty root artifact alongside the real ones. sbt-crossproject appends the platform classifier automatically — a consumer writes `"com.example" %%% "my-app" % "<version>"` and sbt resolves the correct artifact for their platform.

After running `sbt +root/publishLocal`, both artifacts appear in `~/.ivy2/local/`:

```text
$ sbt +root/publishLocal
...
[info] published my-app_2.13 to ~/.ivy2/local/com.example/my-app_2.13/0.1.0/jars/my-app_2.13.jar
[info] published my-app_native0.5_2.13 to ~/.ivy2/local/com.example/my-app_native0.5_2.13/0.1.0/jars/my-app_native0.5_2.13.jar
```

## Putting It Together

The complete example combining every step — cross-project setup, `ZLayer`-based services, `ZStream` processing, and `zio-test` suite — lives in a single compilable file:

```scala title="zio-examples/scala-native/src/main/scala/scalanative/CompleteExample.scala"
package scalanative

import zio._
import zio.stream._

/** Putting It Together — Complete ZIO + Scala Native Example
  *
  * Combines every guide step: the Job type (shared with Main.scala), the
  * Recorder ZLayer service, a ZStream processing pipeline, and a ZIO.foreach
  * result-collection loop.
  *
  * Job and Recorder are defined in Main.scala (same package) and reused here.
  *
  * Run with: sbt "runMain scalanative.CompleteExample"
  */
object CompleteExample extends ZIOAppDefault {

  val jobs: List[Job] = List(
    Job(1, "compile"),
    Job(2, "test"),
    Job(3, "package")
  )

  // ZIO.foreach loop — collects job names in order (mirrors the test spec pattern)
  val collectNames: ZIO[Any, Nothing, List[String]] =
    ZIO.foreach(jobs)(job => ZIO.succeed(job.name))

  def processJob(job: Job): ZIO[Recorder, Nothing, Unit] =
    for {
      now      <- Clock.currentDateTime
      _        <- Console.printLine(s"[$now] Processing job ${job.id}: ${job.name}").orDie
      recorder <- ZIO.service[Recorder]
      _        <- recorder.record(job.name)
    } yield ()

  // ZStream pipeline — processes jobs sequentially via the Recorder ZLayer service
  val pipeline: ZIO[Recorder, Nothing, Unit] =
    ZStream
      .fromIterable(jobs)
      .mapZIO(processJob)
      .runDrain *>
      ZIO.serviceWithZIO[Recorder](_.total).flatMap(n =>
        Console.printLine(s"Completed $n jobs").orDie
      )

  def run: ZIO[Any, Any, Any] =
    for {
      names <- collectNames
      _     <- Console.printLine(s"Jobs to process: ${names.mkString(", ")}").orDie
      _     <- pipeline.provide(Recorder.inMemory)
    } yield ()
}
```

## Running the Examples

The companion examples compile against JVM ZIO so that they are runnable without the Scala Native toolchain installed. The source files contain exactly the same ZIO code you would place in a cross-compiled project; copy them into the `shared/` source tree of a `crossProject(JVMPlatform, NativePlatform)` built with the `nativeSettings` block from the Configure the Cross Build step above.

Clone the repository and navigate to the examples module:

```bash
git clone https://github.com/zio/zio.git
cd zio/zio-examples/scala-native
```

### Run the ZIO Application

The `Main` object uses `ZIOAppDefault` with `ZStream` and `ZLayer` to process jobs and print a summary:

<details>
<summary>Main.scala — source with line numbers</summary>

```scala title="zio-examples/scala-native/src/main/scala/scalanative/Main.scala" showLineNumbers
package scalanative

import zio._
import zio.stream._

/** Step 3 — Write and Run a ZIO Program
  *
  * Processes a list of jobs through a ZStream, records them via a ZLayer-provided
  * Recorder service, and prints a completion summary.
  *
  * Run with: sbt "runMain scalanative.Main"
  */

case class Job(id: Int, name: String)

trait Recorder {
  def record(name: String): UIO[Unit]
  def total: UIO[Int]
}

object Recorder {
  val inMemory: ZLayer[Any, Nothing, Recorder] =
    ZLayer.fromZIO(
      Ref.make(0).map(counter =>
        new Recorder {
          def record(name: String): UIO[Unit] = counter.update(_ + 1)
          def total: UIO[Int]                 = counter.get
        }
      )
    )
}

object Main extends ZIOAppDefault {

  val jobs: List[Job] = List(
    Job(1, "compile"),
    Job(2, "test"),
    Job(3, "package")
  )

  def processJob(job: Job): ZIO[Recorder, Nothing, Unit] =
    for {
      now      <- Clock.currentDateTime
      _        <- Console.printLine(s"[$now] Processing job ${job.id}: ${job.name}").orDie
      recorder <- ZIO.service[Recorder]
      _        <- recorder.record(job.name)
    } yield ()

  val program: ZIO[Recorder, Nothing, Unit] =
    ZStream
      .fromIterable(jobs)
      .mapZIO(processJob)
      .runDrain *>
      ZIO.serviceWithZIO[Recorder](_.total).flatMap(n =>
        Console.printLine(s"Completed $n jobs").orDie
      )

  def run: ZIO[Any, Any, Any] =
    program.provide(Recorder.inMemory)
}
```

</details>

Run the application on the JVM to confirm the logic:

```bash
sbt "runMain scalanative.Main"
```

### Run the Test Suite

The `JobQueueSpec` demonstrates `TestAspect.exceptNative` excluding the large-concurrency test on Native:

<details>
<summary>JobQueueSpec.scala — source with line numbers</summary>

```scala title="zio-examples/scala-native/src/test/scala/scalanative/JobQueueSpec.scala" showLineNumbers
package scalanative

import zio._
import zio.test._
import zio.test.TestAspect._

/** Step 4 — Run Tests on Native
  *
  * Demonstrates TestAspect.exceptNative to exclude the large-concurrency test
  * on Scala Native, keeping native test runs under the 120-second budget.
  *
  * Run with: sbt test
  */

// Job is defined in Main.scala (same package); no redefinition needed here.

object JobQueueSpec extends ZIOSpecDefault {
  def spec = suite("JobQueueSpec")(
    test("processes a short job list in order") {
      val jobs = List(Job(1, "compile"), Job(2, "test"), Job(3, "package"))
      for {
        results <- ZIO.foreach(jobs)(job => ZIO.succeed(job.name))
      } yield assertTrue(results == List("compile", "test", "package"))
    },
    test("handles a large workload concurrently") {
      // Excluded on Scala Native to keep native test runs under the 120-second timeout
      val jobs = List.fill(500)(Job(1, "work"))
      for {
        fibers  <- ZIO.foreach(jobs)(j => ZIO.succeed(j.id).fork)
        results <- ZIO.foreach(fibers)(_.join)
      } yield assertTrue(results.length == 500)
    } @@ exceptNative
  )
}
```

</details>

Execute the test suite:

```bash
sbt test
```

## Going Further

The following reference pages document the types this guide used:

- [ZIOApp](../reference/core/zioapp.md) — lifecycle hooks, `gracefulShutdownTimeout`, and how the `main` entry point works for `ZIOAppDefault` on all platforms.
- [Environment-Specific Tests](../reference/test/aspects/environment-specific-tests.md) — full reference for `TestAspect.exceptNative`, `TestAspect.nativeOnly`, and the companion `jsOnly` / `jvmOnly` aspects.
- [Interop with JavaScript](./interop/with-javascript.md) — the same `crossProject` shape applied to a different platform target; the `nativeSettings` / `jsSettings` split follows an identical pattern.

Two areas this guide deliberately left out:

**Scala Native FFI with ZIO.** When you call C libraries via Scala Native's `@extern` objects, wrap each call in `ZIO.attempt` for error-raising APIs or `ZIO.succeed` for pure-by-convention calls. For C memory allocated with `Zone`, tie the `Zone` lifecycle to `ZIO.acquireRelease` so the allocation is freed regardless of fiber interruption. No ZIO-published example demonstrates this pattern yet.

**Third-party ZIO ecosystem libraries.** Libraries such as `zio-http`, `zio-kafka`, and `zio-nio` currently publish JVM-only artifacts — their `build.sbt` uses `%%` rather than `%%%`. Check each library's own repository for Native support before adding it as a dependency to a `crossProject`.
