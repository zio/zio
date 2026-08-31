---
id: from-cats-effect
title: "Migrate from Cats Effect to ZIO"
sidebar_label: "Migration from Cats Effect"
description: "Replace cats-effect 3.x IO, Resource, Fiber, Ref, and Deferred with equivalent idiomatic ZIO 2.x"
keywords:
  - "Cats Effect Migration"
  - "Effect Systems"
  - "Resource Management"
  - "Fiber Concurrency"
  - "ZIO Migration"
---

## Introduction

This guide takes a cats-effect 3.x application — one using `IO`, `Resource`, `Fiber`, `Ref`, and `Deferred` — and produces an equivalent ZIO 2.x application with the same runtime behavior, the same domain types, and no cats-effect imports left behind. The approach is six structural replacements made in the order migration actually proceeds: entry point, effect constructors, the error channel, resource lifecycles, fiber concurrency, and shared-state primitives.

## The Problem

Cats-effect's `IO[A]` carries its error type implicitly as `Throwable`. Every function that might fail looks the same in its signature regardless of whether it throws a domain error or a raw exception, so the compiler cannot catch missing handlers or mismatched error types. `Resource[IO, A]` requires a `.use` callback at every call site: stacking two resources means nested `.use` calls, and acquisition and release code ends up interleaved with business logic. Fibers add another gap: `fiber.cancel` only works when the wrapped `IO` opts in to cancelability via `Poll`, making interruption behavior invisible from the outside.

`Deferred[IO, A]` can only be completed with a success value; there is no way to push a typed failure through it, so cross-fiber error signaling falls back to a shared `Ref` holding an `Either` or an `Option`. All of these costs compound as the application grows: adding a new failure mode means grepping for every `.handleErrorWith` rather than following the compiler.

The cats-effect application below shows these patterns together:

```scala mdoc:compile-only
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
      counter <- Ref.of[IO](0)
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

This guide replaces every pattern shown above with its ZIO equivalent.

## Prerequisites

Add the ZIO core library to `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio" % "@VERSION@"
```

All types this guide uses — `ZIO`, `Task`, `UIO`, `Ref`, `Promise`, `Fiber`, `Scope`, `ZIOAppDefault` — come from a single import:

```scala mdoc:silent
import zio._
```

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

```scala mdoc:silent
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

```scala mdoc:compile-only
import zio._

object WorkerPool extends ZIOAppDefault {
  def run: Task[Unit] =
    ZIO.succeed(println("Application started under ZIO runtime"))
}
```

:::caution[ZIO 1.x Names]
`App`, `ZIO.effect`, and `ZIO.effectTotal` were all removed in ZIO 2.x. Always use `ZIOAppDefault`, `ZIO.attempt`, and `ZIO.succeed`. If you are also migrating from ZIO 1.x, the Scalafix rule `Zio2Upgrade` renames them automatically. See the [ZIO 1.x → 2.x Migration Guide](migration-guide.md) for the complete rename table.

`ZIOAppDefault` provides `val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = ZLayer.empty`. If you see `def layer` in older ZIO 2.x preview documentation or blog posts, it was renamed to `bootstrap` before the 2.0 release.
:::

## Translating Effect Constructors

Every cats-effect constructor maps to a ZIO counterpart. The table below covers the patterns from the before example:

| cats-effect 3.x                     | ZIO 2.x               | Notes                            |
| ----------------------------------- | --------------------- | -------------------------------- |
| `IO(body)` / `IO.delay(body)`       | `ZIO.attempt(body)`   | Wraps code that may throw        |
| `IO.pure(a)`                        | `ZIO.succeed(a)`      | Already-computed or non-throwing |
| `IO.unit`                           | `ZIO.unit`            | —                                |
| `IO.never`                          | `ZIO.never`           | —                                |
| `IO.raiseError(e)`                  | `ZIO.fail(e)`         | —                                |

Use `ZIO.attemptBlocking(body)` for JDBC calls, file I/O, or any computation that blocks a thread — it shifts execution to ZIO's dedicated blocking thread pool rather than occupying a fiber worker.

The following shows the core constructors in place, producing ZIO effect values as data:

```scala mdoc:compile-only
import zio._

val fetched:  Task[String]  = ZIO.attempt("result from database")
val constant: UIO[Int]      = ZIO.succeed(42)
val unit:     UIO[Unit]     = ZIO.unit
val never:    UIO[Nothing]  = ZIO.never
val raiseErr: Task[Nothing] = ZIO.fail(new RuntimeException("oops"))

val program: Task[String] = for {
  a <- ZIO.attempt("hello")
  b <- ZIO.succeed(" world")
} yield a + b
```

`ZIO.attempt` returns `Task[A]`, which is the alias `ZIO[Any, Throwable, A]` — the closest equivalent to cats-effect's `IO[A]`. `ZIO.succeed` returns `UIO[A]`, meaning `ZIO[Any, Nothing, A]`, a value that cannot fail. The aliases `Task`, `UIO`, `RIO`, `URIO`, and ZIO's two-parameter `IO[E, A]` are all defined in `zio.package` and available after `import zio._`.

## Typing Your Error Channel

`IO[A]`'s error channel is always `Throwable` and invisible to the compiler. `ZIO[R, E, A]` makes `E` explicit, so the compiler enforces exhaustive handling. The replacement operators are:

| cats-effect 3.x                          | ZIO 2.x                              |
| ---------------------------------------- | ------------------------------------ |
| `IO.raiseError(e)`                       | `ZIO.fail(e)`                        |
| `io.handleErrorWith(f)`                  | `zio.catchAll(f)`                    |
| `io.recover { case e: X => … }`          | `zio.catchSome { case e: X => … }`   |
| `io.attempt`                             | `zio.either`                         |
| *(no equivalent)*                        | `zio.mapError(f)`                    |

`mapError` is the primary tool for lifting an untyped `Task[A]` (error = `Throwable`) into a domain-specific `IO[AppError, A]`. It has no cats-effect equivalent because `IO[A]` cannot represent typed errors at all.

The block below demonstrates each replacement, starting from a `ZIO.fail` call and progressing through recovery, type narrowing, and error materialization:

```scala mdoc:compile-only
import zio._

sealed trait AppError extends Throwable
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

// Replace io.attempt — materialise failure as Either
val inspected: UIO[Either[AppError, String]] = typed.either
```

`catchSome` (replacing `recover { case … }`) takes a `PartialFunction[E, ZIO[…]]` and leaves unmatched errors in the error channel, exactly as `recover` leaves unmatched throwables untouched.

## Managing Resource Lifecycles

`Resource.make(acquire)(release)` maps to `ZIO.acquireRelease(acquire)(release)`, which registers the finalizer with an ambient `Scope`. `ZIO.scoped` creates a `Scope`, runs the block, and closes every finalizer when the block exits — on success, on failure, or on interruption:

| cats-effect 3.x                     | ZIO 2.x                                              |
| ----------------------------------- | ---------------------------------------------------- |
| `Resource.make(acq)(rel)`           | `ZIO.acquireRelease(acq)(rel)` — `ZIO[R with R1 with Scope, E, A]` |
| `resource.use(f)`                   | `ZIO.scoped { acquired.flatMap(f) }`                 |
| `Resource.fromAutoCloseable(fa)`    | `ZIO.fromAutoCloseable(fa)`                          |

Stack multiple resources in one `for`-comprehension inside one `ZIO.scoped` block — no nested `.use` calls needed:

```scala mdoc:compile-only
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

:::caution[Do Not Use ZManaged]
`ZManaged` exists in the separate `zio-managed` module as a compatibility shim for ZIO 1.x code. Do not use it in migrated code. `ZIO.acquireRelease` combined with `ZIO.scoped` is the ZIO 2.x idiom for resource management.
:::

## Forking Fibers and Running Effects in Parallel

ZIO uses `zio.fork` and `fiber.interrupt` where cats-effect uses `io.start` and `fiber.cancel`. The semantic difference is significant: in ZIO every fiber is interruptible by default, whereas cats-effect requires opt-in cancelability via `Poll`:

| cats-effect 3.x                              | ZIO 2.x                                            | Notes                                           |
| -------------------------------------------- | -------------------------------------------------- | ----------------------------------------------- |
| `io.start`                                   | `zio.fork`                                         | Returns `URIO[R, Fiber.Runtime[E, A]]`          |
| `fiber.cancel`                               | `fiber.interrupt`                                  | Returns `UIO[Exit[E, A]]`; always interruptible |
| `fiber.join` → `Outcome[IO, Throwable, A]`   | `fiber.join` → re-raises `E`                       | ZIO join propagates failure directly            |
| `IO.race(a, b)` → `Either[A, B]`             | `a.race(b)` → `A`                                  | Winner's value returned directly, not `Either`  |
| `(a, b).parMapN(f)`                          | `a.zipWithPar(b)(f)`                               | —                                               |
| `List[A].parTraverse(f)`                     | `ZIO.foreachPar(list)(f)`                          | —                                               |
| `List[IO[A]].parSequence`                    | `ZIO.collectAllPar(list)`                          | —                                               |

Note that `a.race(b)` in ZIO returns `A` directly when both sides produce the same type — not `Either[A, B]` as cats-effect's `IO.race` does. To distinguish which side won, map each side to a tagged type first: `a.map(Left(_)).race(b.map(Right(_)))`.

The following demonstrates each concurrent pattern in a single for-comprehension:

```scala mdoc:compile-only
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
} yield ()
```

## Shared State and Cross-Fiber Signaling

ZIO provides direct equivalents for both `Ref` and `Deferred`, with an identical method surface for `Ref` and an expanded contract for the promise primitive.

### Replacing `Ref`

ZIO's `Ref` has the same operations as cats-effect's `Ref` — the only difference is the constructor:

| cats-effect 3.x                  | ZIO 2.x                          |
| -------------------------------- | -------------------------------- |
| `Ref.of[IO](value)`              | `Ref.make(value)`                |
| `ref.get`                        | `ref.get`                        |
| `ref.set(a)`                     | `ref.set(a)`                     |
| `ref.update(f)`                  | `ref.update(f)`                  |
| `ref.updateAndGet(f)`            | `ref.updateAndGet(f)`            |
| `ref.getAndUpdate(f)`            | `ref.getAndUpdate(f)`            |
| `ref.modify(f: A => (B, A))`     | `ref.modify(f: A => (B, A))`     |

`Ref.make(value)` returns `UIO[Ref[A]]`. The tuple order of `modify` is identical in both libraries: `f: A => (returnValue, newState)`.

### Replacing `Deferred` with `Promise`

ZIO's `Promise[E, A]` adds a typed error channel that `Deferred[IO, A]` lacks:

| cats-effect 3.x              | ZIO 2.x                       | Notes                                            |
| ---------------------------- | ----------------------------- | ------------------------------------------------ |
| `Deferred[IO, A]`            | `Promise[E, A]`               | ZIO adds error type `E`                          |
| `Deferred.apply[IO, A]`      | `Promise.make[E, A]`          | Returns `UIO[Promise[E, A]]`                     |
| `deferred.get`               | `promise.await`               | Suspends until completed; re-raises `E`          |
| `deferred.complete(a)`       | `promise.succeed(a)`          | Returns `UIO[Boolean]` — `false` if already set |
| *(no equivalent)*            | `promise.fail(e)`             | Completes with typed failure `E`                 |

The block below shows both `Ref` and `Promise` in use, including the typed-failure path that has no cats-effect equivalent:

```scala mdoc:compile-only
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

## Putting It Together

The complete example below combines all six replacement patterns from this guide into one runnable program, demonstrating how `ZIOAppDefault`, `ZIO.acquireRelease`, `ZIO.scoped`, `Ref`, `Promise`, and fiber operations compose together:

```scala mdoc:embed:zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/CompleteExample.scala
```

## Running the Examples

Clone the repository and change into the examples module:

```bash
git clone https://github.com/zio/zio.git
cd zio/zio-examples
```

<details>
<summary>Step 1 — Entry Point</summary>

```scala mdoc:embed:zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/Step1EntryPoint.scala:show-line-numbers
```

Run the entry-point example to confirm `ZIOAppDefault` starts and exits cleanly:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.Step1EntryPoint"
```

</details>

<details>
<summary>Step 2 — Effect Constructors</summary>

```scala mdoc:embed:zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/Step2EffectTypes.scala:show-line-numbers
```

Run the effect-constructor example to see `ZIO.attempt`, `ZIO.succeed`, and `ZIO.fail` in action:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.Step2EffectTypes"
```

</details>

<details>
<summary>Step 3 — Error Channel</summary>

```scala mdoc:embed:zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/Step3ErrorHandling.scala:show-line-numbers
```

Run the error-channel example to observe `catchAll`, `mapError`, and `either`:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.Step3ErrorHandling"
```

</details>

<details>
<summary>Step 4 — Resource Lifecycles</summary>

```scala mdoc:embed:zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/Step4Resources.scala:show-line-numbers
```

Run the resource example to see finalizers print in reverse acquisition order:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.Step4Resources"
```

</details>

<details>
<summary>Step 5 — Fiber Concurrency</summary>

```scala mdoc:embed:zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/Step5Concurrency.scala:show-line-numbers
```

Run the concurrency example to observe `fork`, `interrupt`, `race`, and `foreachPar`:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.Step5Concurrency"
```

</details>

<details>
<summary>Step 6 — Shared State</summary>

```scala mdoc:embed:zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/Step6SharedState.scala:show-line-numbers
```

Run the shared-state example to see `Ref` updates and `Promise` completion, including the typed-failure path:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.Step6SharedState"
```

</details>

<details>
<summary>Complete Example</summary>

```scala mdoc:embed:zio-examples/migrate-cats-effect/src/main/scala/migratecatseffect/CompleteExample.scala:show-line-numbers
```

Run the full worker pool with all six patterns integrated:

```bash
sbt "migrate-cats-effect/runMain migratecatseffect.CompleteExample"
```

</details>

## Going Further

- [Interoperating with Cats Effect](../interop/with-cats-effect.md) — use `zio-interop-cats` to call cats-effect libraries such as doobie, http4s, and fs2 from ZIO code during an incremental migration.
- [ZIO 1.x → 2.x Migration Guide](migration-guide.md) — if the codebase also contains ZIO 1.x code, this reference lists every renamed method and the Scalafix `Zio2Upgrade` rule that automates the renaming.
- [Migrate from Monix](from-monix.md) — a parallel migration guide for codebases coming from Monix `Task`.
- [Ref](../../reference/concurrency/ref.md) — full reference for ZIO's concurrent mutable reference, covering `modify`, continuations, and `Ref.Synchronized`.
- [Promise](../../reference/concurrency/promise.md) — full reference for `Promise[E, A]`, ZIO's typed replacement for cats-effect's `Deferred`.
- [Fiber](../../reference/fiber/fiber.md) — detailed coverage of the fiber lifecycle, supervision, interruption semantics, and `FiberRef`.
- [Scope](../../reference/resource/scope.md) — complete reference for `Scope` and `ZIO.acquireRelease`, the ZIO 2.x resource-management idiom that replaces `Resource`.
