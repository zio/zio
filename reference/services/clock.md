# Clock

> Provides time-related operations for retrieving current time in various units, accessing date-time information, and non-blocking sleep functionality.

Clock service contains some functionality related to time and scheduling. It follows the shared [service pattern](./anatomy.md) common to all of ZIO's built-in services: a `Clock` trait, a `Live` implementation, and a synchronous `UnsafeAPI` for interop.

| Function          | Input Type            | Output Type                     |
|-------------------|------------------------|----------------------------------|
| `currentTime`     | `unit: TimeUnit`       | `UIO[Long]`                       |
| `currentTime`     | `unit: ChronoUnit`     | `UIO[Long]`                       |
| `currentDateTime` |                        | `UIO[OffsetDateTime]`             |
| `instant`         |                        | `UIO[java.time.Instant]`          |
| `javaClock`       |                        | `UIO[java.time.Clock]`            |
| `localDateTime`   |                        | `UIO[java.time.LocalDateTime]`    |
| `nanoTime`        |                        | `UIO[Long]`                       |
| `scheduler`       |                        | `UIO[Scheduler]`                  |
| `sleep`           | `duration: Duration`   | `UIO[Unit]`                       |

To get the current time in a specific time unit, the `currentTime` function takes a unit as `TimeUnit` and returns `UIO[Long]`:

```scala
import zio._
import java.util.concurrent.TimeUnit

val inMilliseconds: UIO[Long] = Clock.currentTime(TimeUnit.MILLISECONDS)
val inDays        : UIO[Long] = Clock.currentTime(TimeUnit.DAYS)
```

`currentTime` also has an overload accepting a `java.time.temporal.ChronoUnit` instead of a `TimeUnit`; the two overloads compile side by side because the `ChronoUnit` version carries an extra `DummyImplicit` parameter:

```scala
import zio._
import java.time.temporal.ChronoUnit

val inSeconds: UIO[Long] = Clock.currentTime(ChronoUnit.SECONDS)
```

To get current date time in the current timezone the `currentDateTime` function returns a ZIO effect containing `OffsetDateTime`. `instant` and `localDateTime` return the equivalent `java.time.Instant` and `java.time.LocalDateTime` values, and `javaClock` returns a `java.time.Clock` backed by the same source of time, for interop with Java APIs that expect one:

```scala
import zio._

val program: UIO[Unit] =
  for {
    instant   <- Clock.instant
    localDT   <- Clock.localDateTime
    javaClock <- Clock.javaClock
    _         <- ZIO.succeed(javaClock.instant())
  } yield ()
```

`scheduler` exposes the underlying `Scheduler` that `sleep` and time-based combinators such as `Schedule` use to register delayed callbacks.

## How `sleep` Works

Also, the Clock service has a very useful functionality for sleeping and creating a delay between jobs. The `sleep` takes a `Duration` and sleeps for the specified duration. It is analogous to `java.lang.Thread.sleep` function, but it doesn't block any underlying thread. It's completely non-blocking.

`ClockLive` implements `sleep` with `ZIO.asyncInterrupt`, registering a callback with a single-threaded, JVM-global `ScheduledThreadPoolExecutor` rather than parking a fiber's thread. When the scheduled duration elapses, the scheduler invokes the callback, which resumes the fiber; if the fiber is interrupted first, the scheduled task is cancelled. This is why an arbitrary number of sleeping fibers costs one scheduler thread, not one thread per fiber, unlike `Thread.sleep`.

In the following example we are going to print the current time periodically by placing a one second `sleep` between each print call:

```scala
import zio._

def printTimeForever: ZIO[Any, Throwable, Nothing] =
  Clock.currentDateTime.flatMap(Console.printLine(_)) *>
    ZIO.sleep(1.seconds) *> printTimeForever
```

:::caution
`nanoTime` does not return wall-clock time. Like `System.nanoTime` on the JVM, it returns nanoseconds measured from an arbitrary, unspecified origin, so it's only meaningful for measuring elapsed durations between two calls — never for deriving an actual date or timestamp.
:::

## Synchronous Access (`unsafe`)

`Clock` also exposes a synchronous `UnsafeAPI`, following the pattern described in [Anatomy of a Built-in Service](./anatomy.md#synchronous-access-the-unsafeapi). `ClockLive`'s `unsafe` implementation calls straight through to `System.nanoTime`, `Instant.now()`, `LocalDateTime.now()`, and `OffsetDateTime.now()` instead of running an effect, which is useful when interoperating with non-ZIO code that cannot await a `ZIO` value:

```scala
import zio._

Unsafe.unsafe { implicit unsafe =>
  val now: Long = Clock.ClockLive.unsafe.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS)
}
```

Prefer the ordinary `Clock.currentTime` accessor everywhere else.

## Testing Time-Dependent Code

`TestClock` lets tests advance time deterministically instead of waiting on real sleeps. See [Testing Clock](../test/services/clock.md) for its `adjust`, `setTime`, and `sleeps` API.

For scheduling purposes like retry and repeats, ZIO has a great data type called [Schedule](../schedule.md).

Scala 2 and Scala 3 expose an identical `Clock` API; the `currentTime(ChronoUnit)` overload's `DummyImplicit` disambiguation trick works the same way in both versions.
