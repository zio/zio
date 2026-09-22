---
id: from-monix
title: "Migrate from Monix to ZIO 2.x"
sidebar_label: "Migration from Monix"
description: "A comprehensive reference for mapping Monix 3.x Task, Coeval, Observable, TaskApp, Atomic, and monix.catnap to their ZIO 2.x equivalents"
keywords:
  - "Monix Migration"
  - "Effect Systems"
  - "Observable ZStream"
  - "Task Migration"
  - "Coeval Migration"
  - "Fiber Concurrency"
  - "ZIO Migration"
  - "ZStream"
  - "TestClock"
---

## Introduction

This guide is a comprehensive reference for migrating a Monix 3.x application to ZIO 2.x. Rather than walking through a single example from start to finish, it is organized by the Monix constructs your codebase uses — `Task`, `Coeval`, `Observable`, `TaskApp`, `Atomic`, and `monix.catnap` — so you can jump directly to the section that applies. Every mapping is backed by a two-column table, and most sections show the actual Monix code next to its ZIO replacement, both compiled against the real libraries, not just described in prose.

What this guide covers:

- [Replacing the Application Entry Point](#replacing-the-application-entry-point) — `TaskApp`, `ExitCode`, `Scheduler`
- [Translating Effect Constructors](#translating-effect-constructors) — `Task.eval`, `Task.async`, `Task.cancelable`
- [Replacing `Coeval`](#replacing-coeval) — the synchronous computation type and its ZIO replacements
- [Mapping the Error Channel](#mapping-the-error-channel) — `onErrorHandleWith`, `redeem`, `redeemWith`, and more
- [Managing Resource Lifecycles](#managing-resource-lifecycles) — `bracket`, `bracketCase`, `guarantee`, `guaranteeCase`
- [Concurrency and Fibers](#concurrency-and-fibers) — `start`, `race`, `parSequence`, `parTraverse`
- [Shared State](#shared-state) — `Atomic` → `Ref`, `TaskLocal` → `FiberRef`
- [Concurrent Data Structures](#concurrent-data-structures) — `MVar`, `ConcurrentQueue`, `Semaphore`, `ConcurrentChannel`
- [Streaming: Observable to ZStream](#streaming-observable-to-zstream) — constructors, operators, resource-safe streams
- [Running Effects Unsafely](#running-effects-unsafely) — `runSyncUnsafe`, `runToFuture`, `runAsync`
- [Testing](#testing) — `TestScheduler` → `TestClock`

The six patterns hit most often — entry point, effect constructors, error channel, resources, fibers, shared state — are demonstrated together in one runnable program in [Putting It Together](#putting-it-together). The remaining sections extend the reference to the rest of the Monix 3.x surface.

## The Problem

Monix's `Task[A]` hardwires the error channel to `Throwable`. Every function that may fail looks identical in its signature regardless of whether it throws a domain error or a raw exception, so the compiler cannot enforce exhaustive error handling or catch mismatched error types. `Task.bracket` is the primary resource primitive: stacking two resources means nested bracket calls, interleaving acquisition and cleanup logic with business logic and making it difficult to add a third resource later without restructuring the chain. `Observable[A]` lives in a separate module (`monix-reactive`) with its own lifecycle rules, so combining streaming with bracketed effects requires careful threading of cancelation tokens that is easy to get wrong.

The following program shows these patterns together — a Monix 3.4.1 application that queries a database, races two tasks, and processes a stream:

```scala
import monix.eval.{Task, TaskApp, Coeval}
import monix.reactive.Observable
import scala.concurrent.duration._

sealed abstract class AppError(msg: String) extends RuntimeException(msg)
case class DbError(msg: String)      extends AppError(msg)
case class TimeoutError(msg: String) extends AppError(msg)

case class DbConnection(id: Int) {
  def query(sql: String): Task[String] =
    Task.eval(s"conn-$id: $sql result")
  def close(): Task[Unit] =
    Task.eval(println(s"Closing connection $id"))
}

def makeConnection(id: Int): Task[DbConnection] =
  Task.eval(DbConnection(id))

def withConnection[A](id: Int)(use: DbConnection => Task[A]): Task[A] =
  makeConnection(id).bracket(conn => conn.close())(use)

def queryWorker(id: Int): Task[String] =
  withConnection(id) { conn =>
    conn.query("SELECT 1")
      .onErrorHandleWith(e => Task.raiseError(DbError(e.getMessage)))
  }

def streamExample: Observable[Int] =
  Observable
    .fromIterable(1 to 10)
    .filter(_ % 2 == 0)
    .map(_ * 2)
    .take(3)

object MyApp extends TaskApp {
  def run(args: List[String]): Task[ExitCode] =
    for {
      results <- Task.parSequence(List(queryWorker(1), queryWorker(2)))
      _       <- Task.eval(println(s"Results: $results"))
      raced   <- Task.race(queryWorker(3), Task.sleep(1.second).as("timeout"))
      _       <- Task.eval(println(s"Race: $raced"))
      nums    <- streamExample.toListL
      _       <- Task.eval(println(s"Stream: $nums"))
      value   =  Coeval.eval(42 * 2).value()
      _       <- Task.eval(println(s"Coeval: $value"))
    } yield ExitCode.Success
}
```

This guide replaces every pattern shown above with its ZIO 2.x equivalent, then extends the coverage to the rest of the Monix API surface.

## Prerequisites

Add ZIO core and the streams module to `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio"        % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams" % "@VERSION@"
```

Then add the two base imports — `import zio._` covers all core types (`ZIO`, `Task`, `UIO`, `Ref`, `FiberRef`, `Fiber`, `Scope`, `ZIOAppDefault`, `Exit`, `Unsafe`, `Runtime`); `import zio.stream._` covers the streaming types (`ZStream`, `ZSink`, `ZPipeline`) used in the [streaming section](#streaming-observable-to-zstream):

```scala mdoc:silent
import zio._
import zio.stream._
```

Assumed knowledge: familiarity with Monix 3.x (`Task`, `Coeval`, `Observable`, `TaskApp`) and basic Scala `for`-comprehension syntax.

## The Core Model

The following domain types appear throughout the guide. Define them once and carry them through each section:

```scala mdoc:silent
case class DbConnection(id: Int) {
  def query(sql: String): Task[String] = ZIO.attempt(s"conn-$id: $sql result")
  def close: UIO[Unit]                 = ZIO.succeed(println(s"Closing connection $id"))
}

sealed trait AppError extends Throwable
case class DbError(msg: String)      extends AppError
case class TimeoutError(msg: String) extends AppError
```

`query` wraps a side-effecting call in `ZIO.attempt` because the body may throw — the ZIO equivalent of `Task.eval`. `close` uses `ZIO.succeed` because it is safe, matching `Task.eval` on a call that never throws. `AppError` and its subtypes appear in the typed error channel demonstrated in the following sections.

## Replacing the Application Entry Point

Replace `TaskApp` with `ZIOAppDefault`. The ZIO runtime, shutdown hooks, and default fiber scheduler are all provided by `ZIOAppDefault` — no `Scheduler` argument is needed, and the `run` method no longer has to produce an `ExitCode`:

| Monix 3.x                                              | ZIO 2.x                                              | Notes                                                          |
| ------------------------------------------------------- | ----------------------------------------------------- | --------------------------------------------------------------- |
| `trait TaskApp`                                         | `trait ZIOAppDefault extends ZIOApp`                 | ZIO runtime managed internally; no `Scheduler` needed           |
| `def run(args: List[String]): Task[ExitCode]`           | `def run: ZIO[ZIOAppArgs with Scope, Any, Any]`      | Any return type — in practice `Task[Unit]` satisfies this via contravariance |
| `Scheduler` injected by `TaskApp`                       | ZIO runtime — managed internally                     | —                                                               |
| `task.runToFuture(scheduler): CancelableFuture[A]`      | `Unsafe.unsafe { implicit u => runtime.unsafe.runToFuture(task) }` | See [Running Effects Unsafely](#running-effects-unsafely)   |

**Before (Monix):**

```scala
import monix.eval.{Task, TaskApp}

object MyApp extends TaskApp {
  def run(args: List[String]): Task[ExitCode] =
    Task.eval(println("Hello from Monix")).as(ExitCode.Success)
}
```

**After (ZIO):**

```scala mdoc:compile-only
import zio._

object MyApp extends ZIOAppDefault {
  def run: Task[Unit] =
    ZIO.succeed(println("Hello from ZIO"))
}
```

The `run` method returns `Task[Unit]` — `ZIO[Any, Throwable, Unit]` — which satisfies `ZIOAppDefault`'s required `ZIO[ZIOAppArgs with Scope, Any, Any]` because `ZIO` is contravariant in its environment type: `ZIO[Any, E, A]` (requires nothing) is a subtype of `ZIO[R, E, A]` for any `R`. The runtime determines the process exit code from whether the effect succeeds, fails, or is interrupted; `zio.ExitCode` still exists for programs that need to return an explicit code, but ordinary application code rarely needs it.

When Monix code cancels a `CancelableFuture`, the ZIO idiom is to manage fibers directly inside the effect:

```scala mdoc:compile-only
import zio._

val program: Task[Unit] = for {
  fiber <- ZIO.attempt("result").fork  // fork a fiber, not a CancelableFuture
  _     <- fiber.interrupt             // cancel — always safe, returns UIO[Exit[E, A]]
  exit  <- fiber.await                 // wait for the final Exit value
  _     <- ZIO.succeed(println(exit))
} yield ()
```

:::caution[No `App` in ZIO 2.x]
`App` was the ZIO 1.x entry-point trait. It no longer exists in ZIO 2.x. Always use `ZIOAppDefault` for applications that need no custom environment, or `ZIOApp` when you need a custom `bootstrap` layer or `Runtime` configuration. If you encounter `App` in older documentation or blog posts, substitute `ZIOAppDefault`.
:::

## Translating Effect Constructors

Every `Task` constructor has a direct ZIO equivalent. The mapping below covers the full constructor surface:

| Monix 3.x                                     | ZIO 2.x                           | Notes                                                                        |
| ---------------------------------------------- | ----------------------------------- | ----------------------------------------------------------------------------- |
| `Task.now(a)` / `Task.pure(a)`                 | `ZIO.succeed(a)`                   | Pure value, no side effect                                                    |
| `Task.eval(body)` / `Task.apply(body)`         | `ZIO.attempt(body)`                | Wraps a side-effecting block that may throw                                   |
| `Task.delay(body)`                             | `ZIO.attempt(body)`                | **Alias for `Task.eval`** — NOT time-based; wraps a synchronous side effect   |
| `Task.raiseError(e)`                           | `ZIO.fail(e)`                      | In Monix `e: Throwable`; in ZIO `e` can be any type                          |
| `Task.unit`                                    | `ZIO.unit`                         | —                                                                              |
| `Task.never`                                   | `ZIO.never`                        | Effect that never completes                                                   |
| `Task.defer(task)` / `Task.suspend(task)`      | `ZIO.suspend(rio)`                 | Lazily evaluates an effect; catches thrown exceptions                         |
| `Task.fromFuture(f)`                           | `ZIO.fromFuture(ec => f)`          | ZIO's variant takes `ExecutionContext => Future[A]` directly                  |
| `Task.fromEither(e)`                           | `ZIO.fromEither(e)`                | Direct mapping                                                                |
| `Task.fromTry(t)`                              | `ZIO.fromTry(t)`                   | Direct mapping                                                                |
| `Task.async(register)`                         | `ZIO.async(register)`              | Callback-based async without cancellation                                     |
| `Task.cancelable(register)`                    | `ZIO.asyncInterrupt(register)`     | Async with cancellation support                                               |
| `Task.sleep(duration)`                         | `ZIO.sleep(duration)`              | Direct mapping                                                                |
| `task.delayExecution(d)`                       | `task.delay(d)`                    | Delays the start of the effect by prepending a sleep                          |
| `task.timeout(d)`                              | `task.timeout(d)`                  | ZIO returns `Option[A]`; `None` on timeout                                    |
| `task.timed`                                   | `task.timed`                       | Returns `(Duration, A)` in both libraries                                     |
| `task.loopForever`                             | `task.forever`                     | Repeats the effect indefinitely                                               |
| `Task.shift`                                   | `ZIO.yieldNow`                     | Cooperatively yields to the scheduler; ZIO also has `ZIO.shift(executor)` for switching executors |

The following shows the core constructors translated, producing effect values as data:

**Before (Monix):**

```scala
import monix.eval.Task
import scala.concurrent.duration._

val fetched:    Task[String]  = Task.eval("result from database")
val constant:   Task[Int]     = Task.pure(42)
val unit:       Task[Unit]    = Task.unit
val never:      Task[Nothing] = Task.never
val raiseErr:   Task[Nothing] = Task.raiseError(new RuntimeException("oops"))
val fromEither: Task[Int]     = Task.fromEither(Right(1): Either[Throwable, Int])
val fromTry:    Task[Int]     = Task.fromTry(scala.util.Try(1))
val slept:      Task[Unit]    = Task.sleep(1.second)
val deferred:   Task[String]  = Task.defer(Task.eval("deferred"))
```

**After (ZIO):**

```scala mdoc:compile-only
import zio._

val fetched:    Task[String]  = ZIO.attempt("result from database")
val constant:   UIO[Int]      = ZIO.succeed(42)
val unit:       UIO[Unit]     = ZIO.unit
val never:      UIO[Nothing]  = ZIO.never
val raiseErr:   Task[Nothing] = ZIO.fail(new RuntimeException("oops"))
val fromEither: IO[String, Int] = ZIO.fromEither(Right(1): Either[String, Int])
val fromTry:    Task[Int]     = ZIO.fromTry(scala.util.Try(1))
val slept:      UIO[Unit]     = ZIO.sleep(1.second)
val deferred:   Task[String]  = ZIO.suspend(ZIO.attempt("deferred"))
```

`ZIO.attempt` returns `Task[A]`, the alias `ZIO[Any, Throwable, A]` — the closest equivalent to Monix's `Task[A]`. `ZIO.succeed` returns `UIO[A]` (`ZIO[Any, Nothing, A]`), a value the type system proves cannot fail.

:::caution[`Task.delay` Is Not a Time-Based Delay]
In Monix, `Task.delay(body)` is an alias for `Task.eval(body)` — it wraps a synchronous side-effecting block. It is NOT a time-based sleep. The correct ZIO mapping is `ZIO.attempt(body)`, not `ZIO.sleep`. The time-based equivalent of `Task.sleep(duration)` is `ZIO.sleep(duration)`.
:::

## Replacing `Coeval`

`Coeval[A]` is Monix's synchronous, lazy effect type for computations that can complete without async execution. ZIO has no separate synchronous type — `UIO[A]` and `Task[A]` cover both synchronous and asynchronous code, and the runtime handles scheduling either way:

| Monix `Coeval[A]`                          | ZIO 2.x replacement                       | Notes                                                                 |
| ------------------------------------------- | ------------------------------------------- | ----------------------------------------------------------------------- |
| `Coeval.pure(a)` / `Coeval.now(a)`          | `ZIO.succeed(a)` (or a plain Scala value)  | For a truly pure, already-evaluated value                               |
| `Coeval.eval(body)` / `Coeval.apply(body)`  | `ZIO.attempt(body)`                        | Wraps a synchronous side-effecting block                                |
| `Coeval.raiseError(e)`                      | `ZIO.fail(e)`                              | —                                                                       |
| `coeval.value()` (synchronous extraction)   | `Unsafe.unsafe { implicit u => Runtime.default.unsafe.run(zio).getOrThrow() }` | Only at program boundaries — see [Running Effects Unsafely](#running-effects-unsafely) |

**Before (Monix):**

```scala
import monix.eval.Coeval

val result: Int =
  Coeval.eval(42 * 2).value()  // synchronous extraction
```

**After (ZIO):**

```scala mdoc:compile-only
import zio._

// Inside a ZIO program — compose with flatMap, never extract synchronously
val result: UIO[Int] = ZIO.succeed(42 * 2)

// Only at the edge of the application, outside any ZIO context:
val extracted: Int =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(result).getOrThrow()
  }
```

Inside a ZIO program, always compose with `flatMap` or `for`-comprehensions — synchronous extraction is an unsafe operation reserved for the outermost boundary of the application.

## Mapping the Error Channel

Monix's error-handling operators each have a ZIO counterpart. `mapError` has no Monix equivalent because `Task[A]`'s error channel is always `Throwable` and cannot be mapped to a domain type:

| Monix 3.x                                  | ZIO 2.x                               | Notes                                                              |
| ------------------------------------------- | --------------------------------------- | ------------------------------------------------------------------- |
| `task.onErrorHandleWith(f)`                 | `zio.catchAll(f)`                      | Recover from any error with an effect                               |
| `task.onErrorRecoverWith(pf)`               | `zio.catchSome(pf)`                    | Partial recovery — unmatched errors propagate unchanged             |
| `task.onErrorFallbackTo(task2)`             | `zio.orElse(that)`                     | Same name semantics                                                 |
| `task.onErrorRestart(n)`                    | `zio.retryN(n)`                        | Retry on failure up to `n` times                                   |
| `task.redeem(failure, success)`             | `zio.fold(failure, success)`           | Pure handlers on both branches                                      |
| `task.redeemWith(f, g)`                     | `zio.foldZIO(f, g)`                    | Effectful handlers on both branches                                 |
| `task.attempt`                              | `zio.either`                           | Materializes failure as `Either[E, A]`                              |
| `task.failed`                               | `zio.flip`                             | Swaps success and failure channels                                  |
| `task.materialize`                          | `zio.either` / `zio.exit`             | Use `.exit` for the full `Cause` including interruption             |
| `Task.raiseError(e)`                        | `ZIO.fail(e)`                          | —                                                                   |
| *(no equivalent)*                           | `zio.mapError(f)`                      | Narrows `Throwable` to a typed domain error; no Monix equivalent    |

**Before (Monix):**

```scala
import monix.eval.Task

sealed abstract class AppError(msg: String) extends RuntimeException(msg)
case class DbError(msg: String)      extends AppError(msg)

val failedQuery: Task[String] =
  Task.raiseError(new RuntimeException("connection refused"))

// handleErrorWith — recover from any Throwable
val recovered: Task[String] =
  failedQuery.onErrorHandleWith(e => Task.now(s"recovered: ${e.getMessage}"))

// redeem — pure handlers on both branches
val summarized: Task[String] =
  failedQuery.redeem(e => s"failed: ${e.getMessage}", a => s"ok: $a")

// materialize — turn failure into data
val inspected: Task[Either[Throwable, String]] = failedQuery.attempt
```

**After (ZIO):**

```scala mdoc:compile-only
import zio._

sealed trait AppError extends Throwable { def msg: String }
case class DbError(msg: String)      extends AppError
case class TimeoutError(msg: String) extends AppError

val failedQuery: IO[DbError, String] =
  ZIO.fail(DbError("connection refused"))

// Replace onErrorHandleWith
val recovered: UIO[String] =
  failedQuery.catchAll(e => ZIO.succeed(s"recovered: ${e.msg}"))

// Narrow Throwable to a domain type — unique to ZIO, no Monix equivalent
val rawQuery: Task[String] =
  ZIO.attempt(throw new RuntimeException("timeout"))

val typed: IO[AppError, String] =
  rawQuery.mapError {
    case e: RuntimeException => TimeoutError(e.getMessage)
    case other               => DbError(other.getMessage)
  }

// Replace task.redeem — pure handlers on both branches
val summarized: UIO[String] =
  typed.fold(e => s"failed: ${e.msg}", a => s"ok: $a")

// Replace task.attempt — materialize failure as Either
val inspected: UIO[Either[AppError, String]] = typed.either
```

`mapError` is the primary tool for lifting an untyped `Task[A]` (error = `Throwable`) into a domain-specific `IO[AppError, A]`. It has no Monix equivalent because `Task[A]` cannot represent typed errors at all.

## Managing Resource Lifecycles

Monix's `bracket` maps to `ZIO.acquireRelease` used inside `ZIO.scoped`. The scoped pattern keeps acquisition and release declarations flat and composes naturally across multiple resources in a single `for`-comprehension:

| Monix 3.x                                          | ZIO 2.x                                                    | Notes                                                          |
| --------------------------------------------------- | ----------------------------------------------------------- | --------------------------------------------------------------- |
| `task.bracket(release)(use)`                        | `ZIO.acquireReleaseWith(acquire)(release)(use)`             | All three phases explicit; no ambient `Scope`                   |
| `task.bracketCase(release)(use)`                    | `ZIO.acquireReleaseExitWith(acquire)(release)(use)`         | `release` receives `(A, Exit[E, B])` — both the acquired resource and the exit value |
| `task.guarantee(finalizer)`                         | `zio.ensuring(finalizer)`                                   | Finalizer runs on success, failure, or interruption             |
| `task.guaranteeCase(f)`                             | `zio.onExit(f)`                                            | Finalizer receives `Exit[E, A]`                                 |
| `ZIO.acquireRelease(acq)(rel)` inside `ZIO.scoped`  | `ZIO.acquireRelease(acq)(rel)`                              | Registers finalizer with ambient `Scope`; preferred for composition |

Stacking multiple resources with Monix requires nested `bracket` calls. With ZIO, `ZIO.acquireRelease` inside `ZIO.scoped` keeps resources flat in a single `for`-comprehension:

**Before (Monix)** — two resources mean two nested bracket calls:

```scala
import monix.eval.Task

case class DbConnection(id: Int) {
  def query(sql: String): Task[String] = Task.eval(s"conn-$id: $sql result")
  def close(): Task[Unit]              = Task.eval(println(s"Closing connection $id"))
}

def openConn(id: Int): Task[DbConnection] =
  Task.eval { println(s"Opening connection $id"); DbConnection(id) }

val program: Task[String] =
  openConn(1).bracket(_.close()) { conn1 =>
    openConn(2).bracket(_.close()) { conn2 =>
      conn1.query("SELECT 1")
    }
  }
```

**After (ZIO)** — one flat `for`-comprehension inside a single `ZIO.scoped`:

```scala mdoc:compile-only
import zio._

case class DbConnection(id: Int) {
  def query(sql: String): Task[String] = ZIO.attempt(s"conn-$id: $sql result")
  def close: UIO[Unit]                 = ZIO.succeed(println(s"Closing connection $id"))
}

def openConn(id: Int): Task[DbConnection] =
  ZIO.attempt { println(s"Opening connection $id"); DbConnection(id) }

def makeConn(id: Int): ZIO[Scope, Throwable, DbConnection] =
  ZIO.acquireRelease(openConn(id))(_.close)

val program: Task[String] =
  ZIO.scoped {
    for {
      conn1  <- makeConn(1)
      conn2  <- makeConn(2)
      result <- conn1.query("SELECT 1")
    } yield result
  }
```

When `ZIO.scoped` exits — on success, failure, or interruption — it runs each finalizer in reverse acquisition order. Both `conn2.close` and `conn1.close` are guaranteed to run.

The `ensuring`/`onExit` replacements preserve the same guarantee behavior:

```scala mdoc:compile-only
import zio._

val task: Task[String] = ZIO.attempt("work")

// Replace task.guarantee(finalizer)
val withFinalizer: Task[String] =
  task.ensuring(ZIO.succeed(println("always runs")))

// Replace task.guaranteeCase(f)
val withExitFinalizer: Task[String] =
  task.onExit {
    case exit if exit.isSuccess => ZIO.succeed(println("succeeded"))
    case _                      => ZIO.succeed(println("failed or interrupted"))
  }
```

:::caution[`guaranteeCase` Maps to `.onExit`, Not `.ensuringExit`]
`ensuringExit` does not exist in ZIO 2.x. The correct mapping for Monix `task.guaranteeCase(f)` is `zio.onExit(f: Exit[E, A] => URIO[R, Any])`. If you see `.ensuringExit` in older migration documentation or blog posts, replace it with `.onExit`.
:::

## Concurrency and Fibers

ZIO uses `task.fork` and `fiber.interrupt` where Monix uses `task.start` and `cancelable.cancel()`. Every ZIO fiber is interruptible by default with no opt-in required:

| Monix 3.x                                           | ZIO 2.x                                              | Notes                                                                |
| ---------------------------------------------------- | ----------------------------------------------------- | --------------------------------------------------------------------- |
| `task.start` → `Task[CancelableFuture[A]]`           | `zio.fork` → `URIO[R, Fiber.Runtime[E, A]]`          | Returns a fiber, not a `CancelableFuture`                             |
| `cancelable.cancel()`                                | `fiber.interrupt`                                    | `UIO[Exit[E, A]]`; always succeeds                                    |
| `cancelable.value` / `future.onComplete`             | `fiber.await` → `UIO[Exit[E, A]]`                   | Materializes the fiber result without re-raising                      |
| `Task.race(a, b)` → `Task[Either[A, B]]`            | `a.raceEither(b)` → `ZIO[R, E, Either[A, B]]`       | Different types — `Left` if `a` wins, `Right` if `b` wins            |
| `Task.race(a, b)` same type — first to succeed       | `a.race(b)` → `ZIO[R, E, A]`                        | Winner's value returned directly, not wrapped in `Either`             |
| `Task.racePair(a, b)`                                | `a.raceWith(b)(leftDone, rightDone)`                 | Low-level; whichever side finishes first invokes its callback with that side's `Exit` and the still-running (losing) `Fiber` — only one callback is ever called |
| `Task.parSequence(list)`                             | `ZIO.collectAllPar(list)`                            | Direct mapping                                                        |
| `Task.parSequenceN(n)(list)`                         | `ZIO.collectAllPar(list).withParallelism(n)`         | See gotcha below                                                      |
| `Task.parTraverse(list)(f)`                          | `ZIO.foreachPar(list)(f)`                            | Direct mapping                                                        |
| `Task.parTraverseN(n)(list)(f)`                      | `ZIO.foreachPar(list)(f).withParallelism(n)`         | See gotcha below                                                      |
| `Task.parMap2(a, b)(f)`                              | `a.zipWithPar(b)(f)`                                 | Replaces `Task.parMap2` and `Task.mapBoth`                            |
| `Task.map2(a, b)(f)`                                 | `a.zipWith(b)(f)`                                    | Sequential zip-with                                                   |
| `Task.parZip2(a, b)`                                 | `a.zipPar(b)`                                        | Produces `(A, B)` in parallel                                         |
| `task.uncancelable`                                  | `zio.uninterruptible`                                | Makes the effect immune to interruption                               |
| `task.doOnCancel(cleanup)`                           | `zio.onInterrupt(cleanup)`                           | Runs cleanup when the fiber is interrupted                            |

**Before (Monix):**

```scala
import monix.eval.Task
import scala.concurrent.duration._

val taskA: Task[String] = Task.eval("fast")
val taskB: Task[Int]    = Task.eval(42)

val program: Task[Unit] = for {
  // start a fiber
  cancelable <- Task.eval("work").start

  // race — returns Either[A, B] discriminating by which side won
  raced <- Task.race(Task.eval("left"), Task.sleep(1.second).as("right"))

  // parSequence — parallel collection
  results <- Task.parSequence(List(Task.eval(1), Task.eval(2), Task.eval(3)))

  // parMap2 — parallel zip
  pair <- Task.parMap2(Task.eval("hello"), Task.eval(42))((a, b) => (a, b))

  _ <- Task.eval(println(s"race=$raced results=$results pair=$pair"))
} yield ()
```

**After (ZIO):**

```scala mdoc:compile-only
import zio._

val taskA: Task[String] = ZIO.attempt("fast")
val taskB: Task[Int]    = ZIO.attempt(42)

val program: Task[Unit] = for {
  // fork — always returns a typed Fiber
  fiber   <- ZIO.attempt("work").fork

  // raceEither — returns Either[A, B] (true Monix race equivalent)
  raced   <- ZIO.attempt("left").raceEither(ZIO.sleep(1.second).as("right"))

  // collectAllPar — parallel collection
  results <- ZIO.collectAllPar(List(ZIO.succeed(1), ZIO.succeed(2), ZIO.succeed(3)))

  // zipWithPar — parallel zip
  pair    <- ZIO.succeed("hello").zipWithPar(ZIO.succeed(42))((a, b) => (a, b))

  _       <- ZIO.succeed(println(s"race=$raced results=$results pair=$pair"))
  _       <- fiber.interrupt
} yield ()
```

:::caution[`Task.race` Returns `Either` — Use `.raceEither`, Not `.race`]
Monix `Task.race(a: Task[A], b: Task[B])` returns `Task[Either[A, B]]` — `Left` if `a` finishes first, `Right` if `b` finishes first. This allows `a` and `b` to have different result types. The ZIO equivalent is `a.raceEither(b)`, which returns `ZIO[R, E, Either[A, B]]`.

The `race` method on `ZIO` has a different signature: `def race[R1 <: R, E1 >: E, A1 >: A](that: ZIO[R1, E1, A1]): ZIO[R1, E1, A1]` — it requires both sides to have the same result type and returns that type directly without wrapping in `Either`. The `raceFirst` method is different again: it returns the first fiber to finish, whether it succeeds or fails, and immediately propagates that result. Match carefully to the Monix semantics you need.
:::

:::caution[`collectAllParN` and `foreachParN` Do Not Exist in ZIO 2.x]
Monix's `Task.parSequenceN(n)(list)` and `Task.parTraverseN(n)(list)(f)` have no direct named equivalents in ZIO 2.x. The correct ZIO pattern calls `withParallelism(n)` on the result of the parallel combinator:

```scala mdoc:compile-only
import zio._

// Monix: Task.parSequenceN(2)(list)
val bounded1: Task[List[Int]] =
  ZIO.collectAllPar(List(ZIO.succeed(1), ZIO.succeed(2))).withParallelism(2)

// Monix: Task.parTraverseN(2)(list)(f)
val bounded2: Task[List[String]] =
  ZIO.foreachPar(List(1, 2, 3))(n => ZIO.succeed(n.toString)).withParallelism(2)
```

Do not try `ZIO.collectAllParN` or `ZIO.foreachParN` — neither method exists.
:::

:::caution[`mapN`, `mapParN`, and `tupledPar` Do Not Exist in ZIO 2.x]
ZIO has no `ZIO.mapN`, `ZIO.mapParN`, or `ZIO.tupledPar`. The replacements are `zipWith` (sequential), `zipWithPar` (parallel), and `zipPar` (parallel tuple). These are instance methods on `ZIO`, not companion-object methods.
:::

## Shared State

Monix provides two mechanisms for thread-safe state: `Atomic[A]` from `monix-execution` for lock-free mutable references, and `TaskLocal[A]` for fiber-local values. ZIO replaces them with `Ref[A]` and `FiberRef[A]` respectively — both fully integrated into the effect system.

### Replacing `Atomic` with `Ref`

Monix's `Atomic[A]` (from `monix-execution`) maps to `Ref[A]` in ZIO. `Ref` is always effectful — all reads and writes return `UIO` values, and every update is atomic:

| Monix `Atomic[A]`                           | ZIO `Ref[A]`                         | Signature                                                   |
| -------------------------------------------- | ------------------------------------- | ------------------------------------------------------------ |
| `Atomic(value)` / `AtomicAny(value)`         | `Ref.make(value)` → `UIO[Ref[A]]`   | `def make[A](a: => A): UIO[Ref[A]]`                         |
| `atomic.get()`                               | `ref.get`                            | `def get: UIO[A]`                                            |
| `atomic.set(v)`                              | `ref.set(v)`                         | `def set(a: A): UIO[Unit]`                                   |
| `atomic.getAndSet(v)`                        | `ref.getAndSet(v)`                   | `def getAndSet(a: A): UIO[A]`                                |
| `atomic.compareAndSet(expected, update)`     | `ref.modify(a => if (a == expected) (true, update) else (false, a))` | Use `modify` for atomic CAS — no standalone `compareAndSet` |
| `atomic.transform(f)`                        | `ref.update(f)`                      | `def update(f: A => A): UIO[Unit]`                           |
| `atomic.transformAndGet(f)`                  | `ref.updateAndGet(f)`                | `def updateAndGet(f: A => A): UIO[A]`                        |
| `AtomicInt` / `AtomicLong` / `AtomicBoolean` | `Ref[Int]` / `Ref[Long]` / `Ref[Boolean]` | No specialized types needed                              |

**Before (Monix):**

```scala
import monix.execution.atomic.Atomic

// Synchronous, mutable atomic reference
val counter = Atomic(0)
counter.transform(_ + 1)
val v: Int = counter.get()
println(s"counter: $v")
```

**After (ZIO):**

```scala mdoc:compile-only
import zio._

val program: UIO[Unit] = for {
  counter <- Ref.make(0)
  _       <- counter.update(_ + 1)
  v       <- counter.get
  _       <- ZIO.succeed(println(s"counter: $v"))
} yield ()
```

`Ref.make` returns a `UIO[Ref[A]]`, so the reference itself must be created inside the effect pipeline. Unlike Monix's `Atomic`, which is synchronous and mutable, `Ref` in ZIO is always used through the effect system — every read and write returns a `UIO` that describes the operation.

### Replacing `TaskLocal` with `FiberRef`

Monix's `TaskLocal[A]` holds fiber-local state: each fiber sees its own value, child fibers inherit the parent's value at fork time, and changes a child makes are not visible to the parent by default. `FiberRef[A]` in ZIO provides the same guarantee with the same `get`/`set`/`update` surface:

| Monix `TaskLocal[A]`                   | ZIO `FiberRef[A]`                        | Notes                                                            |
| --------------------------------------- | ----------------------------------------- | ----------------------------------------------------------------- |
| `TaskLocal(default)` → `Task[TaskLocal[A]]` | `FiberRef.make(initial)` → `ZIO[Scope, Nothing, FiberRef[A]]` | Scoped — create once at application startup |
| `local.read`                            | `fiberRef.get`                           | `def get: UIO[A]`                                                 |
| `local.write(v)`                        | `fiberRef.set(v)`                        | `def set(value: A): UIO[Unit]`                                    |
| `local.bind(v)(task)`                   | `fiberRef.locally(v)(zio)`              | Runs `zio` with the ref set to `v`; restores the original on exit |

The `fork` and `join` parameters of `FiberRef.make` control how the child's value is initialized at fork and how it merges back on join:

```scala mdoc:compile-only
import zio._

// FiberRef.make signature (initial is by-name / lazy):
// def make[A](initial: => A, fork: A => A = ZIO.identityFn[A], join: (A, A) => A = ZIO.secondFn[A]): ZIO[Scope, Nothing, FiberRef[A]]

val program: Task[Unit] =
  ZIO.scoped {
    for {
      requestId <- FiberRef.make("unset")
      _         <- requestId.set("req-42")
      // A forked child fiber inherits the parent's current value
      child     <- requestId.get.flatMap(v => ZIO.succeed(println(s"child sees: $v"))).fork
      _         <- child.join
      // Changes the child makes are not visible to the parent
      _         <- requestId.get.flatMap(v => ZIO.succeed(println(s"parent still sees: $v")))
    } yield ()
  }
```

Unlike Monix's `TaskLocal`, which is returned as a `Task[TaskLocal[A]]` created on demand, `FiberRef.make` in ZIO is scoped — the `FiberRef` itself is a resource tied to a `Scope`. Most applications create their `FiberRef`s once at startup inside the top-level `ZIOAppDefault` lifetime.

## Concurrent Data Structures

Monix's `monix.catnap` module provides concurrent primitives built on cats-effect. ZIO ships direct equivalents as part of `zio` core:

| Monix `monix.catnap`                        | ZIO 2.x                           | Notes                                                              |
| -------------------------------------------- | ----------------------------------- | ------------------------------------------------------------------- |
| `MVar[F].empty[A]` / `MVar[F].of(a)`        | `Queue.bounded[A](1)`              | Single-slot channel: `offer` blocks when full, `take` blocks when empty |
| `ConcurrentQueue[F].bounded[A](n)`           | `Queue.bounded[A](n)`              | Blocks producers when full                                          |
| `ConcurrentQueue[F].unbounded[A]`            | `Queue.unbounded[A]`               | Never blocks producers                                              |
| `ConcurrentQueue[F].withConfig(dropping)`    | `Queue.dropping[A](n)`             | Drops newest elements when full                                     |
| `ConcurrentQueue[F].withConfig(sliding)`     | `Queue.sliding[A](n)`              | Drops oldest elements when full                                     |
| `Semaphore[F].apply(n)`                      | `Semaphore.make(n)`                | Returns `UIO[Semaphore]`                                            |
| `ConcurrentChannel[F]`                       | `Hub[A]`                           | Broadcasts to all current subscribers                               |

**Before (Monix):**

```scala
import monix.eval.Task
import monix.catnap.{MVar, ConcurrentQueue}

val program: Task[Unit] = for {
  // MVar — single-slot channel
  mv <- MVar[Task].empty[Int]
  _  <- mv.put(42)
  n  <- mv.take

  // ConcurrentQueue — multi-slot queue
  q  <- ConcurrentQueue[Task].bounded[String](10)
  _  <- q.offer("hello")
  s  <- q.poll
} yield ()
```

**After (ZIO):**

```scala mdoc:compile-only
import zio._

val program: Task[Unit] = for {
  // MVar → Queue.bounded(1)
  mv <- Queue.bounded[Int](1)
  _  <- mv.offer(42)
  n  <- mv.take

  // ConcurrentQueue → Queue.bounded(n)
  q  <- Queue.bounded[String](10)
  _  <- q.offer("hello")
  s  <- q.poll
} yield ()
```

The `Hub[A]` replacement for `ConcurrentChannel` broadcasts to all current subscribers. Each subscriber receives its own independent copy of every published message:

```scala mdoc:compile-only
import zio._

val broadcast: Task[Unit] = ZIO.scoped {
  for {
    hub  <- Hub.bounded[String](16)
    sub1 <- hub.subscribe
    sub2 <- hub.subscribe
    _    <- hub.publish("hello, all")
    m1   <- sub1.take
    m2   <- sub2.take
    _    <- ZIO.succeed(println(s"sub1=$m1 sub2=$m2"))
  } yield ()
}
```

:::caution[`Semaphore` Has No Standalone `acquire`/`release`]
Monix's `Semaphore` has `acquire` and `release` as separate effectful calls that you pair manually. `Semaphore` in ZIO has no standalone `acquire` or `release` methods. The only way to use a permit is through `withPermit(zio)` or `withPermits(n)(zio)`, which acquire before running the effect and release after — on success, failure, or interruption:

```scala mdoc:compile-only
import zio._

val program: Task[Unit] = for {
  sem <- Semaphore.make(2)
  _   <- sem.withPermit(ZIO.succeed(println("exclusive section")))
  _   <- sem.withPermits(2)(ZIO.succeed(println("holds both permits")))
} yield ()
```
:::

## Streaming: Observable to ZStream

`Observable[A]` maps to `ZStream[R, E, A]`. The key conceptual differences are: `ZStream` is always pull-based and lazy; its error channel is typed (`E` rather than always `Throwable`); and `stream.runCollect` returns `Chunk[A]`, not `List[A]`.

### Observable Constructors

| Monix `Observable`                           | ZIO `ZStream`                              | Signature                                                              |
| --------------------------------------------- | ------------------------------------------- | ----------------------------------------------------------------------- |
| `Observable.fromIterable(list)`               | `ZStream.fromIterable(as)`                 | `def fromIterable[O](as: Iterable[O]): ZStream[Any, Nothing, O]`       |
| `Observable.fromTask(task)`                   | `ZStream.fromZIO(fa)`                      | `def fromZIO[R,E,A](fa: ZIO[R,E,A]): ZStream[R,E,A]`                  |
| `Observable.empty`                            | `ZStream.empty`                            | —                                                                       |
| `Observable.never`                            | `ZStream.never`                            | —                                                                       |
| `Observable.interval(period)`                 | `ZStream.tick(period)`                     | `def tick(interval: Duration): ZStream[Any, Nothing, Unit]` — emits `Unit` |
| `Observable.repeatTask(task)`                 | `ZStream.repeatZIO(fa)`                    | `def repeatZIO[R,E,A](fa: ZIO[R,E,A]): ZStream[R,E,A]`               |
| `Observable.fromSchedule(s)`                  | `ZStream.fromSchedule(schedule)`           | `def fromSchedule[R,A](schedule: Schedule[R,Any,A]): ZStream[R,Nothing,A]` |

### Observable Operators

| Monix `obs.X`                                | ZIO `stream.X`                              | Notes                                                                  |
| --------------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------------- |
| `obs.map(f)`                                  | `stream.map(f)`                             | —                                                                       |
| `obs.flatMap(f)`                              | `stream.flatMap(f)`                         | Sequential concatenation                                               |
| `obs.filter(p)`                               | `stream.filter(p)`                          | —                                                                       |
| `obs.take(n)`                                 | `stream.take(n)`                            | ZIO takes `Long`                                                        |
| `obs.drop(n)`                                 | `stream.drop(n)`                            | —                                                                       |
| `obs.takeWhile(p)`                            | `stream.takeWhile(p)`                       | —                                                                       |
| `obs.dropWhile(p)`                            | `stream.dropWhile(p)`                       | —                                                                       |
| `obs.scan(z)(f)`                              | `stream.scan(z)(f)`                         | —                                                                       |
| `obs.foldLeft(z)(f)` / `obs.foldLeftL(z)(f)` | `stream.runFold(z)(f)`                      | Returns a `ZIO[R, E, S]` effect                                         |
| `obs.toListL`                                 | `stream.runCollect`                         | Returns `ZIO[R, E, Chunk[A]]` — see gotcha below                        |
| `obs.firstL`                                  | `stream.runHead`                            | `def runHead: ZIO[R, E, Option[A]]`                                    |
| `obs.lastL`                                   | `stream.runLast`                            | `def runLast: ZIO[R, E, Option[A]]`                                    |
| `obs.countL`                                  | `stream.runCount`                           | `def runCount: ZIO[R, E, Long]`                                         |
| `obs.foreachL(f)` / `obs.foreach(f)`          | `stream.runForeach(f)`                      | `def runForeach[R1,E1>:E](f: A => ZIO[R1,E1,Any]): ZIO[R1,E1,Unit]`   |
| `obs.mergeMap(f)`                             | `stream.flatMapPar(n)(f)`                   | Concurrency degree is explicit                                          |
| `obs.flatMapLatest(f)`                        | `stream.flatMapParSwitch(1)(f)`             | "switch map" — replaces current inner with newest                       |
| `obs.debounce(d)`                             | `stream.debounce(d)`                        | —                                                                       |
| `obs.zip(other)`                              | `stream.zip(other)`                         | —                                                                       |
| `obs.merge(other)`                            | `stream.merge(other)`                       | —                                                                       |
| `Observable.merge(streams*)`                  | `ZStream.mergeAll(n)(streams*)`             | `def mergeAll[R,E,O](n: Int, outputBuffer: Int = 16)(streams: ZStream[R,E,O]*): ZStream[R,E,O]` |
| `obs.bufferWithCount(n)`                      | `stream.grouped(n)`                         | Emits `Chunk[A]` groups of size up to `n`                              |
| `obs.groupBy(f)`                              | `stream.groupByKey(f)` / `stream.groupBy(f)` | `groupByKey` for pure key fn; `groupBy` for effectful key fn           |
| `obs.onErrorHandleWith(f)`                    | `stream.catchAll(f)`                        | `def catchAll[R1,E2,A1>:A](f: E => ZStream[R1,E2,A1]): ZStream[R1,E2,A1]` |

**Before (Monix):**

```scala
import monix.eval.Task
import monix.reactive.Observable

val result: Task[List[Int]] =
  Observable.fromIterable(1 to 100)
    .filter(_ % 2 == 0)
    .map(_ * 3)
    .take(5)
    .toListL
```

**After (ZIO):**

```scala mdoc:compile-only
import zio._
import zio.stream._

val result: ZIO[Any, Nothing, Chunk[Int]] =
  ZStream.fromIterable(1 to 100)
    .filter(_ % 2 == 0)
    .map(_ * 3)
    .take(5)
    .runCollect

// Use .map(_.toList) when a List is required downstream
val asList: ZIO[Any, Nothing, List[Int]] =
  result.map(_.toList)
```

### Resource-Safe Streams

Monix's `Observable.fromResource` requires a cats-effect `Resource`. ZIO's `ZStream.acquireReleaseWith` registers the finalizer directly on the stream's scope, which is closed when the stream terminates or is interrupted:

**Before (Monix):**

```scala
import monix.eval.Task
import monix.reactive.Observable

case class FileHandle(name: String)

def openFile(name: String): Task[FileHandle] =
  Task.eval { println(s"Opening $name"); FileHandle(name) }
def closeFile(f: FileHandle): Task[Unit] =
  Task.eval(println(s"Closing ${f.name}"))
def readLine(f: FileHandle): Task[String] =
  Task.eval(s"line from ${f.name}")

// Resource-safe Observable using bracket
val lines: Observable[String] =
  Observable.fromTask(openFile("data.txt")).flatMap { handle =>
    Observable.fromTask(readLine(handle))
      .guarantee(closeFile(handle))
  }
```

**After (ZIO):**

```scala mdoc:compile-only
import zio._
import zio.stream._

case class FileHandle(name: String)

def openFile(name: String): Task[FileHandle] =
  ZIO.attempt { println(s"Opening $name"); FileHandle(name) }
def closeFile(f: FileHandle): UIO[Unit] =
  ZIO.succeed(println(s"Closing ${f.name}"))
def readLine(f: FileHandle): Task[String] =
  ZIO.attempt(s"line from ${f.name}")

// ZStream acquires the resource and releases it when the stream ends
val lines: ZStream[Any, Throwable, String] =
  ZStream.acquireReleaseWith(openFile("data.txt"))(closeFile).flatMap { handle =>
    ZStream.fromZIO(readLine(handle))
  }
```

:::caution[`runCollect` Returns `Chunk[A]`, Not `List[A]`]
`obs.toListL` in Monix returns `Task[List[A]]`. The ZIO equivalent `stream.runCollect` returns `ZIO[R, E, Chunk[A]]`. `Chunk` is ZIO's optimized immutable sequence type, not a `List`. Call `chunk.map(_.toList)` when downstream code requires a `List`, or use `Chunk` directly — it implements `Iterable[A]` and supports all the standard collection operations.
:::

:::caution[`ZStream.tick` Emits `Unit`, Not a `Long` Index]
Monix's `Observable.interval(period)` emits a `Long` counter starting at `0`. ZIO's `ZStream.tick(period)` emits `Unit` on each tick — there is no index. If you need a counter, use `ZStream.tick(period).zipWithIndex.map(_._2)`.
:::

## Running Effects Unsafely

Monix effects are run through an explicit `Scheduler` passed at the boundary. ZIO effects are run through `Runtime`, accessed via `Unsafe.unsafe`:

| Monix 3.x                              | ZIO 2.x                                                                  | Notes                                        |
| --------------------------------------- | ------------------------------------------------------------------------- | --------------------------------------------- |
| `task.runSyncUnsafe()`                  | `Unsafe.unsafe { implicit u => Runtime.default.unsafe.run(zio).getOrThrow() }` | Synchronous extraction at program boundary |
| `task.runToFuture(scheduler)`           | `Unsafe.unsafe { implicit u => Runtime.default.unsafe.runToFuture(zio) }` | Returns `CancelableFuture[A]`                |
| `task.runAsync(callback)(scheduler)`    | `Unsafe.unsafe { implicit u => Runtime.default.unsafe.fork(zio) }`       | Returns `Fiber.Runtime[E, A]` immediately    |

**Before (Monix):**

```scala
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

val result: String  = Task.eval("hello").runSyncUnsafe()
val future          = Task.eval("hello").runToFuture
```

**After (ZIO):**

```scala mdoc:compile-only
import zio._

// Synchronous run — returns Exit[E, A], not A directly
val exit: Exit[Throwable, String] =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(ZIO.attempt("hello"))
  }

// Extract the value, throwing on failure
val value: String =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(ZIO.attempt("hello")).getOrThrow()
  }

// Run to Future — for interop with Future-based libraries
val future =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.runToFuture(ZIO.attempt("hello"))
  }
```

:::caution[`runtime.unsafe.run` Returns `Exit[E, A]`, Not `A`]
Monix's `task.runSyncUnsafe()` returns `A` directly and throws on failure. ZIO's `Runtime.default.unsafe.run(zio)` returns `Exit[E, A]` — a value that is either `Exit.Success(a)` or `Exit.Failure(cause)`. To extract `A` and throw on failure, call `getOrThrow()` on the returned `Exit` value. To pattern-match instead:

```scala mdoc:compile-only
import zio._

val exit: Exit[Throwable, Int] =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(ZIO.attempt(42))
  }

exit match {
  case Exit.Success(value)  => println(s"Got: $value")
  case Exit.Failure(cause)  => println(s"Failed: ${cause.prettyPrint}")
}
```
:::

## Testing

Monix's `TestScheduler` advances a virtual clock to test time-dependent effects. `TestClock` in ZIO serves the same role, integrating directly with `zio-test`:

| Monix 3.x (`TestScheduler`)              | ZIO 2.x (`TestClock`)                            | Notes                                                          |
| ----------------------------------------- | ------------------------------------------------- | --------------------------------------------------------------- |
| `TestScheduler()`                         | `TestClock` (provided automatically by `zio-test`) | No constructor call needed; provided by the test environment   |
| `testScheduler.tick(d)`                   | `TestClock.adjust(d)`                            | Advances the virtual clock, unblocking sleeping fibers         |
| `testScheduler.clockMonotonic()`          | `TestClock.currentTime(unit)`                    | Read the current virtual time                                   |

**Before (Monix):**

```scala
import monix.eval.Task
import monix.execution.schedulers.TestScheduler
import scala.concurrent.duration._

implicit val testScheduler: TestScheduler = TestScheduler()

val f = Task.sleep(1.second).as("done").runToFuture
// f.value is None — the sleep hasn't fired yet

testScheduler.tick(1.second)
// f.value is now Some(Success("done"))
assert(f.value.isDefined)
```

**After (ZIO):**

```scala mdoc:compile-only
import zio._
import zio.test.{TestClock, assertTrue, Spec}

val myTest: Spec[Any, Nothing] =
  zio.test.test("effect completes after delay") {
    for {
      fiber  <- ZIO.sleep(1.second).as("done").fork
      _      <- TestClock.adjust(1.second)
      result <- fiber.join
    } yield assertTrue(result == "done")
  }
```

:::caution[Fork the Fiber Before Adjusting the Clock]
The fiber performing `ZIO.sleep` must be forked before `TestClock.adjust` is called. If you call `TestClock.adjust` first and then create the sleeping fiber, the sleep starts after the clock has already advanced — the fiber's sleep threshold is never crossed and it sleeps forever. Always fork first, then adjust:

```scala mdoc:compile-only
import zio._
import zio.test.{TestClock, assertTrue, Spec}

val correct: Spec[Any, Nothing] =
  zio.test.test("correct order — fork then adjust") {
    for {
      fiber  <- ZIO.sleep(5.seconds).as("result").fork  // 1. fork first
      _      <- TestClock.adjust(5.seconds)              // 2. then advance clock
      result <- fiber.join                               // 3. then join
    } yield assertTrue(result == "result")
  }
```
:::

## Putting It Together

The program below combines every migration pattern from this guide into one runnable ZIO 2.x application:

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/CompleteExample.scala
```

## Running the Examples

Clone the repository and change into the examples module. Every step ships as two independently runnable programs — the Monix original under `migratefrommonix.monix.*` and its ZIO translation under `migratefrommonix.*` — so you can run both, compare output, and diff the source side by side:

```bash
git clone https://github.com/zio/zio.git
cd zio/zio-examples
```

<details>
<summary>Entry Point</summary>

**Before (Monix):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/monix/Step1EntryPoint.scala:show-line-numbers
```

Run the Monix entry-point program:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.monix.Step1EntryPoint"
```

**After (ZIO):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/Step1EntryPoint.scala:show-line-numbers
```

Run the ZIO entry-point program to confirm `ZIOAppDefault` starts and exits cleanly:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.Step1EntryPoint"
```

</details>

<details>
<summary>Effect Constructors</summary>

**Before (Monix):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/monix/Step2EffectConstructors.scala:show-line-numbers
```

Run the Monix effect-constructor example:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.monix.Step2EffectConstructors"
```

**After (ZIO):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/Step2EffectConstructors.scala:show-line-numbers
```

Run the ZIO constructor example to see `ZIO.attempt`, `ZIO.succeed`, and `ZIO.suspend` in action:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.Step2EffectConstructors"
```

</details>

<details>
<summary>Error Handling</summary>

**Before (Monix):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/monix/Step3ErrorHandling.scala:show-line-numbers
```

Run the Monix error-handling example:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.monix.Step3ErrorHandling"
```

**After (ZIO):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/Step3ErrorHandling.scala:show-line-numbers
```

Run the ZIO error-handling example to observe `catchAll`, `mapError`, `fold`, and `either`:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.Step3ErrorHandling"
```

</details>

<details>
<summary>Resources</summary>

**Before (Monix)** — note the nested `bracket` calls:

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/monix/Step4Resources.scala:show-line-numbers
```

Run the Monix resource example:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.monix.Step4Resources"
```

**After (ZIO):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/Step4Resources.scala:show-line-numbers
```

Run the ZIO resource example to see finalizers print in reverse acquisition order:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.Step4Resources"
```

</details>

<details>
<summary>Concurrency</summary>

**Before (Monix):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/monix/Step5Concurrency.scala:show-line-numbers
```

Run the Monix concurrency example:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.monix.Step5Concurrency"
```

**After (ZIO):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/Step5Concurrency.scala:show-line-numbers
```

Run the ZIO concurrency example to observe `fork`, `interrupt`, `raceEither`, and `foreachPar`:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.Step5Concurrency"
```

</details>

<details>
<summary>Shared State</summary>

**Before (Monix):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/monix/Step6SharedState.scala:show-line-numbers
```

Run the Monix shared-state example:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.monix.Step6SharedState"
```

**After (ZIO):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/Step6SharedState.scala:show-line-numbers
```

Run the ZIO shared-state example to see `Ref` updates and `FiberRef` scoped fiber-local state:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.Step6SharedState"
```

</details>

<details>
<summary>Concurrent Data Structures</summary>

**Before (Monix):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/monix/Step7ConcurrentDataStructures.scala:show-line-numbers
```

Run the Monix concurrent-data-structures example:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.monix.Step7ConcurrentDataStructures"
```

**After (ZIO):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/Step7ConcurrentDataStructures.scala:show-line-numbers
```

Run the ZIO data-structures example to see `Queue`, `Semaphore.withPermit`, and `Hub` in action:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.Step7ConcurrentDataStructures"
```

</details>

<details>
<summary>Streaming</summary>

**Before (Monix):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/monix/Step8Streaming.scala:show-line-numbers
```

Run the Monix streaming example:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.monix.Step8Streaming"
```

**After (ZIO):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/Step8Streaming.scala:show-line-numbers
```

Run the ZIO streaming example to observe `ZStream` constructors, operators, and `runCollect`:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.Step8Streaming"
```

</details>

<details>
<summary>Running Unsafely</summary>

**Before (Monix):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/monix/Step9RunningUnsafely.scala:show-line-numbers
```

Run the Monix unsafe-execution example:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.monix.Step9RunningUnsafely"
```

**After (ZIO):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/Step9RunningUnsafely.scala:show-line-numbers
```

Run the ZIO unsafe-execution example to see `Unsafe.unsafe` and `Exit` pattern-matching:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.Step9RunningUnsafely"
```

</details>

<details>
<summary>Testing</summary>

**Before (Monix):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/monix/Step10Testing.scala:show-line-numbers
```

Run the Monix testing example:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.monix.Step10Testing"
```

**After (ZIO):**

```scala mdoc:embed:zio-examples/migrate-from-monix/src/main/scala/migratefrommonix/Step10Testing.scala:show-line-numbers
```

Run the ZIO testing example to observe `TestClock.adjust` advancing virtual time:

```bash
sbt "migrate-from-monix/runMain migratefrommonix.Step10Testing"
```

</details>

## Going Further

The following reference pages cover the ZIO types used throughout this guide in full detail:

- [Migrate from Cats Effect](./from-cats-effect.md) — a parallel guide for codebases coming from cats-effect 3.x `IO`, `Resource`, and `IOLocal`.
- [Ref](../../reference/concurrency/ref.md) — full reference for ZIO's concurrent mutable reference, covering `modify`, continuations, and `Ref.Synchronized`.
- [Queue](../../reference/concurrency/queue.md) — full reference for `Queue`, covering all four constructor variants and their backpressure semantics.
- [Semaphore](../../reference/concurrency/semaphore.md) — full reference for `Semaphore`, including `withPermit`, `withPermits`, and `available`.
- [Hub](../../reference/concurrency/hub.md) — full reference for `Hub`, the broadcast channel that replaces `ConcurrentChannel`.
- [Promise](../../reference/concurrency/promise.md) — full reference for `Promise[E, A]`, ZIO's typed one-shot completion mechanism.
- [FiberRef](../../reference/state-management/fiberref.md) — the replacement for `TaskLocal`, covering fork/join value propagation and the `locally` scoping combinator.
- [Fiber](../../reference/fiber/fiber.md) — detailed coverage of the fiber lifecycle, supervision, interruption semantics, and structured concurrency patterns.
- [Schedule](../../reference/schedule/index.md) — the retry and repetition API, covering built-in schedules, combinators, and the full replacement for Monix's scheduling patterns.
- [ZStream Overview](../../reference/stream/zstream/index.md) — introduction to `ZStream` and its place in the ZIO ecosystem.
- [Creating ZStreams](../../reference/stream/zstream/creating-zio-streams.md) — detailed coverage of all `ZStream` constructors, including resource-safe streams.
- [ZStream Operations](../../reference/stream/zstream/operations.md) — full reference for transformation, filtering, merging, and grouping operators.
- [ZStream Error Handling](../../reference/stream/zstream/error-handling.md) — `catchAll`, `orElse`, and recovery patterns on streams.
- [Resourceful Streams](../../reference/stream/zstream/resourceful-streams.md) — `ZStream.acquireReleaseWith`, `ZStream.scoped`, and finalizer guarantees.
