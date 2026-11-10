# ZIO 2.x Interoperation with Cats 3.x

> ```sbt
> libraryDependencies += "dev.zio" %% "zio-interop-cats" % "23.1.0.15"
> ```

## Installation

```sbt
libraryDependencies += "dev.zio" %% "zio-interop-cats" % "23.1.0.15"
```

## `ZIO` Cats Effect 3 instances

**ZIO** integrates with Typelevel libraries by providing an instance of `Concurrent`, `Temporal` and `Async` for `Task`
as required, for instance, by `fs2`, `doobie` and `http4s`.

For convenience, the ZIO library defines an alias as follows:

```scala
type Task[A] = ZIO[Any, Throwable, A]
```

Therefore, we provide Cats Effect instances based on this specific datatype.

## `Concurrent`

In order to get a `Concurrent[Task]` or `Concurrent[RIO[R, *]]` (note `*` is kind-projector notation) we need to import `zio.interop.catz._`:

```scala
import cats.effect._
import zio._
import zio.interop.catz._

def ceConcurrentForTaskExample = {
  val F: cats.effect.Concurrent[Task] = implicitly
  F.racePair(F.unit, F.unit)
}
```

## `Temporal`

```scala
import cats.effect._
import zio._
import zio.interop.catz._

def ceTemporal = {
  val F: cats.effect.Temporal[Task] = implicitly
  F.sleep(1.second) *> F.unit
}
```

## `Async`

```scala
import cats.effect._
import zio._
import zio.interop.catz._

def ceAsync = {
  val F: cats.effect.Async[Task] = implicitly
  F.racePair(F.unit, F.sleep(1.second) *> F.unit)
}
```

## Recovering from defects with `catz.autocatch`

The default instances capture exceptions thrown outside of `Sync#delay`, for example inside `map` or `flatMap`, as ZIO
defects (`ZIO.die`). Cats Effect methods such as `handleErrorWith`, `attempt` or `recover` cannot recover from defects:

```scala
import cats.effect._
import cats.syntax.all._
import zio._
import zio.interop.catz._

// dies with the exception instead of recovering
val dies: Task[Int] = Async[Task].unit.map(_ => throw new RuntimeException("boom")).handleError(_ => 1)
```

Code written against Cats Effect typeclasses often expects such exceptions to be recoverable, as they are with
`cats.effect.IO`. For such code, `zio.interop.catz.autocatch._` provides alternative `Async`, `Temporal` and `Concurrent`
instances for `RIO[R, *]` that make defects recoverable:

```scala
import cats.effect._
import cats.syntax.all._
import zio._
import zio.interop.catz.autocatch._

// succeeds with 1
val recovers: Task[Int] = Async[Task].unit.map(_ => throw new RuntimeException("boom")).handleError(_ => 1)
```

With these instances:
* `handleErrorWith`, `recoverWith`, `attempt`, `adaptError` and the methods derived from them recover from defects. A
  single defect is passed to the handler as is, multiple defects are passed as a `FiberFailure` holding the whole
  `Cause`. That is the same `Throwable` that `guaranteeCase` and `Fiber#join` report as `Outcome.Errored`.
* A typed failure takes precedence over defects in the same `Cause`, as with the default instances.
* A defect is not recovered if the fiber is being interrupted.
* A `Cause` without defects is recovered from the same way as by the default instances.
* `unlessA`, `raiseUnless` and `fromOption` suspend their by-name arguments, so that exceptions thrown by them are
  recoverable too.

Exceptions thrown while an effect is being constructed, before it is passed to a typeclass method, for example by the
argument of `pure`, cannot be captured by any instance.

These instances replace `zio.interop.catz._`, do not import both in the same scope. Instances for cats-core typeclasses,
such as `Parallel`, can be imported alongside:

```scala
import zio.interop.catz.core._
import zio.interop.catz.autocatch._
```

## Other typeclasses

There are many other typeclasses and useful conversions that this library provides implementations for:
* See `zio/interop/cats.scala` file to see all available typeclass implementations for the Cats Effect 3 typeclasses
* See `zio/stream/interop/cats.scala` for ZStream typeclass implementations
* See `zio/stream/interop/FS2StreamSyntax.scala` for `FS2 <-> ZStream` conversions

### `Resource` and `Dispatcher`

A `Resource` whose effect type is ZIO converts to a scoped ZIO, or to a `ZManaged`, with `toScopedZIO` and `toManagedZIO`. These don't need a `Dispatcher`:

```scala
import cats.effect.Resource
import zio._
import zio.interop.catz._

val resource: Resource[Task, String] =
  Resource.make[Task, String](ZIO.succeed("connection"))(_ => ZIO.debug("released"))

val program: Task[Unit] =
  ZIO.scoped(resource.toScopedZIO.flatMap(conn => ZIO.debug(s"using $conn")))
```

`Dispatcher` is a `Resource`, not a typeclass, so this library can't provide an implicit instance of it. To get a `Dispatcher[Task]`, for example to run ZIO effects from callback-based code, allocate it as a resource:

```scala
import cats.effect.std.Dispatcher
import zio._
import zio.interop.catz._

val program: Task[Unit] =
  ZIO.scoped {
    Dispatcher.parallel[Task].toScopedZIO.flatMap { dispatcher =>
      ZIO.attempt(dispatcher.unsafeRunAndForget(ZIO.debug("called back")))
    }
  }
```

A `Resource` over another effect type `F` (`toManaged`) does need a `Dispatcher[F]`. That dispatcher has to come from `F`'s own runtime. For example, for `cats.effect.IO`:

```scala
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import zio._
import zio.interop.catz._

def ioDispatcher(implicit runtime: IORuntime): ZIO[Scope, Throwable, Dispatcher[cats.effect.IO]] =
  ZIO
    .acquireRelease(ZIO.fromFuture(_ => Dispatcher.parallel[cats.effect.IO].allocated.unsafeToFuture())) {
      case (_, release) => ZIO.fromFuture(_ => release.unsafeToFuture()).orDie
    }
    .map(_._1)

def toZIO[A](resource: Resource[cats.effect.IO, A])(implicit runtime: IORuntime): ZIO[Scope, Throwable, A] =
  ioDispatcher.flatMap(implicit dispatcher => resource.toManaged.scoped)
```

In the other direction, `ZManaged` provides `toResourceZIO` and `toResource[F]`, and `Resource.scopedZIO` converts a scoped ZIO into a `Resource`.

### cats-core

If you only need instances for `cats-core` typeclasses, not `cats-effect` import `zio.interop.catz.core._`:

```scala
import zio.interop.catz.core._
```

Note that this library only has an `Optional` dependency on cats-effect – if you or your libraries don't depend on it, this library will not add it to the classpath.

### `NonEmptyList`

`ZIO.foreach` cannot be overloaded for `cats.data.NonEmptyList`, because an extension method is not considered for a name `ZIO` already defines. The variants that return a `NonEmptyList` are therefore named `foreachNel` and friends. The same operations are also extension methods on `NonEmptyList`, where the `Nel` suffix is unnecessary. Both are available from `zio.interop.catz.core._` and `zio.interop.catz._`:

```scala
import cats.data.NonEmptyList
import zio.*
import zio.interop.catz.core.*

val doubled = ZIO.foreachNel(NonEmptyList.of(1, 2, 3))(i => ZIO.succeed(i * 2))
val doubledPar = NonEmptyList.of(1, 2, 3).foreachPar(i => ZIO.succeed(i * 2))
```

`foreachNel` / `foreach` run sequentially and stop at the first failure. `foreachParNel` / `foreachPar` run in parallel and preserve order. `collectAllNel` / `collectAll` and `collectAllParNel` / `collectAllPar` do the same for a `NonEmptyList` of effects.

### Example

The following example shows how to use ZIO with Doobie (a library for JDBC access) and FS2 (a streaming library), which both rely on Cats Effect instances (`cats.effect.Async` and `cats.effect.Temporal`):

```scala
import zio.{durationInt as _, *}
import zio.interop.catz.*
import doobie.*
import doobie.implicits.*
import scala.concurrent.duration.*

object Example extends ZIOAppDefault:
  val run = {
    val xa: Transactor[Task] =
      Transactor.fromDriverManager[Task]("org.h2.Driver", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "user", "", None)

    sql"SELECT 42"
      .query[Int]
      .stream
      .transact(xa)
      .delayBy(1.second)
      .evalTap(i => Console.printLine(i))
      .compile
      .drain
      .exitCode
  }
```

## Law testing

The `zio-interop-cats-laws` artifact provides ScalaCheck `Arbitrary`, `Cogen` and cats `Eq`/`Order` instances for ZIO data types, so that cats and cats-effect laws (e.g. from `cats-laws` and `cats-effect-laws`) can be checked against your own ZIO-based typeclass instances. These are the same instances that this library uses to test its own typeclass instances.

```sbt
libraryDependencies += "dev.zio" %% "zio-interop-cats-laws" % "23.1.0.15" % Test
```

The instances are provided as traits in the `zio.interop.laws` package that are mixed into a test suite:
* `CatsTestInstances` – `Eq`, `Order` and `Cogen` instances for `ZIO`, `ZManaged` and `Cause`, and the implicit `ZIO[Any, E, Boolean] => Prop` conversion required by the cats-effect law tests. It extends `cats.effect.testkit.TestInstances`: effects are compared by running them deterministically on the `TestContext` of an implicit `Ticker`.
* `ZioTestInstances` – adds `Arbitrary` instances for `ZIO`, `ZManaged` and `Cause` (see `GenIOInteropCats` for the generators).
* `ZStreamTestInstances` – adds `Eq` and `Arbitrary` instances for `ZStream` (see `GenStreamInteropCats` for the generators).

The traits do not depend on a test framework. For example, with [discipline-scalatest](https://github.com/typelevel/discipline-scalatest):

```scala
import cats.effect.laws.GenTemporalTests
import org.scalatest.funsuite.AnyFunSuite
import org.typelevel.discipline.scalatest.FunSuiteDiscipline
import org.scalatest.prop.Configuration
import zio.{ durationInt as _, * }
import zio.interop.catz.*
import zio.interop.laws.ZioTestInstances
import scala.concurrent.duration.*

class TaskLawsSpec extends AnyFunSuite with FunSuiteDiscipline with Configuration with ZioTestInstances {
  implicit val ticker: Ticker = Ticker()

  checkAll("Temporal[Task]", GenTemporalTests[Task, Throwable].temporal[Int, Int, Int](100.millis))
}
```

This library's own law checks deviate from the stock cats-effect law tests as follows:
* ZIO does not satisfy the `evalOn local pure` and `executionContext commutativity` laws of `AsyncTests`, as `ZIO.executor.asExecutionContext` does not preserve the original `ExecutionContext`. These laws are checked with an `Eq[ExecutionContext]` that considers all execution contexts equal, which is deliberately not part of `zio-interop-cats-laws`.

## Links

- [Guide: How to Interop with Cats Effect?](https://zio.dev/guides/interop/with-cats-effect)
