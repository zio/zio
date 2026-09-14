---
id: "anatomy"
title: "Anatomy of a Built-in Service"
description: "How Clock, Console, Random, and System share one design: a service trait, a companion object with a Tag and accessors, a Live implementation, and a synchronous UnsafeAPI."
keywords:
  - "Built-in Services"
  - "Service Pattern"
  - "ZIO Environment"
  - "UnsafeAPI"
  - "Live Environment"
  - "Test Environment"
---

Clock, Console, Random, and System are ZIO's four built-in services. Despite covering unrelated concerns — time, console I/O, randomness, and system access — all four are built from the same handful of pieces: a service trait, a companion object exposing a `Tag` and accessor methods, a `Live` implementation, and a synchronous `UnsafeAPI`. This page explains that shared design once; the [Clock](clock.md), [Console](console.md), [Random](random.md), and [System](system.md) pages each build on it for their own APIs.

## The Service Trait and Its Accessors

Each service starts as a plain trait describing its operations. Every method returns `UIO[A]` when it cannot fail, or an `IO[E, A]`/`Task[A]` when it can — `Console#printLine`, for example, returns `IO[IOException, Unit]` because writing to the console can fail. The trait itself is otherwise a normal Scala interface:

```scala
trait Clock extends Serializable {
  def currentTime(unit: TimeUnit): UIO[Long]
  def currentDateTime: UIO[OffsetDateTime]
  def sleep(duration: Duration): UIO[Unit]
}
```

The companion object identifies the trait to ZIO's environment with an implicit `Tag`, then republishes each trait method as a top-level accessor built from a shared helper — `ZIO.clockWith` for `Clock`, `ZIO.consoleWith` for `Console`, `ZIO.randomWith` for `Random`, and `ZIO.systemWith` for `System`. Each accessor is a one-line delegation:

- `Clock.currentTime(unit) = ZIO.clockWith(_.currentTime(unit))`
- `Console.printLine(line) = ZIO.consoleWith(_.printLine(line))`
- `Random.nextInt = ZIO.randomWith(_.nextInt)`
- `System.env(variable) = ZIO.systemWith(_.env(variable))`

`ZIO.clockWith` (and its three siblings) read the current `Clock` out of ZIO's environment and pass it to the given function, so we never fetch the service ourselves before calling `Clock.currentTime`:

```scala mdoc:compile-only
import zio._
import java.util.concurrent.TimeUnit

val millisSinceEpoch: UIO[Long] =
  ZIO.clockWith(_.currentTime(TimeUnit.MILLISECONDS))
```

## The `Live` Implementation

Each service ships one singleton implementation of its trait — `Clock.ClockLive`, `Console.ConsoleLive`, `Random.RandomLive`, `System.SystemLive` — that performs the real work. A `Live` implementation wraps the underlying platform call in `ZIO.succeed` when it cannot fail, or in a blocking constructor such as `ZIO.attemptBlockingIO` when it wraps a real blocking system call; the [Console](console.md) and [System](system.md) pages cover which of their methods fall into each category.

## Synchronous Access: The `UnsafeAPI`

Every service trait also nests a `UnsafeAPI` trait, exposed through a `def unsafe: UnsafeAPI` member, offering a synchronous counterpart to each asynchronous method:

```scala
trait Clock {
  trait UnsafeAPI extends Serializable {
    def currentTime(unit: TimeUnit)(implicit unsafe: Unsafe): Long
  }
  def unsafe: UnsafeAPI
}
```

The default `unsafe` falls back to `Runtime.default.unsafe.run(...).getOrThrowFiberFailure()`, but each `Live` implementation overrides it to call straight through to the underlying platform API instead of round-tripping through the ZIO runtime. You rarely call `unsafe` directly — it exists for synchronous interop with non-ZIO code that cannot await an effect; prefer the ordinary accessor (`Clock.currentTime`, `Console.printLine`, and so on) everywhere else.

## Composing Multiple Services in the Environment

Because each service is just a plain trait, an effect that needs more than one of them lists all of them in its environment type using an intersection type. Scala 2 spells this with the `with` keyword; Scala 3 spells the identical type with `&`:

```scala
// Scala 2
def effect: ZIO[Clock with Console, Nothing, Unit] = ???

// Scala 3
def effect: ZIO[Clock & Console, Nothing, Unit] = ???
```

Both forms describe the same requirement — an environment providing both `Clock` and `Console` — and ZIO's own source keeps using `with` even in its Scala 3 build, since the two are binary compatible. The [combined example](index.md) on the services overview page composes `Clock` and `Console` this way. Aside from that spelling difference, ZIO's Scala 2 and Scala 3 builds of these four services are functionally identical.

## How These Services Reach Your Program

A ZIO effect never has to provide `Clock`, `Console`, `Random`, or `System` explicitly. ZIO seeds a `FiberRef` of default services, `DefaultServices.live`, with `Live` implementations of the four services (alongside a default `ConfigProvider`) before a program ever runs, and every `ZIO.clockWith`-style accessor reads from that `FiberRef` unless something upstream provides a different implementation. The same four `Live` instances are also packaged as a `ZLayer` named `liveEnvironment`:

```scala mdoc:compile-only
import zio._

val liveEnvironment: Layer[Nothing, Clock with Console with System with Random] =
  ZLayer.succeedEnvironment(
    ZEnvironment[Clock, Console, System, Random](
      Clock.ClockLive,
      Console.ConsoleLive,
      System.SystemLive,
      Random.RandomLive
    )
  )
```

ZIO Test builds `TestEnvironment` on top of this exact layer, replacing each `Live` service with its deterministic counterpart — `TestClock`, `TestConsole`, `TestRandom`, and `TestSystem` — while still layering in `Live` access for tests that need it. Because the replacement happens entirely behind the same trait, code written against `Clock`, `Console`, `Random`, and `System` needs no changes to become testable — only its environment changes. See [Testing These Services](../test/services/index.md) for how each test double behaves.

## See Also

- [Testing These Services](../test/services/index.md) — the `TestClock`, `TestConsole`, `TestRandom`, and `TestSystem` doubles that replace `Live` in tests.
- [Writing ZIO Services](../service-pattern/index.md) — the general trait/companion/`ZLayer` pattern this design follows, for services you write yourself.
