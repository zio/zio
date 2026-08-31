# Migrate from Cats Effect to ZIO

> A comprehensive reference for mapping cats-effect 3.x IO, Resource, Fiber, Ref, Deferred, typeclasses, and the std module to their ZIO 2.x equivalents

## Introduction

This guide is a comprehensive reference for migrating a cats-effect 3.x application to ZIO 2.x. Rather than walking through a single example from start to finish, it is organized by topic — `IO`, `Resource`, `Fiber`, typeclasses, `cats.effect.std`, time and retries, testing, and more — so you can jump directly to whatever construct your codebase uses. Every mapping is backed by a two-column table (cats-effect 3.x → ZIO 2.x), and most sections show the actual cats-effect code next to its ZIO replacement — both sides compiled against the real libraries, not just described in prose.

What this guide covers:

- [Replacing the Application Entry Point](#replacing-the-application-entry-point) — `IOApp`, `SyncIO`, `ExitCode`
- [Translating Effect Constructors](#translating-effect-constructors) — `IO.pure`, `IO.async`, `IO.sleep`, and more
- [Typing Your Error Channel](#typing-your-error-channel) — `handleErrorWith`, `redeem`, `adaptError`, and more
- [Managing Resource Lifecycles](#managing-resource-lifecycles) — `Resource`, `ExitCase`
- [Cats-Effect Typeclasses vs. Direct ZIO Usage](#cats-effect-typeclasses-vs-direct-zio-usage) — `Sync`, `Async`, `Concurrent`, `Temporal`, `MonadCancel`
- [Forking Fibers and Running Effects in Parallel](#forking-fibers-and-running-effects-in-parallel) — `Fiber`, `Outcome`, `Poll`
- [Shared State and Cross-Fiber Signaling](#shared-state-and-cross-fiber-signaling) — `Ref`, `Deferred`, `IOLocal`
- [Concurrent Data Structures from cats-effect's std Module](#concurrent-data-structures-from-cats-effects-std-module) — `Queue`, `Semaphore`, `CountDownLatch`, `Dispatcher`, and the rest of `cats.effect.std`
- [Time, Timeouts, and Retries](#time-timeouts-and-retries) — `Temporal`, `IO.sleep`, cats-retry
- [Runtime Configuration and Thread Model](#runtime-configuration-and-thread-model)
- [Testing](#testing) — munit-cats-effect, `TestControl`
- [Streaming: fs2 to ZStream](#streaming-fs2-to-zstream)

The six patterns most commonly hit first — entry point, effect constructors, error channel, resources, fibers, shared state — are demonstrated together in one runnable program in [Putting It Together](#putting-it-together); the remaining sections extend the reference to the rest of the cats-effect 3.x surface, even where no single example uses every construct at once.

## The Problem

Cats-effect's `IO[A]` carries its error type implicitly as `Throwable`. Every function that might fail looks the same in its signature regardless of whether it throws a domain error or a raw exception, so the compiler cannot catch missing handlers or mismatched error types. `Resource[IO, A]` requires a `.use` callback at every call site: stacking two resources means nested `.use` calls, and acquisition and release code ends up interleaved with business logic. Fibers add another gap: `fiber.cancel` only works when the wrapped `IO` opts in to cancelability via `Poll`, making interruption behavior invisible from the outside.

`Deferred[IO, A]` can only be completed with a success value; there is no way to push a typed failure through it, so cross-fiber error signaling falls back to a shared `Ref` holding an `Either` or an `Option`. All of these costs compound as the application grows: adding a new failure mode means grepping for every `.handleErrorWith` rather than following the compiler.

The cats-effect application below shows these patterns together:

```scala
import cats.effect.{IO, IOApp, Resource}
import cats.effect.kernel.{Ref, Deferred}
import cats.syntax.all._

import scala.concurrent.duration._

sealed abstract class AppError(msg: String) extends RuntimeException(msg)
case class DbError(msg: String)      extends AppError(msg)
case class TimeoutError(msg: String) extends AppError(msg)

case class DbConnection(id: Int) {
  def query(sql: String): IO[String] = IO(s"conn-$id: $sql result")
  def close(): IO[Unit] = IO(println(s"Closing connection $id"))
}

def makeDbConnection(id: Int): Resource[IO, DbConnection] =
  Resource.make(
    IO(println(s"Opening connection $id")).as(DbConnection(id))
  )(conn => conn.close())

def worker(id: Int, counter: Ref[IO, Int], done: Deferred[IO, String]): IO[Unit] =
  makeDbConnection(id).use { conn =>
    for {
      result <- conn.query("SELECT 1")
                  .handleErrorWith(e => IO.raiseError(DbError(e.getMessage)))
      n      <- counter.updateAndGet(_ + 1)
      _      <- IO(println(s"Worker $id got: $result, total: $n"))
      _      <- if (n >= 2) done.complete(s"Worker $id finished last").void else IO.unit
    } yield ()
  }

object WorkerPool extends IOApp.Simple {
  def run: IO[Unit] =
    for {
      counter <- Ref.of[IO, Int](0)
      done    <- Deferred[IO, String]
      fiber1  <- worker(1, counter, done).start
      fiber2  <- worker(2, counter, done).start
      result  <- IO.race(done.get, IO.sleep(5.seconds).as("timeout"))
      msg     <- result match {
                   case Left(doneMsg)  => IO.pure(doneMsg)
                   case Right(timeout) =>
                     fiber1.cancel *> fiber2.cancel *> IO.raiseError(TimeoutError(timeout))
                 }
      _       <- IO(println(s"Final: $msg"))
      _       <- fiber1.join
      _       <- fiber2.join
      results <- List(1, 2, 3).parTraverse(i => IO(i * i))
      _       <- IO(println(s"Squares: $results"))
      pair    <- (IO(42), IO("hello")).parMapN((x, y) => (x, y))
      _       <- IO(println(s"parMapN: ${pair._1}, ${pair._2}"))
    } yield ()
}
```

This guide replaces every pattern shown above with its ZIO equivalent, and then extends the coverage to the rest of the cats-effect API.

## Prerequisites

Add the ZIO core library to `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio" % "2.1.26"
```

All types this guide uses — `ZIO`, `Task`, `UIO`, `Ref`, `Promise`, `Fiber`, `Scope`, `ZIOAppDefault` — come from a single import:

```scala
import zio._
```

A handful of sections use additional modules — `zio-concurrent` for `CountdownLatch`/`CyclicBarrier`, and `zio-streams` for `ZStream` — noted where they apply.

Assumed knowledge: familiarity with cats-effect 3.x (`IO`, `Resource`, `Fiber`, `Ref`, `Deferred`) and basic Scala `for`-comprehension syntax.

:::note[Incremental Migration]
If the codebase depends on cats-effect libraries that cannot be migrated immediately (doobie, http4s, fs2), add the interop module:

```scala
libraryDependencies += "dev.zio" %% "zio-interop-cats" % "23.1.0.3"
```

With `import zio.interop.catz._`, a `ZIO[R, Throwable, A]` satisfies cats-effect type classes so existing libraries continue to work unchanged. See [Interoperating with Cats Effect](../interop/with-cats-effect.md) for the full setup. The interop layer is a migration tool, not a destination.
:::

## The Core Model

The examples throughout this guide work with these domain types. Define them once and carry them through each section:

```scala
case class DbConnection(id: Int) {
  def query(sql: String): Task[String] = ZIO.attempt(s"conn-$id: $sql result")
  def close: UIO[Unit]                 = ZIO.succeed(println(s"Closing connection $id"))
}

sealed trait AppError extends Throwable
case class DbError(msg: String)      extends AppError
case class TimeoutError(msg: String) extends AppError
```

`query` wraps a side-effecting call in `ZIO.attempt` — the ZIO equivalent of `IO(body)` — because the body may throw. `close` uses `ZIO.succeed` because it cannot throw, matching the intent of `IO.pure(…)` on a safe value. `AppError` and its subtypes appear in the error channel in the next section.

## Replacing the Application Entry Point

Replace `IOApp.Simple` with `ZIOAppDefault` and change the `def run: IO[Unit]` return type to `Task[Unit]`. The ZIO runtime, shutdown hooks, and default fiber scheduler are all provided by `ZIOAppDefault` — no additional configuration is required:

**Before (cats-effect):**

```scala
import cats.effect.{IO, IOApp}

object WorkerPool extends IOApp.Simple {
  def run: IO[Unit] =
    IO(println("Application started under cats-effect runtime"))
}
```

**After (ZIO):**

```scala
import zio._

object WorkerPool extends ZIOAppDefault {
  def run: Task[Unit] =
    ZIO.succeed(println("Application started under ZIO runtime"))
}
```

The rest of the cats-effect entry-point surface maps as follows:

| cats-effect 3.x                                | ZIO 2.x                                    | Notes                                                                                     |
| ----------------------------------------------- | ------------------------------------------ | ------------------------------------------------------------------------------------------ |
| `IOApp`                                         | `ZIOApp`                                   | Full trait, used when you need custom `bootstrap` layers or `Runtime` configuration        |
| `IOApp.Simple`                                  | `ZIOAppDefault`                            | The common case — no custom layers required                                                |
| `def run(args: List[String]): IO[ExitCode]`     | `def run: ZIO[R, E, Any]`                  | `run` doesn't need to produce an `ExitCode` — see below                                    |
| `SyncIO[A]`                                     | `Task[A]` / `UIO[A]`                       | ZIO has no separate synchronous-only effect type — see below                               |

:::caution[No ExitCode Required]
In cats-effect, `IOApp#run` must produce an `IO[ExitCode]`, so every program ends with an explicit `ExitCode.Success`/`ExitCode.Error` value. In ZIO, `run` can return any type — the runtime determines the process exit code from whether the effect succeeded, failed, or was interrupted. `zio.ExitCode` still exists (for interop with code that needs to construct one explicitly, or via `ZIO#exitCode`), but ordinary application code rarely needs to reach for it.
:::

:::caution[No SyncIO Equivalent]
Cats-effect's `SyncIO[A]` restricts the effect to synchronous, non-blocking operations — it exists so a library can guarantee at compile time that it never needs a thread pool for async or blocking work. ZIO does not have a separate synchronous-only type: `Task`/`UIO` cover both synchronous and asynchronous code, and the runtime handles scheduling either way. Code written against `SyncIO` migrates to ordinary `ZIO.succeed`/`ZIO.attempt` — drop the `SyncIO` type entirely, there's nothing to replace it with structurally.
:::

:::caution[ZIO 1.x Names]
`App`, `ZIO.effect`, and `ZIO.effectTotal` were all removed in ZIO 2.x. Always use `ZIOAppDefault`, `ZIO.attempt`, and `ZIO.succeed`. If you are also migrating from ZIO 1.x, the Scalafix rule `Zio2Upgrade` renames them automatically. See the [ZIO 1.x → 2.x Migration Guide](migration-guide.md) for the complete rename table.

`ZIOAppDefault` provides `val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = ZLayer.empty`. If you see `def layer` in older ZIO 2.x preview documentation or blog posts, it was renamed to `bootstrap` before the 2.0 release.
:::

## Translating Effect Constructors

Every cats-effect constructor maps to a ZIO counterpart:

| cats-effect 3.x                        | ZIO 2.x                       | Notes                                                                 |
| --------------------------------------- | ------------------------------ | ---------------------------------------------------------------------- |
| `IO(body)` / `IO.delay(body)`           | `ZIO.attempt(body)`            | Wraps code that may throw                                              |
| `IO.pure(a)`                            | `ZIO.succeed(a)`               | Already-computed or non-throwing                                       |
| `IO.unit`                               | `ZIO.unit`                     | —                                                                       |
| `IO.never`                              | `ZIO.never`                    | —                                                                       |
| `IO.raiseError(e)`                      | `ZIO.fail(e)`                  | —                                                                       |
| `IO.fromEither(e)`                      | `ZIO.fromEither(e)`            | —                                                                       |
| `IO.fromOption(o)(orElse)`              | `ZIO.fromOption(o)`            | ZIO's error channel is `Option[Nothing]`; use `.orElseFail(e)` to attach a typed error, matching CE's explicit `orElse` |
| `IO.fromTry(t)`                         | `ZIO.fromTry(t)`               | Returns `Task[A]`, error type is always `Throwable`                    |
| `IO.fromFuture(IO(future))`             | `ZIO.fromFuture(ec => future)` | ZIO's variant takes the `ExecutionContext => Future[A]` function directly, no outer `IO` wrapper needed |
| `IO.async_(cb)`                         | `ZIO.async(register)`          | Fire-and-forget callback; `cb: Either[Throwable, A] => Unit` becomes `register: (ZIO[R, E, A] => Unit) => Unit` |
| `IO.async(register)`                    | `ZIO.asyncInterrupt(register)` | CE's optional cancel finalizer (`IO[Option[IO[Unit]]]`) becomes `Left(canceler)` in the `Either` ZIO's register function returns |
| `IO.sleep(duration)`                    | `ZIO.sleep(duration)`          | —                                                                       |
| `IO.realTime` / `IO.monotonic`          | `Clock.currentTime(unit)` / `Clock.nanoTime` | Built-in `Clock` service, no environment requirement in ZIO 2.x |
| `IO.uncancelable(poll => body)`         | `ZIO.uninterruptibleMask(restore => body)` | See [Forking Fibers](#forking-fibers-and-running-effects-in-parallel) for the full `Poll`/interruption mapping |

Use `ZIO.attemptBlocking(body)` for JDBC calls, file I/O, or any computation that blocks a thread — it shifts execution to ZIO's dedicated blocking thread pool rather than occupying a fiber worker.

The following shows the core constructors in place, producing effect values as data — the cats-effect version first, then the same program translated to ZIO:

**Before (cats-effect):**

```scala
import cats.effect.IO

import scala.concurrent.duration.{FiniteDuration, SECONDS}

val fetched:    IO[String]  = IO("result from database")
val constant:   IO[Int]     = IO.pure(42)
val unit:       IO[Unit]    = IO.unit
val never:      IO[Nothing] = IO.never
val raiseErr:   IO[Nothing] = IO.raiseError(new RuntimeException("oops"))
val fromEither: IO[Int]     = IO.fromEither(Right(1): Either[Throwable, Int])
val fromTry:    IO[Int]     = IO.fromTry(scala.util.Try(1 / 1))
val slept:      IO[Unit]    = IO.sleep(FiniteDuration(1, SECONDS))
val now:        IO[Long]    = IO.monotonic.map(_.toNanos)

val program: IO[String] = for {
  a <- IO("hello")
  b <- IO.pure(" world")
} yield a + b
```

**After (ZIO):**

```scala
import zio._

val fetched:   Task[String]           = ZIO.attempt("result from database")
val constant:  UIO[Int]               = ZIO.succeed(42)
val unit:      UIO[Unit]              = ZIO.unit
val never:     UIO[Nothing]           = ZIO.never
val raiseErr:  Task[Nothing]          = ZIO.fail(new RuntimeException("oops"))
val fromEither: IO[String, Int]       = ZIO.fromEither(Right(1): Either[String, Int])
val fromTry:   Task[Int]              = ZIO.fromTry(scala.util.Try(1 / 1))
val slept:     UIO[Unit]              = ZIO.sleep(1.second)
val now:       UIO[Long]              = Clock.nanoTime

val program: Task[String] = for {
  a <- ZIO.attempt("hello")
  b <- ZIO.succeed(" world")
} yield a + b
```

`ZIO.attempt` returns `Task[A]`, which is the alias `ZIO[Any, Throwable, A]` — the closest equivalent to cats-effect's `IO[A]`. `ZIO.succeed` returns `UIO[A]`, meaning `ZIO[Any, Nothing, A]`, a value that cannot fail. The aliases `Task`, `UIO`, `RIO`, `URIO`, and ZIO's two-parameter `IO[E, A]` are all defined in `zio.package` and available after `import zio._`.

## Typing Your Error Channel

`IO[A]`'s error channel is always `Throwable` and invisible to the compiler. `ZIO[R, E, A]` makes `E` explicit, so the compiler enforces exhaustive handling. The replacement operators are:

| cats-effect 3.x                          | ZIO 2.x                              | Notes                                                              |
| ---------------------------------------- | ------------------------------------- | -------------------------------------------------------------------- |
| `IO.raiseError(e)`                       | `ZIO.fail(e)`                        | —                                                                    |
| `io.handleErrorWith(f)`                  | `zio.catchAll(f)`                    | —                                                                    |
| `io.recover { case e: X => … }`          | `zio.catchSome { case e: X => … }`   | —                                                                    |
| `io.recoverWith { case e: X => io2 }`    | `zio.catchSome { case e: X => io2 }` | `catchSome`'s partial function already returns a `ZIO`, so `recover` and `recoverWith` collapse into the same combinator |
| `io.redeem(recover, map)`                | `zio.fold(recover, map)`             | Pure handlers on both branches; ZIO 1.x also spelled this `redeem`, renamed in 2.x |
| `io.redeemWith(recover, bind)`           | `zio.foldZIO(recover, bind)`         | Effectful handlers on both branches; ZIO 1.x spelled this `redeemWith` / `foldM` |
| `io.adaptError { case e: X => e2 }`      | *(compose manually)*                 | No single built-in; use `zio.catchSome { case e: X => ZIO.fail(e2) }` — unmatched errors pass through unchanged, same as `adaptError` |
| `io.onError(f)`                          | `zio.tapError(f)`                    | Observes the error without swallowing it — the original failure still propagates |
| `io.orElse(fallback)`                    | `zio.orElse(fallback)`               | Same name in both libraries                                          |
| `io.attempt`                             | `zio.either`                         | —                                                                    |
| *(no equivalent)*                        | `zio.mapError(f)`                    | —                                                                    |

`mapError` is the primary tool for lifting an untyped `Task[A]` (error = `Throwable`) into a domain-specific `IO[AppError, A]`. It has no cats-effect equivalent because `IO[A]` cannot represent typed errors at all.

The block below demonstrates each replacement, starting from `raiseError`/`ZIO.fail` and progressing through recovery, type narrowing, and error materialization — the cats-effect version first, then the same program translated to ZIO:

**Before (cats-effect):**

```scala
import cats.effect.IO

sealed trait AppError extends Throwable { def msg: String }
case class DbError(msg: String)      extends AppError
case class TimeoutError(msg: String) extends AppError

// Raise a typed domain error
val failedQuery: IO[String] =
  IO.raiseError(DbError("connection refused"))

// handleErrorWith — recover from any Throwable
val recovered: IO[String] =
  failedQuery.handleErrorWith(e => IO(s"recovered: ${e.getMessage}"))

// A call that may throw
val rawQuery: IO[String] =
  IO(throw new RuntimeException("timeout"))

// adaptError — narrow Throwable to a domain error type
val typed: IO[String] =
  rawQuery.adaptError {
    case e: RuntimeException => TimeoutError(e.getMessage)
    case other               => DbError(other.getMessage)
  }

// redeem — pure handlers on both branches
val summarized: IO[String] =
  typed.redeem(e => s"failed: ${e.getMessage}", r => s"ok: $r")

// onError — observe without swallowing; takes a PartialFunction
val observed: IO[String] =
  typed.onError { case e => IO(println(s"logging failure: ${e.getMessage}")) }

// attempt — materialise failure as Either
val inspected: IO[Either[Throwable, String]] = typed.attempt
```

**After (ZIO):**

```scala
import zio._

sealed trait AppError extends Throwable { def msg: String }
case class DbError(msg: String)      extends AppError
case class TimeoutError(msg: String) extends AppError

// Replace IO.raiseError
val failedQuery: IO[DbError, String] =
  ZIO.fail(DbError("connection refused"))

// Replace io.handleErrorWith
val recovered: UIO[String] =
  failedQuery.catchAll(e => ZIO.succeed(s"recovered: ${e.msg}"))

// Narrow Throwable to a domain error type — no cats-effect equivalent
val rawQuery: Task[String] =
  ZIO.attempt(throw new RuntimeException("timeout"))

val typed: IO[AppError, String] =
  rawQuery.mapError {
    case e: RuntimeException => TimeoutError(e.getMessage)
    case other               => DbError(other.getMessage)
  }

// Replace io.redeem — pure handlers on both branches
val summarized: UIO[String] =
  typed.fold(e => s"failed: ${e.msg}", r => s"ok: $r")

// Replace io.onError — observe without swallowing
val observed: IO[AppError, String] =
  typed.tapError(e => ZIO.succeed(println(s"logging failure: ${e.msg}")))

// Replace io.attempt — materialise failure as Either
val inspected: UIO[Either[AppError, String]] = typed.either
```

## Managing Resource Lifecycles

`Resource.make(acquire)(release)` maps to `ZIO.acquireRelease(acquire)(release)`, which registers the finalizer with an ambient `Scope`. `ZIO.scoped` creates a `Scope`, runs the block, and closes every finalizer when the block exits — on success, on failure, or on interruption:

| cats-effect 3.x                     | ZIO 2.x                                              | Notes                                                       |
| ------------------------------------ | ----------------------------------------------------- | -------------------------------------------------------------- |
| `Resource.make(acq)(rel)`            | `ZIO.acquireRelease(acq)(rel)` — `ZIO[R with R1 with Scope, E, A]` | —                                                    |
| `Resource.makeCase(acq)(rel)`        | `ZIO.acquireReleaseExit(acq)(rel)`                    | `rel: (A, ExitCase) => F[Unit]` becomes `rel: (A, Exit[Any, Any]) => URIO[R1, Any]` — see the `ExitCase` note below |
| `Resource.eval(fa)`                  | *(no wrapper needed)*                                 | An effect with no finalizer composes directly inside a `for`-comprehension in `ZIO.scoped { ... }` |
| `Resource.fromAutoCloseable(fa)`     | `ZIO.fromAutoCloseable(fa)`                           | —                                                            |
| `resource.use(f)`                    | `ZIO.scoped { acquired.flatMap(f) }`                  | —                                                            |
| `resource.evalMap(f)`                | `acquired.flatMap(f)`                                 | Ordinary `flatMap` inside the `for`-comprehension — the finalizer registered by `acquireRelease` still fires on scope close regardless of what's chained afterward |
| `resource.evalTap(f)`                | `acquired.tap(f)`                                     | Same reasoning as `evalMap`                                  |

Stack multiple resources in one `for`-comprehension inside one `ZIO.scoped` block — no nested `.use` calls needed:

**Before (cats-effect)** — two resources mean two nested `.use` calls:

```scala
import cats.effect.{IO, Resource}

case class DbConnection(id: Int) {
  def query(sql: String): IO[String] = IO(s"conn-$id: $sql result")
  def close(): IO[Unit]              = IO(println(s"Closing connection $id"))
}

def makeDbConnection(id: Int): Resource[IO, DbConnection] =
  Resource.make(
    IO(println(s"Opening connection $id")).as(DbConnection(id))
  )(conn => conn.close())

val program: IO[String] =
  makeDbConnection(1).use { conn1 =>
    makeDbConnection(2).use { conn2 =>
      conn1.query("SELECT 1")
    }
  }
```

**After (ZIO)** — one flat `for`-comprehension inside a single `ZIO.scoped`:

```scala
import zio._

case class DbConnection(id: Int) {
  def query(sql: String): Task[String] = ZIO.attempt(s"conn-$id: $sql result")
  def close: UIO[Unit]                 = ZIO.succeed(println(s"Closing connection $id"))
}

def makeDbConnection(id: Int): ZIO[Scope, Nothing, DbConnection] =
  ZIO.acquireRelease(
    ZIO.succeed { println(s"Opening connection $id"); DbConnection(id) }
  )(conn => conn.close)

// Two resources acquired and released inside one ZIO.scoped block
val program: Task[String] =
  ZIO.scoped {
    for {
      conn1  <- makeDbConnection(1)
      conn2  <- makeDbConnection(2)
      result <- conn1.query("SELECT 1")
    } yield result
  }
```

When `ZIO.scoped` exits — on success, failure, or interruption — it runs each finalizer in reverse acquisition order. Both `conn2.close` and `conn1.close` are guaranteed to run.

:::note[ExitCase and Outcome Both Map to Exit]
Cats-effect has two separate "how did this end" types: `Resource.ExitCase` (`Succeeded` / `Errored(e)` / `Canceled`), passed to `Resource.makeCase`'s release function, and `Outcome` (`Succeeded` / `Errored(e)` / `Canceled`), returned by `fiber.join`. ZIO unifies both into a single `Exit[E, A]` type — `Exit.Success(a)` or `Exit.Failure(cause)`, where `cause` distinguishes a typed failure from interruption via `Cause.fail`/`Cause.interrupt`. `ZIO.acquireReleaseExit`'s release function and `fiber.await` both receive this same `Exit` type — there's only one case class hierarchy to learn instead of two.
:::

:::caution[Do Not Use ZManaged]
`ZManaged` exists in the separate `zio-managed` module as a compatibility shim for ZIO 1.x code. Do not use it in migrated code. `ZIO.acquireRelease` combined with `ZIO.scoped` is the ZIO 2.x idiom for resource management.
:::

## Cats-Effect Typeclasses vs. Direct ZIO Usage

Cats-effect code is frequently written polymorphically, constrained by a typeclass (`Sync[F]`, `Async[F]`, `Concurrent[F]`, `Temporal[F]`, `MonadCancel[F, Throwable]`) rather than committing to `IO` directly. ZIO does not use this pattern — `ZIO`/`Task` is a concrete data type with every capability (synchronous effects, async callbacks, concurrency, timeouts, cancellation) built in, so there is no typeclass hierarchy to satisfy. Migrating polymorphic cats-effect code means deleting the type parameter and its constraint, and writing directly against `ZIO`:

| cats-effect 3.x typeclass                | ZIO 2.x replacement                     | Notes                                                                 |
| ----------------------------------------- | ----------------------------------------- | ------------------------------------------------------------------------ |
| `MonadCancel[F, Throwable]`               | *(not needed)*                            | `ZIO`'s cancellation/interruption behavior is built in, not opt-in via a typeclass |
| `Sync[F]`                                 | *(not needed)*                            | Use `ZIO.attempt`/`ZIO.succeed` directly                                 |
| `Async[F]`                                | *(not needed)*                            | Use `ZIO.async`/`ZIO.asyncZIO`/`ZIO.fromFuture` directly                 |
| `Spawn[F]`                                | *(not needed)*                            | Use `.fork`/`.join`/`.interrupt` directly — see [Forking Fibers](#forking-fibers-and-running-effects-in-parallel) |
| `Concurrent[F]` / `GenConcurrent[F, E]`   | *(not needed)*                            | ZIO's `E` is already first-class in the type signature, so the generalized `GenConcurrent[F, E]` variant maps the same way as `Concurrent[F]` |
| `Temporal[F]` / `GenTemporal[F, E]`       | *(not needed)*                            | Use `ZIO.sleep`/`.timeout`/`Schedule` directly — see [Time, Timeouts, and Retries](#time-timeouts-and-retries) |
| `Clock[F]`                                | *(not needed)*                            | Use the built-in `Clock` service — see [Time, Timeouts, and Retries](#time-timeouts-and-retries) |
| `Unique[F]`                               | *(not needed)*                            | Generates unique tokens for identity comparisons; ZIO's `FiberId` or `Random.nextUUID` cover the same use cases without a dedicated typeclass |

A function constrained by `Async[F]` collapses to a concrete `ZIO` signature:

```scala
import zio._
import cats.effect.kernel.Async

// Before: polymorphic over any Async[F], only usable through evidence
def fetchPolymorphic[F[_]: Async](id: Int): F[String] =
  Async[F].delay(s"fetched $id")

// After: concrete ZIO signature, no typeclass constraint
def fetchZIO(id: Int): Task[String] =
  ZIO.attempt(s"fetched $id")
```

:::note[Fiber Tracing]
Cats-effect's fiber tracing configuration (`docs/tracing.md` upstream) has no method-by-method ZIO equivalent to map — the two runtimes implement tracing very differently. ZIO's `Trace` is an implicit, compile-time-captured value threaded through every operator, giving accurate source locations in failure output without any runtime tracing mode to configure. If your cats-effect code tunes tracing behavior explicitly, there's nothing to port — ZIO's tracing is always on and requires no setup.
:::

## Forking Fibers and Running Effects in Parallel

ZIO uses `zio.fork` and `fiber.interrupt` where cats-effect uses `io.start` and `fiber.cancel`. The semantic difference is significant: in ZIO every fiber is interruptible by default, whereas cats-effect requires opt-in cancelability via `Poll`:

| cats-effect 3.x                              | ZIO 2.x                                            | Notes                                            |
| --------------------------------------------- | ---------------------------------------------------- | --------------------------------------------------- |
| `io.start`                                    | `zio.fork`                                           | Returns `URIO[R, Fiber.Runtime[E, A]]`               |
| `fiber.cancel`                                | `fiber.interrupt`                                    | Returns `UIO[Exit[E, A]]`; always interruptible      |
| `fiber.join` → `Outcome[IO, Throwable, A]`    | `fiber.join` → re-raises `E`                         | ZIO join propagates failure directly                 |
| `fiber.join` (materialized) → `Outcome`       | `fiber.await` → `UIO[Exit[E, A]]`                    | `Exit` unifies `Outcome`'s `Succeeded`/`Errored`/`Canceled` — see the note above |
| `IO.race(a, b)` → `Either[A, B]`              | `a.race(b)` → `A`                                    | Winner's value returned directly, not `Either`       |
| `IO.racePair(a, b)`                           | `a.raceWith(b)(leftDone, rightDone)`                 | Both give access to the loser's fiber for cleanup    |
| `(a, b).parMapN(f)`                           | `a.zipWithPar(b)(f)`                                 | —                                                     |
| `List[A].parTraverse(f)`                      | `ZIO.foreachPar(list)(f)`                            | —                                                     |
| `List[IO[A]].parSequence`                     | `ZIO.collectAllPar(list)`                            | —                                                     |
| `IO.uncancelable(poll => body)`               | `ZIO.uninterruptibleMask(restore => body)`           | `poll(io)` becomes `restore(zio)` — marks the wrapped effect interruptible again inside an otherwise uninterruptible region |

Note that `a.race(b)` in ZIO returns `A` directly when both sides produce the same type — not `Either[A, B]` as cats-effect's `IO.race` does. To distinguish which side won, map each side to a tagged type first: `a.map(Left(_)).race(b.map(Right(_)))`.

The following demonstrates each concurrent pattern in a single for-comprehension — the cats-effect version first, then the same program translated to ZIO:

**Before (cats-effect):**

```scala
import cats.effect.IO
import cats.syntax.all._

val step5: IO[Unit] = for {
  // start replaces .fork
  fiber1 <- IO(println("worker-1")).start
  fiber2 <- IO(println("worker-2")).start

  // cancel — only takes effect where the wrapped IO opted in via Poll
  _ <- fiber1.cancel

  // race: returns Either[A, B]
  winner <- IO.race(IO.pure("fast"), IO.pure("slow"))

  // join returns Outcome[IO, Throwable, A]
  outcome2 <- fiber2.join

  // parTraverse replaces foreachPar
  squares <- List(1, 2, 3).parTraverse(n => IO.pure(n * n))

  // parMapN — runs both effects in parallel, returns a tuple
  pair <- (IO.pure(42), IO.pure("hello")).parMapN((a, b) => (a, b))

  // uncancelable — poll(_) re-enables cancelation for the wrapped effect
  _ <- IO.uncancelable(poll => poll(IO(println("critical section"))))
} yield ()
```

**After (ZIO):**

```scala
import zio._

val step5: Task[Unit] = for {
  // fork replaces .start
  fiber1 <- ZIO.attempt(println("worker-1")).fork
  fiber2 <- ZIO.attempt(println("worker-2")).fork

  // interrupt replaces .cancel; always succeeds, returns UIO[Exit[E, A]]
  _ <- fiber1.interrupt

  // race: both sides return String; winner's value returned directly
  winner <- ZIO.succeed("fast").race(ZIO.succeed("slow"))

  // join re-raises the fiber's failure into the current fiber
  _ <- fiber2.join

  // foreachPar replaces parTraverse
  squares <- ZIO.foreachPar(List(1, 2, 3))(n => ZIO.succeed(n * n))

  // <&> is zipPar — runs both effects in parallel and returns a tuple
  pair <- ZIO.succeed(42) <&> ZIO.succeed("hello")

  // uninterruptibleMask replaces IO.uncancelable; restore(_) re-enables interruption for the wrapped effect
  _ <- ZIO.uninterruptibleMask(restore => restore(ZIO.succeed(println("critical section"))))
} yield ()
```

## Shared State and Cross-Fiber Signaling

ZIO provides direct equivalents for both `Ref` and `Deferred`, with an identical method surface for `Ref` and an expanded contract for the promise primitive — plus a direct replacement for `IOLocal`.

### Replacing `Ref`

ZIO's `Ref` has the same operations as cats-effect's `Ref` — the only difference is the constructor:

| cats-effect 3.x                  | ZIO 2.x                          |
| ---------------------------------- | ----------------------------------- |
| `Ref.of[IO](value)`                | `Ref.make(value)`                   |
| `ref.get`                          | `ref.get`                           |
| `ref.set(a)`                       | `ref.set(a)`                        |
| `ref.update(f)`                    | `ref.update(f)`                     |
| `ref.updateAndGet(f)`              | `ref.updateAndGet(f)`               |
| `ref.getAndUpdate(f)`              | `ref.getAndUpdate(f)`               |
| `ref.modify(f: A => (B, A))`       | `ref.modify(f: A => (B, A))`        |

`Ref.make(value)` returns `UIO[Ref[A]]`. The tuple order of `modify` is identical in both libraries: `f: A => (returnValue, newState)`.

### Replacing `Deferred` with `Promise`

ZIO's `Promise[E, A]` adds a typed error channel that `Deferred[IO, A]` lacks:

| cats-effect 3.x              | ZIO 2.x                       | Notes                                            |
| ------------------------------ | -------------------------------- | ---------------------------------------------------- |
| `Deferred[IO, A]`              | `Promise[E, A]`                  | ZIO adds error type `E`                              |
| `Deferred.apply[IO, A]`        | `Promise.make[E, A]`             | Returns `UIO[Promise[E, A]]`                         |
| `deferred.get`                 | `promise.await`                  | Suspends until completed; re-raises `E`              |
| `deferred.complete(a)`         | `promise.succeed(a)`             | Returns `UIO[Boolean]` — `false` if already set      |
| *(no equivalent)*              | `promise.fail(e)`                | Completes with typed failure `E`                     |

The block below shows both `Ref`/`Deferred` and their ZIO replacements — including the typed-failure path on `Promise` that `Deferred` has no equivalent for:

**Before (cats-effect):**

```scala
import cats.effect.{IO, Ref, Deferred}

val step6: IO[Unit] = for {
  // Ref — same API ZIO uses, different constructor
  counter <- Ref.of[IO, Int](0)
  n1      <- counter.updateAndGet(_ + 1)
  n2      <- counter.updateAndGet(_ + 1)

  // Deferred — completed once with a success value only
  done <- Deferred[IO, String]
  _    <- done.complete("all done")
  msg  <- done.get

  // No typed failure channel — Deferred can only carry a success value,
  // so a domain failure has to be smuggled through as data
  errored <- Deferred[IO, Either[String, Int]]
  _       <- errored.complete(Left("something went wrong"))
  result  <- errored.get
} yield ()
```

**After (ZIO):**

```scala
import zio._

val step6: Task[Unit] = for {
  // Ref — same API, different constructor
  counter <- Ref.make(0)
  n1      <- counter.updateAndGet(_ + 1)
  n2      <- counter.updateAndGet(_ + 1)

  // Promise — replaces Deferred, adds typed error channel
  done    <- Promise.make[Nothing, String]
  _       <- done.succeed("all done")
  msg     <- done.await

  // Promise with typed failure — no cats-effect equivalent
  errored <- Promise.make[String, Int]
  _       <- errored.fail("something went wrong")
  result  <- errored.await.either
} yield ()
```

`promise.await` suspends the current fiber until the `Promise` is completed. If the `Promise` was failed with `promise.fail(e)`, every fiber waiting on `promise.await` sees that failure re-raised through the normal ZIO error mechanism — no shared `Ref[Option[Either[E, A]]]` workaround is needed.

### Replacing `IOLocal` with `FiberRef`

Cats-effect's `IOLocal[A]` holds fiber-local mutable state: each fiber sees its own value, child fibers inherit the parent's value at fork time, and changes a child makes are invisible to the parent. ZIO's `FiberRef[A]` provides the same guarantee, with the same `get`/`set`/`update` surface as `Ref`:

| cats-effect 3.x                          | ZIO 2.x                                  | Notes                                                        |
| ------------------------------------------ | ------------------------------------------- | ----------------------------------------------------------------- |
| `IOLocal(initial)`                        | `FiberRef.make(initial)`                    | Returns `ZIO[Scope, Nothing, FiberRef[A]]` — scoped, so create it once inside `ZIO.scoped` or at application startup, not per-use |
| `local.get`                               | `fiberRef.get`                              | —                                                              |
| `local.set(a)`                            | `fiberRef.set(a)`                           | —                                                              |
| `local.update(f)`                         | `fiberRef.update(f)`                        | —                                                              |
| `local.reset`                             | *(no direct equivalent)*                    | Restore the initial value with `fiberRef.set(initial)` explicitly |

**Before (cats-effect):**

```scala
import cats.effect.{IO, IOLocal}

val step6b: IO[Unit] = for {
  requestId <- IOLocal("unset")
  _         <- requestId.set("req-42")
  // A forked child fiber inherits the current value...
  child     <- requestId.get.flatMap(v => IO(println(s"child sees $v"))).start
  _         <- child.join
  // ...but changes the child makes are not visible to the parent
  _         <- requestId.get.flatMap(v => IO(println(s"parent still sees $v")))
} yield ()
```

**After (ZIO):**

```scala
import zio._

val step6b: Task[Unit] =
  ZIO.scoped {
    for {
      requestId <- FiberRef.make("unset")
      _         <- requestId.set("req-42")
      // A forked child fiber inherits the current value...
      child     <- requestId.get.debug("child sees").fork
      _         <- child.join
      // ...but changes the child makes are not visible to the parent
      _         <- requestId.get.debug("parent still sees")
    } yield ()
  }
```

Unlike `IOLocal`, ZIO's `FiberRef.make` is scoped — the `FiberRef` itself is a resource, released when its `Scope` closes. Most applications create their `FiberRef`s once at startup inside the top-level `ZIO.scoped`/`ZIOAppDefault` lifetime rather than per-request.

## Concurrent Data Structures from cats-effect's std Module

`cats.effect.std` bundles a set of concurrency primitives built on top of `IO`. ZIO ships direct equivalents for most of them as part of `zio` core, with a few in the separate `zio-concurrent` module:

| cats-effect 3.x (`cats.effect.std`) | ZIO 2.x                          | Notes                                                                 |
| ------------------------------------- | ----------------------------------- | -------------------------------------------------------------------------- |
| `Queue[F, A]`                         | `zio.Queue[A]`                      | Bounded/unbounded, `offer`/`take`, same core semantics                     |
| `Semaphore[F]`                        | `zio.Semaphore`                     | `withPermit`/`withPermits` acquire-and-release a permit around an effect; there's no standalone `acquire`/`release` pair to call manually |
| `CountDownLatch[F]`                   | `zio.concurrent.CountdownLatch`     | Requires the `zio-concurrent` module; `countDown`/`await`                  |
| `CyclicBarrier[F]`                    | `zio.concurrent.CyclicBarrier`      | Requires the `zio-concurrent` module; resettable, unlike `CountDownLatch`  |
| `Random[F]`                           | `zio.Random`                        | Built-in service, no environment requirement                              |
| `Console[F]`                          | `zio.Console`                       | Built-in service, no environment requirement                              |
| `Mutex[F]`                            | `Semaphore.make(1)`                 | A binary semaphore — `withPermit` gives mutual exclusion                   |
| `AtomicCell[F, A]`                    | `Ref.Synchronized.make(a)`          | Guarantees effectful updates run to completion without interleaving, unlike plain `Ref` |
| `Supervisor[F]`                       | `zio.Supervisor`                    | Conceptually different: ZIO's `Supervisor` observes fiber lifecycle events rather than providing CE's structured-scope supervision; for "fork children, clean them all up together," use `ZIO#fork` inside a `Scope` instead |
| `Dispatcher[F]`                       | *(not needed)*                      | Dispatcher exists to run `IO` from non-cats-effect callback code; ZIO's `Runtime`/`Unsafe` API covers the same FFI use case directly, without a separate resource to acquire |
| `Hotswap[F]`                          | *(no direct equivalent)*            | Model dynamic resource-swapping with nested `Scope`s: close the old scope and open a new one when swapping                |

**Before (cats-effect):**

```scala
import cats.effect.IO
import cats.effect.std.{AtomicCell, Queue, Semaphore}

val step7: IO[Unit] = for {
  // Queue replaces cats.effect.std.Queue
  queue <- Queue.bounded[IO, Int](10)
  _     <- queue.offer(1)
  n     <- queue.take

  // Semaphore — permit is a Resource, used via .use
  sem <- Semaphore[IO](1)
  _   <- sem.permit.use(_ => IO(println(s"exclusive access, got $n")))

  // AtomicCell — replaced by Ref.Synchronized; effectful updates never interleave
  cell <- AtomicCell[IO].of(0)
  _    <- cell.update(v => v + 1)
} yield ()
```

**After (ZIO):**

```scala
import zio._

val step7: Task[Unit] = for {
  // Queue replaces cats.effect.std.Queue
  queue <- Queue.bounded[Int](10)
  _     <- queue.offer(1)
  n     <- queue.take

  // Semaphore replaces cats.effect.std.Semaphore
  sem   <- Semaphore.make(1)
  _     <- sem.withPermit(ZIO.succeed(println(s"exclusive access, got $n")))

  // Ref.Synchronized replaces AtomicCell — effectful updates never interleave
  cell  <- Ref.Synchronized.make(0)
  _     <- cell.updateZIO(v => ZIO.succeed(v + 1))
} yield ()
```

### Additional `cats.effect.std` Utilities

The rest of `cats.effect.std` maps as follows. These are less commonly hit during migration, so this is a mapping-only reference table rather than a worked example for each:

| cats-effect 3.x                | ZIO 2.x                              | Notes                                                                    |
| --------------------------------- | ---------------------------------------- | ------------------------------------------------------------------------ |
| `QueueSource[F, A]`               | `zio.Dequeue[A]`                         | The read-only half of `Queue` in both libraries — cats-effect's take-only interface |
| `Dequeue[F, A]`                   | *(no direct equivalent)*                 | Cats-effect's `Dequeue` is a double-ended queue (`offerFront`/`offerBack`/`takeFront`/`takeBack`) that extends the full read-write `Queue` — not read-only, despite the name. `zio.Queue` isn't double-ended; model the same use case with two `zio.Queue`s or a `Ref[Chunk[A]]` |
| `PQueue[F, A]`                    | `zio.stm.TPriorityQueue[A]`              | Runs inside `STM`; commit with `.commit` to get back a `UIO[A]`          |
| `MapRef[F, K, V]` / `AtomicMap[F, K, V]` | `Ref[Map[K, V]]` or `zio.stm.TMap[K, V]` | `Ref[Map[K, V]]` for simple cases; `TMap` when you need per-key STM transactions |
| `Backpressure[F]`                 | *(not needed)*                           | A bounded `zio.Queue` already blocks producers when full — no separate wrapper required |
| `Env[F]`                          | `zio.System`                             | `Env[F].get("VAR")` becomes `System.env("VAR")`, both return `IO[_, Option[String]]` |
| `KeyedMutex[F, K]`                | *(model manually)*                       | No dedicated type; combine `Ref[Map[K, Semaphore]]` (one semaphore per key, created on demand) |

## Time, Timeouts, and Retries

cats-effect's `Temporal[F]` typeclass bundles sleeping, timeouts, and clock access; ZIO builds the same capabilities into `ZIO`/`Clock` directly, and moves retry policies into a dedicated `Schedule` data type rather than a separate `cats-retry` library:

| cats-effect 3.x                          | ZIO 2.x                           | Notes                                                              |
| ------------------------------------------ | ------------------------------------ | ---------------------------------------------------------------------- |
| `IO.sleep(duration)`                       | `ZIO.sleep(duration)`               | —                                                                   |
| `Temporal[F].timeout(io, duration)`        | `zio.timeout(duration)`             | Returns `ZIO[R, E, Option[A]]` — `None` on timeout                 |
| `io.timeoutTo(duration, fallback)`         | `zio.timeoutFail(e)(duration)` / `zio.timeout(duration).someOrElse(fallback)` | Choose `timeoutFail` to fail typed, or fall back to a default value |
| cats-retry `retryingOnAllErrors(policy)`   | `zio.retry(schedule)`               | `Schedule` replaces the separate `cats-retry` library entirely     |
| cats-retry `RetryPolicies.exponentialBackoff` | `Schedule.exponential(base)`     | —                                                                   |
| cats-retry `RetryPolicies.limitRetries(n)` | `Schedule.recurs(n)`                | —                                                                   |
| cats-retry policy combination (`policy1.join(policy2)`) | `schedule1 && schedule2`  | `Schedule` composes with ordinary combinators (`&&`, `||`, `andThen`) instead of a separate policy-combination API |

**Before (cats-effect)** — `sleep`/`timeout` compile against cats-effect directly; the retry side isn't shown compiled here since `cats-retry` is a separate library from cats-effect itself, not part of the core dependency this guide compiles against:

```scala
import cats.effect.IO

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS, SECONDS}

val step8: IO[Unit] = for {
  // sleep
  _ <- IO.sleep(FiniteDuration(100, MILLISECONDS))

  // timeout — raises a TimeoutException on timeout; ZIO's variant below returns an Option instead
  timed <- IO("slow computation").timeout(FiniteDuration(1, SECONDS))
  _     <- IO(println(s"timeout result: $timed"))
} yield ()
```

**After (ZIO):**

```scala
import zio._

val flaky: Task[String] = ZIO.attempt(if (scala.util.Random.nextBoolean()) "ok" else throw new RuntimeException("boom"))

val step8: Task[Unit] = for {
  // sleep replaces IO.sleep
  _        <- ZIO.sleep(100.millis)

  // timeout replaces Temporal#timeout; returns Option
  maybe    <- ZIO.succeed("slow computation").timeout(1.second)

  // retry with exponential backoff, capped at 5 attempts — replaces cats-retry
  retried  <- flaky.retry(Schedule.exponential(100.millis) && Schedule.recurs(5))
  _        <- ZIO.succeed(println(s"timeout result: $maybe, retried: $retried"))
} yield ()
```

See [Schedule](../../reference/schedule/index.md) for the full set of built-in schedules and composition operators.

## Runtime Configuration and Thread Model

Cats-effect's `IORuntime` — its thread pools, blocking-detection tuning, and startup configuration — maps conceptually to ZIO's `Runtime` layer customization. Rather than duplicate that material here, see the [ZIO 1.x → 2.x Migration Guide's Runtime section](migration-guide.md#runtime-platform-and-executor), which documents `Runtime.setExecutor`, `Runtime.addLogger`, and the rest of the layer-based runtime customization API in full — the same API applies whether you're migrating from ZIO 1.x or from cats-effect.

One thing that does *not* carry over automatically: cats-effect's `IOApp` runtime periodically checks for compute-pool starvation and logs a warning when a fiber blocks a worker thread too long — but it only warns, it does not reschedule the blocking work. ZIO has no equivalent starvation checker at all. In both runtimes, an accidental `ZIO.attempt(Thread.sleep(...))` (or `IO(Thread.sleep(...))`) occupies a worker thread for the full duration with no automatic compensation — use `ZIO.attemptBlocking` explicitly for JDBC calls, file I/O, or anything else that blocks a thread, the same discipline `IO.blocking` requires in cats-effect.

## Testing

Both ecosystems separate "the effect system" from "the test framework," so migrating tests means swapping the test runner along with the effect type:

| cats-effect 3.x                          | ZIO 2.x                                  | Notes                                                        |
| ------------------------------------------ | ------------------------------------------- | ------------------------------------------------------------------ |
| munit-cats-effect / weaver-cats-effect     | `zio-test`                                  | `ZIOSpecDefault` replaces the `CatsEffectSuite`/`IOSuite` base class |
| `TestControl` (cats-effect-testkit)        | `TestClock`                                 | Simulated, controllable time for testing timeouts/schedules without real delays |

A `ZIOSpecDefault` test looks like an ordinary ZIO program — `test("name") { assertion }` where the body is a `ZIO` effect, composed the same way application code is. See [ZIO Test](../../reference/test/index.md) and [TestClock](../../reference/test/services/clock.md) for the full API.

## Streaming: fs2 to ZStream

If the codebase also uses fs2 (`Stream[IO, A]`), the direct equivalent is `ZStream[R, E, A]` — a pull-based, backpressured stream sharing the same `for`-comprehension ergonomics as `ZIO`. A full fs2-to-ZStream migration is out of scope for this guide (streaming has its own vocabulary of pipes, sinks, and chunking strategies worth a dedicated treatment); for an incremental migration, `zio-interop-cats` and `zio-interop-reactivestreams` let fs2 and `ZStream` pipelines interoperate through the shared `Stream`/`Publisher` boundary while the rest of the migration proceeds. See [ZStream](../../reference/stream/zstream/index.md) for ZIO's streaming reference.

## Putting It Together

The complete example below combines the core replacement patterns from this guide into one runnable program, demonstrating how `ZIOAppDefault`, `ZIO.acquireRelease`, `ZIO.scoped`, `Ref`, `Promise`, and fiber operations compose together:

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/CompleteExample.scala"
package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Complete example combining all six migration steps:
 *   1. ZIOAppDefault entry point
 *   2. ZIO.attempt / ZIO.succeed effect constructors
 *   3. Typed error channel with mapError / catchAll
 *   4. ZIO.acquireRelease + ZIO.scoped resource management
 *   5. fork / interrupt / race / foreachPar / <&> concurrency
 *   6. Ref / Promise shared state and cross-fiber signaling
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.CompleteExample"
 */

// ── Domain types ──────────────────────────────────────────────────
case class CompleteDbConnection(id: Int) {
  def query(sql: String): Task[String] = ZIO.attempt(s"conn-$id: $sql result")
  def close: UIO[Unit]                 = ZIO.succeed(println(s"[cleanup] Closing connection $id"))
}

sealed trait CompleteAppError extends Throwable
case class CompleteDbError(msg: String)      extends CompleteAppError
case class CompleteTimeoutError(msg: String) extends CompleteAppError

object CompleteExample extends ZIOAppDefault {

  // ── Resource ─────────────────────────────────────────────────────
  def makeDbConnection(id: Int): ZIO[Scope, Nothing, CompleteDbConnection] =
    ZIO.acquireRelease(
      ZIO.succeed { println(s"[acquire] Opening connection $id"); CompleteDbConnection(id) }
    )(conn => conn.close)

  // ── Worker: resource + typed errors + Ref + Promise ──────────────
  def worker(
    id:      Int,
    counter: Ref[Int],
    done:    Promise[Nothing, String]
  ): Task[Unit] =
    ZIO.scoped {
      for {
        conn   <- makeDbConnection(id)
        result <- conn
                    .query("SELECT 1")
                    .mapError(e => CompleteDbError(e.getMessage))
        n      <- counter.updateAndGet(_ + 1)
        _      <- ZIO.succeed(println(s"[worker-$id] got: $result, total: $n"))
        _      <- ZIO.when(n >= 2)(done.succeed(s"worker-$id finished last").unit)
      } yield ()
    }

  // ── Application ───────────────────────────────────────────────────
  def run: Task[Unit] =
    for {
      counter <- Ref.make(0)
      done    <- Promise.make[Nothing, String]

      // fork replaces .start
      fiber1 <- worker(1, counter, done).fork
      fiber2 <- worker(2, counter, done).fork

      // race done.await against a 5-second timeout
      winner <- done.await.race(ZIO.sleep(5.seconds).as("timeout"))
      _      <- ZIO.succeed(println(s"[race] winner: $winner"))

      // join re-raises any fiber failures
      _ <- fiber1.join
      _ <- fiber2.join

      // foreachPar replaces parTraverse
      squares <- ZIO.foreachPar(List(1, 2, 3))(n => ZIO.succeed(n * n))
      _       <- ZIO.succeed(println(s"[parallel] squares: $squares"))

      // <&> is zipPar
      pair <- ZIO.succeed(42) <&> ZIO.succeed("hello")
      _    <- ZIO.succeed(println(s"[zipPar] pair: $pair"))
    } yield ()
}
```

## Running the Examples

Clone the repository and change into the examples module. Every step below ships as two full, independently runnable programs — the cats-effect original under `migratecatseffect.catseffect.*` and its ZIO migration under `migratecatseffect.*` — so you can run both, compare output, and diff the source side by side instead of taking the guide's word for it:

```bash
git clone https://github.com/zio/zio.git
cd zio/zio-examples
```

<details>
<summary>Step 1 — Entry Point</summary>

**Before (cats-effect):**

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/catseffect/Step1EntryPoint.scala" showLineNumbers
package migratecatseffect.catseffect

import cats.effect.{IO, IOApp}

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Replacing the Application Entry Point
 *
 * The "before" side of migratecatseffect.Step1EntryPoint.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step1EntryPoint"
 */
object Step1EntryPoint extends IOApp.Simple {
  def run: IO[Unit] =
    IO(println("Application started under cats-effect runtime"))
}
```

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step1EntryPoint"
```

**After (ZIO):**

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/Step1EntryPoint.scala" showLineNumbers
package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Replacing the Application Entry Point
 *
 * Replaces: IOApp.Simple { def run: IO[Unit] }
 * With:     ZIOAppDefault { def run: Task[Unit] }
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step1EntryPoint"
 */
object Step1EntryPoint extends ZIOAppDefault {
  def run: Task[Unit] =
    ZIO.succeed(println("Application started under ZIO runtime"))
}
```

Run the entry-point example to confirm `ZIOAppDefault` starts and exits cleanly:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.Step1EntryPoint"
```

</details>

<details>
<summary>Step 2 — Effect Constructors</summary>

**Before (cats-effect):**

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/catseffect/Step2EffectTypes.scala" showLineNumbers
package migratecatseffect.catseffect

import cats.effect.{IO, IOApp}

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Translating Effect Constructors
 *
 * The "before" side of migratecatseffect.Step2EffectTypes.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step2EffectTypes"
 */
object Step2EffectTypes extends IOApp.Simple {

  val fetched: IO[String]   = IO("result from database")
  val constant: IO[Int]     = IO.pure(42)
  val unit: IO[Unit]        = IO.unit
  val raiseErr: IO[Nothing] = IO.raiseError(new RuntimeException("intentional failure"))

  val program: IO[String] = for {
    a <- IO("hello")
    b <- IO.pure(" world")
  } yield a + b

  def run: IO[Unit] =
    for {
      result  <- program
      _       <- IO(println(s"program: $result"))
      value   <- constant
      _       <- IO(println(s"constant: $value"))
      handled <- raiseErr.handleErrorWith(e => IO(s"caught: ${e.getMessage}"))
      _       <- IO(println(s"raiseErr recovered: $handled"))
    } yield ()
}
```

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step2EffectTypes"
```

**After (ZIO):**

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/Step2EffectTypes.scala" showLineNumbers
package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Translating Effect Constructors
 *
 * Replaces:
 *   IO(body)      -> ZIO.attempt(body)
 *   IO.pure(a)    -> ZIO.succeed(a)
 *   IO.unit       -> ZIO.unit
 *   IO.never      -> ZIO.never
 *   IO.raiseError -> ZIO.fail
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step2EffectTypes"
 */
object Step2EffectTypes extends ZIOAppDefault {

  val fetched: Task[String]   = ZIO.attempt("result from database")
  val constant: UIO[Int]      = ZIO.succeed(42)
  val unit: UIO[Unit]         = ZIO.unit
  val raiseErr: Task[Nothing] = ZIO.fail(new RuntimeException("intentional failure"))

  val program: Task[String] = for {
    a <- ZIO.attempt("hello")
    b <- ZIO.succeed(" world")
  } yield a + b

  def run: Task[Unit] =
    for {
      result  <- program
      _       <- ZIO.succeed(println(s"program: $result"))
      value   <- constant
      _       <- ZIO.succeed(println(s"constant: $value"))
      handled <- raiseErr.catchAll(e => ZIO.succeed(s"caught: ${e.getMessage}"))
      _       <- ZIO.succeed(println(s"raiseErr recovered: $handled"))
    } yield ()
}
```

Run the effect-constructor example to see `ZIO.attempt`, `ZIO.succeed`, and `ZIO.fail` in action:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.Step2EffectTypes"
```

</details>

<details>
<summary>Step 3 — Error Channel</summary>

**Before (cats-effect):**

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/catseffect/Step3ErrorHandling.scala" showLineNumbers
package migratecatseffect.catseffect

import cats.effect.{IO, IOApp}

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Typing Your Error Channel
 *
 * The "before" side of migratecatseffect.Step3ErrorHandling.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step3ErrorHandling"
 */
object Step3ErrorHandling extends IOApp.Simple {

  sealed abstract class AppError(msg: String) extends RuntimeException(msg)
  case class DbError(msg: String)      extends AppError(msg)
  case class TimeoutError(msg: String) extends AppError(msg)

  val failedQuery: IO[String] =
    IO.raiseError(DbError("connection refused"))

  val recovered: IO[String] =
    failedQuery.handleErrorWith(e => IO(s"recovered: ${e.getMessage}"))

  val rawQuery: IO[String] =
    IO(throw new RuntimeException("timeout"))

  val typed: IO[String] =
    rawQuery.adaptError {
      case e: RuntimeException => TimeoutError(e.getMessage)
      case other                => DbError(other.getMessage)
    }

  val inspected: IO[Either[Throwable, String]] = typed.attempt

  def run: IO[Unit] =
    for {
      r1 <- recovered
      _  <- IO(println(r1))
      r2 <- inspected
      _  <- IO(println(r2))
    } yield ()
}
```

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step3ErrorHandling"
```

**After (ZIO):**

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/Step3ErrorHandling.scala" showLineNumbers
package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Typing Your Error Channel
 *
 * Replaces:
 *   IO.raiseError(e)        -> ZIO.fail(e)
 *   io.handleErrorWith(f)   -> zio.catchAll(f)
 *   io.recover { case ... } -> zio.catchSome { case ... }
 *   io.attempt              -> zio.either
 *   (no equivalent)         -> zio.mapError(f)
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step3ErrorHandling"
 */
object Step3ErrorHandling extends ZIOAppDefault {

  sealed trait AppError extends Throwable
  case class DbError(msg: String)      extends AppError
  case class TimeoutError(msg: String) extends AppError

  val failedQuery: IO[DbError, String] =
    ZIO.fail(DbError("connection refused"))

  val recovered: UIO[String] =
    failedQuery.catchAll(e => ZIO.succeed(s"recovered: ${e.msg}"))

  val rawQuery: Task[String] =
    ZIO.attempt(throw new RuntimeException("timeout"))

  val typed: IO[AppError, String] =
    rawQuery.mapError {
      case e: RuntimeException => TimeoutError(e.getMessage)
      case other               => DbError(other.getMessage)
    }

  val inspected: UIO[Either[AppError, String]] = typed.either

  def run: Task[Unit] =
    for {
      r1 <- recovered
      _  <- ZIO.succeed(println(r1))
      r2 <- inspected
      _  <- ZIO.succeed(println(r2))
    } yield ()
}
```

Run the error-channel example to observe `catchAll`, `mapError`, and `either`:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.Step3ErrorHandling"
```

</details>

<details>
<summary>Step 4 — Resource Lifecycles</summary>

**Before (cats-effect)** — note the nested `.use` calls:

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/catseffect/Step4Resources.scala" showLineNumbers
package migratecatseffect.catseffect

import cats.effect.{IO, IOApp, Resource}

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Managing Resource Lifecycles
 *
 * The "before" side of migratecatseffect.Step4Resources — note the nested
 * .use calls, which the ZIO version flattens into one for-comprehension
 * inside a single ZIO.scoped block.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step4Resources"
 */
object Step4Resources extends IOApp.Simple {

  case class DbConnection(id: Int) {
    def query(sql: String): IO[String] = IO(s"conn-$id: $sql result")
    def close(): IO[Unit]              = IO(println(s"Closing connection $id"))
  }

  def makeDbConnection(id: Int): Resource[IO, DbConnection] =
    Resource.make(
      IO(println(s"Opening connection $id")).as(DbConnection(id))
    )(conn => conn.close())

  def run: IO[Unit] =
    makeDbConnection(1).use { conn1 =>
      makeDbConnection(2).use { conn2 =>
        conn1.query("SELECT 1").flatMap(result => IO(println(s"Query result: $result")))
      }
    }
}
```

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step4Resources"
```

**After (ZIO):**

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/Step4Resources.scala" showLineNumbers
package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Managing Resource Lifecycles
 *
 * Replaces:
 *   Resource.make(acq)(rel)       -> ZIO.acquireRelease(acq)(rel)
 *   resource.use(f)               -> ZIO.scoped { acquired.flatMap(f) }
 *   Resource.fromAutoCloseable    -> ZIO.fromAutoCloseable
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step4Resources"
 */
object Step4Resources extends ZIOAppDefault {

  case class DbConnection(id: Int) {
    def query(sql: String): Task[String] = ZIO.attempt(s"conn-$id: $sql result")
    def close: UIO[Unit]                 = ZIO.succeed(println(s"Closing connection $id"))
  }

  def makeDbConnection(id: Int): ZIO[Scope, Nothing, DbConnection] =
    ZIO.acquireRelease(
      ZIO.succeed { println(s"Opening connection $id"); DbConnection(id) }
    )(conn => conn.close)

  def run: Task[Unit] =
    ZIO.scoped {
      for {
        conn1  <- makeDbConnection(1)
        conn2  <- makeDbConnection(2)
        result <- conn1.query("SELECT 1")
        _      <- ZIO.succeed(println(s"Query result: $result"))
      } yield ()
    }
}
```

Run the resource example to see finalizers print in reverse acquisition order:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.Step4Resources"
```

</details>

<details>
<summary>Step 5 — Fiber Concurrency</summary>

**Before (cats-effect):**

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/catseffect/Step5Concurrency.scala" showLineNumbers
package migratecatseffect.catseffect

import cats.effect.{IO, IOApp}
import cats.syntax.all._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Forking Fibers and Running Effects in Parallel
 *
 * The "before" side of migratecatseffect.Step5Concurrency.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step5Concurrency"
 */
object Step5Concurrency extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      // start replaces .fork
      fiber1 <- IO(println("worker-1")).start
      fiber2 <- IO(println("worker-2")).start

      // cancel — only takes effect where the wrapped IO opted in via Poll
      _ <- fiber1.cancel

      // race: returns Either[A, B]
      winner <- IO.race(IO.pure("fast"), IO.pure("slow"))
      _      <- IO(println(s"Race winner: $winner"))

      // join returns Outcome[IO, Throwable, A]
      _ <- fiber2.join

      // parTraverse replaces foreachPar
      squares <- List(1, 2, 3).parTraverse(n => IO.pure(n * n))
      _       <- IO(println(s"Squares: $squares"))

      // parMapN — runs both effects in parallel, returns a tuple
      pair <- (IO.pure(42), IO.pure("hello")).parMapN((a, b) => (a, b))
      _    <- IO(println(s"parMapN: $pair"))
    } yield ()
}
```

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step5Concurrency"
```

**After (ZIO):**

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/Step5Concurrency.scala" showLineNumbers
package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Forking Fibers and Running Effects in Parallel
 *
 * Replaces:
 *   io.start              -> zio.fork
 *   fiber.cancel          -> fiber.interrupt
 *   IO.race(a, b)         -> a.race(b)
 *   (a, b).parMapN(f)     -> a.zipWithPar(b)(f)
 *   List.parTraverse(f)   -> ZIO.foreachPar(list)(f)
 *   List.parSequence      -> ZIO.collectAllPar(list)
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step5Concurrency"
 */
object Step5Concurrency extends ZIOAppDefault {

  def run: Task[Unit] =
    for {
      // fork replaces .start; always succeeds
      fiber1 <- ZIO.succeed(println("worker-1")).fork
      fiber2 <- ZIO.succeed(println("worker-2")).fork

      // interrupt replaces .cancel; always succeeds, returns UIO[Exit[E, A]]
      _ <- fiber1.interrupt

      // race: winner's value returned directly (not Either)
      winner <- ZIO.succeed("fast").race(ZIO.succeed("slow"))
      _      <- ZIO.succeed(println(s"Race winner: $winner"))

      // join re-raises failures from the fiber
      _ <- fiber2.join

      // foreachPar replaces parTraverse
      squares <- ZIO.foreachPar(List(1, 2, 3))(n => ZIO.succeed(n * n))
      _       <- ZIO.succeed(println(s"Squares: $squares"))

      // <&> is zipPar — runs both in parallel, returns a tuple
      pair <- ZIO.succeed(42) <&> ZIO.succeed("hello")
      _    <- ZIO.succeed(println(s"zipPar: $pair"))
    } yield ()
}
```

Run the concurrency example to observe `fork`, `interrupt`, `race`, and `foreachPar`:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.Step5Concurrency"
```

</details>

<details>
<summary>Step 6 — Shared State</summary>

**Before (cats-effect)** — includes `IOLocal`:

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/catseffect/Step6SharedState.scala" showLineNumbers
package migratecatseffect.catseffect

import cats.effect.{Deferred, IO, IOApp, IOLocal, Ref}

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Shared State and Cross-Fiber Signaling
 *
 * The "before" side of migratecatseffect.Step6SharedState, including the
 * IOLocal -> FiberRef subsection.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step6SharedState"
 */
object Step6SharedState extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      // Ref — same API ZIO uses, different constructor
      counter <- Ref.of[IO, Int](0)
      n1      <- counter.updateAndGet(_ + 1)
      n2      <- counter.updateAndGet(_ + 1)
      total   <- counter.get
      _       <- IO(println(s"Counter after 2 updates: $total (n1=$n1, n2=$n2)"))

      // Deferred — completed once with a success value only
      done <- Deferred[IO, String]
      _    <- done.complete("all done")
      msg  <- done.get
      _    <- IO(println(s"Deferred resolved: $msg"))

      // No typed failure channel — Deferred can only carry a success value,
      // so a domain failure has to be smuggled through as data
      errored <- Deferred[IO, Either[String, Int]]
      _       <- errored.complete(Left("something went wrong"))
      result  <- errored.get
      _       <- IO(println(s"Deferred (smuggled failure): $result"))

      // IOLocal — fiber-local state, inherited by children at fork time
      requestId <- IOLocal("unset")
      _         <- requestId.set("req-42")
      child     <- requestId.get.flatMap(v => IO(println(s"child sees $v"))).start
      _         <- child.join
      _         <- requestId.get.flatMap(v => IO(println(s"parent still sees $v")))
    } yield ()
}
```

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step6SharedState"
```

**After (ZIO):**

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/Step6SharedState.scala" showLineNumbers
package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Shared State and Cross-Fiber Signaling
 *
 * Replaces:
 *   Ref.of[IO](value)   -> Ref.make(value)
 *   Deferred[IO, A]     -> Promise[E, A]
 *   deferred.get        -> promise.await
 *   deferred.complete   -> promise.succeed
 *   (no equivalent)     -> promise.fail
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step6SharedState"
 */
object Step6SharedState extends ZIOAppDefault {

  def run: Task[Unit] =
    for {
      // Ref — same API, different constructor
      counter <- Ref.make(0)
      n1      <- counter.updateAndGet(_ + 1)
      n2      <- counter.updateAndGet(_ + 1)
      total   <- counter.get
      _       <- ZIO.succeed(println(s"Counter after 2 updates: $total (n1=$n1, n2=$n2)"))

      // Promise — replaces Deferred, adds typed error channel
      done <- Promise.make[Nothing, String]
      _    <- done.succeed("all done")
      msg  <- done.await
      _    <- ZIO.succeed(println(s"Promise resolved: $msg"))

      // Promise with typed failure — no cats-effect equivalent
      errored <- Promise.make[String, Int]
      _       <- errored.fail("something went wrong")
      result  <- errored.await.either
      _       <- ZIO.succeed(println(s"Promise failed: $result"))
    } yield ()
}
```

Run the shared-state example to see `Ref` updates and `Promise` completion, including the typed-failure path:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.Step6SharedState"
```

</details>

<details>
<summary>Step 7 — Concurrent Data Structures</summary>

**Before (cats-effect)** — note `CountDownLatch#release`, not `countDown`:

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/catseffect/Step7ConcurrentDataStructures.scala" showLineNumbers
package migratecatseffect.catseffect

import cats.effect.{IO, IOApp}
import cats.effect.std.{AtomicCell, CountDownLatch, Queue, Semaphore}

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Concurrent Data Structures from cats-effect's std Module
 *
 * The "before" side of migratecatseffect.Step7ConcurrentDataStructures.
 * Note cats-effect's CountDownLatch uses `release`, not `countDown`.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step7ConcurrentDataStructures"
 */
object Step7ConcurrentDataStructures extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      // Queue
      queue <- Queue.bounded[IO, Int](10)
      _     <- queue.offer(1)
      _     <- queue.offer(2)
      n     <- queue.take
      _     <- IO(println(s"Queue: took $n"))

      // Semaphore — permit is a Resource, used via .use
      sem <- Semaphore[IO](1)
      _   <- sem.permit.use(_ => IO(println("Semaphore: exclusive access granted")))

      // AtomicCell — effectful updates never interleave
      cell  <- AtomicCell[IO].of(0)
      _     <- cell.update(v => v + 1)
      cellV <- cell.get
      _     <- IO(println(s"AtomicCell: $cellV"))

      // CountDownLatch — release replaces zio.concurrent.CountdownLatch#countDown
      latch <- CountDownLatch[IO](2)
      w1    <- (IO(println("worker-1 finishing")) *> latch.release).start
      w2    <- (IO(println("worker-2 finishing")) *> latch.release).start
      _     <- w1.join *> w2.join
      _     <- latch.await
      _     <- IO(println("CountDownLatch: all workers finished"))
    } yield ()
}
```

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step7ConcurrentDataStructures"
```

**After (ZIO):**

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/Step7ConcurrentDataStructures.scala" showLineNumbers
package migratecatseffect

import zio._
import zio.concurrent.CountdownLatch

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Concurrent Data Structures from cats-effect's std Module
 *
 * Replaces:
 *   Queue[F, A]           -> zio.Queue[A]
 *   Semaphore[F]          -> zio.Semaphore
 *   CountDownLatch[F]     -> zio.concurrent.CountdownLatch
 *   AtomicCell[F, A]      -> Ref.Synchronized
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step7ConcurrentDataStructures"
 */
object Step7ConcurrentDataStructures extends ZIOAppDefault {

  def run: Task[Unit] =
    for {
      // Queue — replaces cats.effect.std.Queue
      queue <- Queue.bounded[Int](10)
      _     <- queue.offer(1)
      _     <- queue.offer(2)
      n     <- queue.take
      _     <- ZIO.succeed(println(s"Queue: took $n"))

      // Semaphore — replaces cats.effect.std.Semaphore
      sem <- Semaphore.make(1)
      _   <- sem.withPermit(ZIO.succeed(println("Semaphore: exclusive access granted")))

      // Ref.Synchronized — replaces cats.effect.std.AtomicCell; effectful updates never interleave
      cell   <- Ref.Synchronized.make(0)
      _      <- cell.updateZIO(v => ZIO.succeed(v + 1))
      cellV  <- cell.get
      _      <- ZIO.succeed(println(s"AtomicCell replacement: $cellV"))

      // CountdownLatch — replaces cats.effect.std.CountDownLatch
      latch <- CountdownLatch.make(2)
      w1    <- (ZIO.succeed(println("worker-1 finishing")) *> latch.countDown).fork
      w2    <- (ZIO.succeed(println("worker-2 finishing")) *> latch.countDown).fork
      _     <- w1.join *> w2.join
      _     <- latch.await
      _     <- ZIO.succeed(println("CountdownLatch: all workers finished"))
    } yield ()
}
```

Run the concurrent-data-structures example to see `Queue`, `Semaphore`, and `CountdownLatch` in action:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.Step7ConcurrentDataStructures"
```

</details>

<details>
<summary>Step 8 — Time and Retries</summary>

**Before (cats-effect)** — sleep/timeout only; `cats-retry` is a separate library not included here:

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/catseffect/Step8TimeAndRetry.scala" showLineNumbers
package migratecatseffect.catseffect

import cats.effect.{IO, IOApp}

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS, SECONDS}

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Time, Timeouts, and Retries
 *
 * The "before" side of migratecatseffect.Step8TimeAndRetry. Only sleep/
 * timeout are shown here — retry policies live in the separate cats-retry
 * library, not cats-effect itself, so there is no compiled retry snippet
 * to mirror ZIO's Schedule-based retry.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step8TimeAndRetry"
 */
object Step8TimeAndRetry extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      // sleep — replaces IO.sleep
      _ <- IO(println("Sleeping for 100 millis..."))
      _ <- IO.sleep(FiniteDuration(100, MILLISECONDS))

      // timeout — raises a TimeoutException on timeout; ZIO's variant returns an Option instead
      timed <- IO("this finishes fast").timeout(FiniteDuration(1, SECONDS))
      _     <- IO(println(s"Timeout result: $timed"))
    } yield ()
}
```

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step8TimeAndRetry"
```

**After (ZIO):**

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/Step8TimeAndRetry.scala" showLineNumbers
package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Time, Timeouts, and Retries
 *
 * Replaces:
 *   IO.sleep(duration)                    -> ZIO.sleep(duration)
 *   Temporal[F].timeout(io, duration)     -> zio.timeout(duration)
 *   cats-retry retryingOnAllErrors(policy) -> zio.retry(schedule)
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step8TimeAndRetry"
 */
object Step8TimeAndRetry extends ZIOAppDefault {

  private var attempts = 0

  private def flaky: Task[String] =
    ZIO.attempt {
      attempts += 1
      if (attempts < 3) throw new RuntimeException(s"attempt $attempts failed")
      else s"succeeded on attempt $attempts"
    }

  def run: Task[Unit] =
    for {
      // sleep — replaces IO.sleep
      _ <- ZIO.succeed(println("Sleeping for 100 millis..."))
      _ <- ZIO.sleep(100.millis)

      // timeout — replaces Temporal#timeout; returns Option, None on timeout
      timedOut <- ZIO.succeed("this finishes fast").timeout(1.second)
      _        <- ZIO.succeed(println(s"Timeout result: $timedOut"))

      // retry with exponential backoff, capped at 5 attempts — replaces cats-retry
      retried <- flaky.retry(Schedule.exponential(10.millis) && Schedule.recurs(5))
      _       <- ZIO.succeed(println(s"Retry result: $retried"))
    } yield ()
}
```

Run the time-and-retry example to see `ZIO.sleep`, `ZIO.timeout`, and a `Schedule`-based retry:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.Step8TimeAndRetry"
```

</details>

<details>
<summary>Complete Example</summary>

**Before (cats-effect)** — the motivating program from [The Problem](#the-problem):

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/catseffect/CompleteExample.scala" showLineNumbers
package migratecatseffect.catseffect

import cats.effect.{IO, IOApp, Resource}
import cats.effect.kernel.{Deferred, Ref}
import cats.syntax.all._

import scala.concurrent.duration._

/**
 * Guide: Migrate from Cats Effect to ZIO
 *
 * The "before" side of migratecatseffect.CompleteExample — the motivating
 * cats-effect program from the guide's "The Problem" section, combining
 * IOApp, Resource, typed-ish errors, Ref, Deferred, and fiber concurrency.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.CompleteExample"
 */

sealed abstract class AppError(msg: String) extends RuntimeException(msg)
case class DbError(msg: String)      extends AppError(msg)
case class TimeoutError(msg: String) extends AppError(msg)

case class DbConnection(id: Int) {
  def query(sql: String): IO[String] = IO(s"conn-$id: $sql result")
  def close(): IO[Unit] = IO(println(s"[cleanup] Closing connection $id"))
}

object CompleteExample extends IOApp.Simple {

  def makeDbConnection(id: Int): Resource[IO, DbConnection] =
    Resource.make(
      IO(println(s"[acquire] Opening connection $id")).as(DbConnection(id))
    )(conn => conn.close())

  def worker(id: Int, counter: Ref[IO, Int], done: Deferred[IO, String]): IO[Unit] =
    makeDbConnection(id).use { conn =>
      for {
        result <- conn.query("SELECT 1")
                    .handleErrorWith(e => IO.raiseError(DbError(e.getMessage)))
        n      <- counter.updateAndGet(_ + 1)
        _      <- IO(println(s"[worker-$id] got: $result, total: $n"))
        _      <- if (n >= 2) done.complete(s"worker-$id finished last").void else IO.unit
      } yield ()
    }

  def run: IO[Unit] =
    for {
      counter <- Ref.of[IO, Int](0)
      done    <- Deferred[IO, String]
      fiber1  <- worker(1, counter, done).start
      fiber2  <- worker(2, counter, done).start
      result  <- IO.race(done.get, IO.sleep(5.seconds).as("timeout"))
      msg     <- result match {
                   case Left(doneMsg)  => IO.pure(doneMsg)
                   case Right(timeout) =>
                     fiber1.cancel *> fiber2.cancel *> IO.raiseError(TimeoutError(timeout))
                 }
      _       <- IO(println(s"[race] Final: $msg"))
      _       <- fiber1.join
      _       <- fiber2.join
      results <- List(1, 2, 3).parTraverse(i => IO(i * i))
      _       <- IO(println(s"[parallel] Squares: $results"))
      pair    <- (IO(42), IO("hello")).parMapN((x, y) => (x, y))
      _       <- IO(println(s"[parMapN] pair: ${pair._1}, ${pair._2}"))
    } yield ()
}
```

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.CompleteExample"
```

**After (ZIO):**

```scala title="zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/CompleteExample.scala" showLineNumbers
package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Complete example combining all six migration steps:
 *   1. ZIOAppDefault entry point
 *   2. ZIO.attempt / ZIO.succeed effect constructors
 *   3. Typed error channel with mapError / catchAll
 *   4. ZIO.acquireRelease + ZIO.scoped resource management
 *   5. fork / interrupt / race / foreachPar / <&> concurrency
 *   6. Ref / Promise shared state and cross-fiber signaling
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.CompleteExample"
 */

// ── Domain types ──────────────────────────────────────────────────
case class CompleteDbConnection(id: Int) {
  def query(sql: String): Task[String] = ZIO.attempt(s"conn-$id: $sql result")
  def close: UIO[Unit]                 = ZIO.succeed(println(s"[cleanup] Closing connection $id"))
}

sealed trait CompleteAppError extends Throwable
case class CompleteDbError(msg: String)      extends CompleteAppError
case class CompleteTimeoutError(msg: String) extends CompleteAppError

object CompleteExample extends ZIOAppDefault {

  // ── Resource ─────────────────────────────────────────────────────
  def makeDbConnection(id: Int): ZIO[Scope, Nothing, CompleteDbConnection] =
    ZIO.acquireRelease(
      ZIO.succeed { println(s"[acquire] Opening connection $id"); CompleteDbConnection(id) }
    )(conn => conn.close)

  // ── Worker: resource + typed errors + Ref + Promise ──────────────
  def worker(
    id:      Int,
    counter: Ref[Int],
    done:    Promise[Nothing, String]
  ): Task[Unit] =
    ZIO.scoped {
      for {
        conn   <- makeDbConnection(id)
        result <- conn
                    .query("SELECT 1")
                    .mapError(e => CompleteDbError(e.getMessage))
        n      <- counter.updateAndGet(_ + 1)
        _      <- ZIO.succeed(println(s"[worker-$id] got: $result, total: $n"))
        _      <- ZIO.when(n >= 2)(done.succeed(s"worker-$id finished last").unit)
      } yield ()
    }

  // ── Application ───────────────────────────────────────────────────
  def run: Task[Unit] =
    for {
      counter <- Ref.make(0)
      done    <- Promise.make[Nothing, String]

      // fork replaces .start
      fiber1 <- worker(1, counter, done).fork
      fiber2 <- worker(2, counter, done).fork

      // race done.await against a 5-second timeout
      winner <- done.await.race(ZIO.sleep(5.seconds).as("timeout"))
      _      <- ZIO.succeed(println(s"[race] winner: $winner"))

      // join re-raises any fiber failures
      _ <- fiber1.join
      _ <- fiber2.join

      // foreachPar replaces parTraverse
      squares <- ZIO.foreachPar(List(1, 2, 3))(n => ZIO.succeed(n * n))
      _       <- ZIO.succeed(println(s"[parallel] squares: $squares"))

      // <&> is zipPar
      pair <- ZIO.succeed(42) <&> ZIO.succeed("hello")
      _    <- ZIO.succeed(println(s"[zipPar] pair: $pair"))
    } yield ()
}
```

Run the full worker pool with all six core patterns integrated:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.CompleteExample"
```

</details>

## Going Further

- [Interoperating with Cats Effect](../interop/with-cats-effect.md) — use `zio-interop-cats` to call cats-effect libraries such as doobie, http4s, and fs2 from ZIO code during an incremental migration.
- [ZIO 1.x → 2.x Migration Guide](migration-guide.md) — if the codebase also contains ZIO 1.x code, this reference lists every renamed method, the Scalafix `Zio2Upgrade` rule that automates the renaming, and the full `Runtime`/`Platform`/`Executor` customization API.
- [Migrate from Monix](from-monix.md) — a parallel migration guide for codebases coming from Monix `Task`.
- [Ref](../../reference/concurrency/ref.md) — full reference for ZIO's concurrent mutable reference, covering `modify`, continuations, and `Ref.Synchronized`.
- [Promise](../../reference/concurrency/promise.md) — full reference for `Promise[E, A]`, ZIO's typed replacement for cats-effect's `Deferred`.
- [Fiber](../../reference/fiber/fiber.md) — detailed coverage of the fiber lifecycle, supervision, interruption semantics, and `FiberRef`.
- [Scope](../../reference/resource/scope.md) — complete reference for `Scope` and `ZIO.acquireRelease`, the ZIO 2.x resource-management idiom that replaces `Resource`.
- [FiberRef](../../reference/state-management/fiberref.md) — the replacement for `IOLocal`, covering fork/join value propagation.
- [Queue](../../reference/concurrency/queue.md) and [Semaphore](../../reference/concurrency/semaphore.md) — full references for the two most common `cats.effect.std` replacements.
- [CountdownLatch](../../reference/sync/countdownlatch.md) and [CyclicBarrier](../../reference/sync/cyclicbarrier.md) — from the `zio-concurrent` module.
- [Schedule](../../reference/schedule/index.md) — the full retry/repetition API that replaces cats-retry.
- [ZIO Test](../../reference/test/index.md) — the test framework that replaces munit-cats-effect/weaver-cats-effect.
