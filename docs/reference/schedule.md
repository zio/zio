---
id: schedule
title: "Schedule"
description: "A composable, time-aware state machine for repeating and retrying ZIO effects with full control over timing, delays, and recurrence logic."
keywords:
  - "Retry Policy"
  - "Recurrence"
  - "Schedule Composition"
  - "Backoff Strategy"
  - "Schedule"
---

`Schedule[-Env, -In, +Out]` is a composable, time-aware state machine that describes when and how often a ZIO effect should execute. It observes an input `In` at each step — the effect's success value when repeating, or its error value when retrying — produces an output `Out`, and may require an environment `Env` for effects embedded in the schedule itself.

| Type parameter | Variance            | Role                                                                                                                              |
|----------------|---------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `Env`          | `-` (contravariant) | ZIO environment the schedule's own effects require                                                                                |
| `In`           | `-` (contravariant) | Value the schedule observes at each step — the effect's success type when repeating, or its error type when retrying              |
| `Out`          | `+` (covariant)     | Value the schedule produces at each step — often a count, a `Duration`, or a transformed input                                    |

Every concrete `Schedule` specifies three abstract members; all mutable data lives in the abstract `State` type:

```scala
trait Schedule[-Env, -In, +Out] extends Serializable { self =>
  type State

  def initial: State

  def step(now: OffsetDateTime, in: In, state: State)(implicit
    trace: Trace
  ): ZIO[Env, Nothing, (State, Out, Decision)]
}
```

`step` receives the current wall-clock `OffsetDateTime`, the current input, and the current state, and returns a `ZIO` that resolves to a triple: the updated state, the current output, and a `Decision`. A `Decision.Continue(interval)` tells the runtime how long to sleep before the next step; `Decision.Done` stops the loop.

Four design properties make `Schedule` a full algebra:

- **State machine** — All mutable data is captured in the abstract type member `State`. Combinators pair two `State` types into a product, so composed schedules remain pure values with no hidden mutation.
- **Time-aware** — `step` receives the current `OffsetDateTime` and returns timing information via `Intervals`, giving the runtime precise control over when each recurrence should begin.
- **Effect-capable** — Because `step` returns `ZIO[Env, Nothing, ...]`, a schedule can read clocks, draw random numbers, or call any service, all without breaking the functional model.
- **Composable algebra** — Operators such as `&&`, `||`, `>>>`, and `++` build new `Schedule` values from existing ones, intersecting or unioning timing and combining outputs with full type-safety.

## Usage

A typical use is retrying a failing effect with exponential backoff capped to a fixed number of attempts, combined with repeat for periodic polling:

```scala mdoc:reset
import zio._

// Retry with exponential backoff, at most 5 additional attempts, with random jitter
val policy: Schedule[Any, Any, Long] =
  Schedule.recurs(5) <* Schedule.exponential(100.millis).jittered

// Retry an HTTP call up to 5 times, waiting 100ms, 200ms, 400ms … between attempts
val result: ZIO[Any, Nothing, String] =
  ZIO.fail(new RuntimeException("service unavailable"))
    .retry(policy)
    .orElse(ZIO.succeed("fallback"))

// The same schedule type drives repeat for polling or heartbeat loops:
val heartbeat: ZIO[Any, Nothing, Long] =
  ZIO.logInfo("ping").repeat(Schedule.spaced(5.seconds))
```

## Installation

`Schedule` is part of the core `zio` module; no additional dependency is needed:

```scala
libraryDependencies += "dev.zio" %% "zio" % "@VERSION@"
```

## Creating Values

The companion object provides a large set of pre-built schedules. Every factory returns a `WithState[S, Env, In, Out]`, making the concrete state type `S` visible to the type system.

### Predefined Schedules

Three `val` members provide always-recurring base schedules that serve as building blocks for more complex policies:

```scala
object Schedule {
  val forever: Schedule.WithState[Long, Any, Any, Long]
  val count:   Schedule.WithState[Long, Any, Any, Long]
  val elapsed: Schedule.WithState[Option[OffsetDateTime], Any, Any, Duration]
}
```

`Schedule.forever` and `Schedule.count` are equivalent — both always recur, outputting an increasing count starting at 0. `Schedule.elapsed` always recurs and outputs the `Duration` since the very first step.

We can pipe `elapsed` after a delay schedule to observe how much wall-clock time has passed:

```scala mdoc:compile-only
import zio._

// Produce delay durations, then observe total elapsed time
val tickAndMeasure: Schedule[Any, Any, Duration] =
  Schedule.spaced(500.millis) >>> Schedule.elapsed
```

### Fixed-Count Recurrence

These factories build schedules that stop after a fixed number of repetitions:

```scala
object Schedule {
  def recurs(n: Long): Schedule.WithState[Long, Any, Any, Long]
  def recurs(n: Int):  Schedule.WithState[Long, Any, Any, Long]
  def once:            Schedule.WithState[Long, Any, Any, Unit]
  def stop:            Schedule.WithState[Long, Any, Any, Unit]
}
```

`Schedule.recurs(n)` runs `n` additional times after the first execution, outputting the counts 0 through `n − 1`. Both the `Long` and `Int` overloads behave identically; negative values behave as 0. `Schedule.once` is `recurs(1).unit` — one additional run. `Schedule.stop` is `recurs(0).unit` — no additional runs.

```scala mdoc:compile-only
import zio._

val fiveRetries = Schedule.recurs(5)   // 5 additional runs, outputs: 0, 1, 2, 3, 4
val singleRetry = Schedule.once        // 1 additional run
val noRetry     = Schedule.stop        // 0 additional runs
```

### Delay-Based Recurrence

These factories build always-recurring schedules whose primary purpose is to control the delay between steps:

```scala
object Schedule {
  def spaced(duration: Duration):
    Schedule.WithState[Long, Any, Any, Long]

  def fixed(interval: Duration):
    Schedule.WithState[(Option[(Long, Long)], Long), Any, Any, Long]

  def windowed(interval: Duration):
    Schedule.WithState[(Option[Long], Long), Any, Any, Long]

  def linear(base: Duration):
    Schedule.WithState[Long, Any, Any, Duration]

  def exponential(base: Duration, factor: Double = 2.0):
    Schedule.WithState[Long, Any, Any, Duration]

  def fibonacci(one: Duration):
    Schedule.WithState[(Duration, Duration), Any, Any, Duration]
}
```

`spaced(d)` waits exactly `d` after each execution ends. `fixed(d)` targets a fixed interval measured from execution start — if a run takes longer than `d`, the next starts immediately with no pile-up. `windowed(d)` divides the timeline into windows of length `d` and waits until the next window boundary. `linear(base)` produces delays `base × 1`, `base × 2`, `base × 3`, …, outputting the current delay `Duration`. `exponential(base, factor)` produces `base × factor⁰`, `base × factor¹`, … (default `factor = 2.0`), outputting the current delay `Duration`. `fibonacci(one)` produces delays following the Fibonacci sequence: `one, one, 2×one, 3×one, 5×one, …`, outputting the current delay `Duration`.

The following block shows these schedules as candidates for retry policies with progressively longer backoffs:

```scala mdoc:compile-only
import zio._

val linear100ms    = Schedule.linear(100.millis)         // 100ms, 200ms, 300ms, ...
val doubling100ms  = Schedule.exponential(100.millis)    // 100ms, 200ms, 400ms, ...
val tripling100ms  = Schedule.exponential(100.millis, 3.0) // 100ms, 300ms, 900ms, ...
val fibonacci100ms = Schedule.fibonacci(100.millis)      // 100ms, 100ms, 200ms, 300ms, ...
val every5s        = Schedule.fixed(5.seconds)
val windowOf10s    = Schedule.windowed(10.seconds)
```

### Duration-Bounded

These factories build schedules that recur for a specific total duration:

```scala
object Schedule {
  def duration(duration: Duration):
    Schedule.WithState[Boolean, Any, Any, Duration]

  def fromDuration(duration: Duration):
    Schedule.WithState[Boolean, Any, Any, Duration]

  def fromDurations(duration: Duration, durations: Duration*):
    Schedule.WithState[(::[Duration], Boolean), Any, Any, Duration]

  def upTo(duration: Duration):
    Schedule.WithState[Option[OffsetDateTime], Any, Any, Duration]
}
```

`duration(d)` and `fromDuration(d)` are aliases: both recur exactly once after sleeping `d`, then stop. `fromDurations(d, ds*)` recurs once for each provided duration, sleeping the corresponding duration between steps. `upTo(totalDuration)` recurs continuously while total elapsed time is less than `totalDuration`, outputting the elapsed `Duration`.

```scala mdoc:compile-only
import zio._

// Recur once after 5 seconds
val onceAfter5s = Schedule.duration(5.seconds)

// Recur at +4s, +7s, +12s, +19s (four recurrences total)
val customSteps = Schedule.fromDurations(4.seconds, 7.seconds, 12.seconds, 19.seconds)

// Repeat for at most 30 seconds, outputting elapsed time
val thirtySeconds = Schedule.upTo(30.seconds)
```

### Calendar and Cron-Like

The calendar schedules trigger at specific positions within a time unit, similar to cron expressions:

```scala
object Schedule {
  def secondOfMinute(second0: Int):
    Schedule.WithState[(OffsetDateTime, Long), Any, Any, Long]

  def minuteOfHour(minute: Int):
    Schedule.WithState[(OffsetDateTime, Long), Any, Any, Long]

  def hourOfDay(hour: Int):
    Schedule.WithState[(OffsetDateTime, Long), Any, Any, Long]

  def dayOfWeek(day: Int):
    Schedule.WithState[(OffsetDateTime, Long), Any, Any, Long]

  def dayOfMonth(day: Int):
    Schedule.WithState[(OffsetDateTime, Long), Any, Any, Long]
}
```

`secondOfMinute(s)` triggers at second `s` (0–59) of each minute. `minuteOfHour(m)` triggers at minute `m` (0–59) of each hour. `hourOfDay(h)` triggers at hour `h` (0–23) of each day. `dayOfWeek(d)` triggers on ISO-8601 day `d` (1 = Monday, 7 = Sunday) of each week at midnight. `dayOfMonth(d)` triggers on day `d` (1–31) of each month at midnight, skipping months that do not have that day. All outputs are increasing counts starting at 0.

```scala mdoc:compile-only
import zio._

val everyMinuteAt30 = Schedule.secondOfMinute(30)  // every minute at :30
val everyHourOnHour = Schedule.minuteOfHour(0)     // every hour at :00
val daily9am        = Schedule.hourOfDay(9)         // daily at 09:00
val everyTuesday    = Schedule.dayOfWeek(2)         // every Tuesday
val firstOfMonth    = Schedule.dayOfMonth(1)        // first day of each month
```

:::caution
Calendar schedules validate their argument **lazily**. A call such as `Schedule.dayOfWeek(9)` compiles without error, but the schedule dies with `IllegalArgumentException` the first time it runs. Valid ranges: `secondOfMinute` 0–59, `minuteOfHour` 0–59, `hourOfDay` 0–23, `dayOfWeek` 1–7 (ISO-8601), `dayOfMonth` 1–31.
:::

### Conditional (Input-Driven)

These factories produce schedules whose recurrence is controlled by inspecting each input value.

#### Predicate and Equality Variants

`recurWhile`, `recurWhileZIO`, and `recurWhileEquals` recur as long as a condition holds; `recurUntil`, `recurUntilZIO`, and `recurUntilEquals` recur until a condition holds. All pass the input through as output with no delay.

The `While` family (note: `recurWhileZIO` has no implicit `Trace` parameter):

```scala
object Schedule {
  def recurWhile[A](f: A => Boolean):
    Schedule.WithState[Unit, Any, A, A]

  def recurWhileZIO[Env, A](f: A => URIO[Env, Boolean]):
    Schedule.WithState[Unit, Env, A, A]

  def recurWhileEquals[A](a: => A):
    Schedule.WithState[Unit, Any, A, A]
}
```

The `Until` family:

```scala
object Schedule {
  def recurUntil[A](f: A => Boolean):
    Schedule.WithState[Unit, Any, A, A]

  def recurUntilZIO[Env, A](f: A => URIO[Env, Boolean]):
    Schedule.WithState[Unit, Env, A, A]

  def recurUntilEquals[A](a: => A):
    Schedule.WithState[Unit, Any, A, A]
}
```

The `ZIO` variants accept effectful predicates. The `Equals` variants compare with `==`. For example:

```scala mdoc:compile-only
import zio._

// Keep repeating while the result is less than 10
val whileBelowTen: Schedule[Any, Int, Int] = Schedule.recurWhile[Int](_ < 10)

// Retry until the value reaches 100
val untilHundred: Schedule[Any, Int, Int] = Schedule.recurUntil[Int](_ >= 100)

// Retry while a database flag reports "busy" (effectful check)
val whileBusy: Schedule[Any, String, String] =
  Schedule.recurWhileZIO[Any, String](s => ZIO.succeed(s == "busy"))
```

#### Partial-Function Variant

`recurUntil` has a second overload that accepts a `PartialFunction` and outputs `Option[B]`:

```scala
object Schedule {
  def recurUntil[A, B](pf: PartialFunction[A, B]):
    Schedule.WithState[Unit, Any, A, Option[B]]
}
```

The output is `None` at every step where `pf` is not defined on the current input, and `Some(b)` when `pf` first matches. The schedule stops as soon as `pf` matches — this lets the caller detect a terminal condition and extract a typed value in a single step:

```scala mdoc:compile-only
import zio._

sealed trait Event
case class Ready(value: Int) extends Event
case object Pending          extends Event

// Recur until a Ready event arrives; extract the payload
val awaitReady: Schedule[Any, Event, Option[Int]] =
  Schedule.recurUntil[Event, Int] { case Ready(v) => v }
```

### Collecting Inputs

These companion-object schedules always recur and accumulate the stream of *input* values into a `Chunk`:

```scala
object Schedule {
  def collectAll[A]:
    Schedule.WithState[(Unit, Chunk[A]), Any, A, Chunk[A]]

  def collectWhile[A](f: A => Boolean):
    Schedule.WithState[(Unit, Chunk[A]), Any, A, Chunk[A]]

  def collectWhileZIO[Env, A](f: A => URIO[Env, Boolean]):
    Schedule.WithState[(Unit, Chunk[A]), Env, A, Chunk[A]]

  def collectUntil[A](f: A => Boolean):
    Schedule.WithState[(Unit, Chunk[A]), Any, A, Chunk[A]]

  def collectUntilZIO[Env, A](f: A => URIO[Env, Boolean]):
    Schedule.WithState[(Unit, Chunk[A]), Env, A, Chunk[A]]
}
```

`collectAll[A]` collects every input into a growing `Chunk[A]`, continuing indefinitely. `collectWhile(f)` stops once `f(input)` is false. `collectUntil(f)` stops once `f(input)` is true. These companion constructors collect *inputs* — for collecting the *outputs* of an existing schedule, see `Schedule#collectAll` in [Collecting Outputs](#collecting-outputs). Usage:

```scala mdoc:compile-only
import zio._

// Collect all success values of a repeated effect
val collectEverything: Schedule[Any, Int, Chunk[Int]] =
  Schedule.collectAll[Int]

// Collect while values stay below 10
val collectSmall: Schedule[Any, Int, Chunk[Int]] =
  Schedule.collectWhile[Int](_ < 10)
```

### Primitives and Building Blocks

These low-level factories provide the raw material for building custom schedules:

```scala
object Schedule {
  def identity[A]:
    Schedule.WithState[Unit, Any, A, A]

  def succeed[A](a: => A):
    Schedule.WithState[Long, Any, Any, A]

  def fromFunction[A, B](f: A => B):
    Schedule.WithState[Unit, Any, A, B]

  def unfold[A](a: => A)(f: A => A):
    Schedule.WithState[A, Any, Any, A]

  def delayed[Env, In](schedule: Schedule[Env, In, Duration]):
    Schedule.WithState[schedule.State, Env, In, Duration]
}
```

`Schedule.identity[A]` always recurs with no delay, passing each input through as output — it is the foundation of all `recurWhile*`, `recurUntil*`, and `collectAll*` factories. `Schedule.succeed(a)` always recurs, producing the constant value `a` at each step. `Schedule.fromFunction(f)` always recurs, mapping each input through `f` to produce the output. `Schedule.unfold(a)(f)` always recurs with no delay: it starts from state `a` and applies `f` to advance the state, emitting the state as output — this is how `forever` and `count` are implemented.

`Schedule.delayed(schedule)` is a *companion constructor* that wraps a schedule which already produces `Duration` values and adds each duration as a delay to the corresponding interval. This is distinct from the instance method `schedule.delayed(f: Duration => Duration)`, which transforms an existing delay — see [Scaling the Delay](#scaling-the-delay).

```scala mdoc:compile-only
import zio._

// Pass each input through unchanged, recur forever with no delay
val id: Schedule[Any, String, String] = Schedule.identity[String]

// Always recur, always output the string "ok"
val constant: Schedule[Any, Any, String] = Schedule.succeed("ok")

// Map each error to its message length, always recur
val msgLen: Schedule[Any, Throwable, Int] =
  Schedule.fromFunction[Throwable, Int](_.getMessage.length)

// Output powers of 2: 1, 2, 4, 8, 16, ...
val powers: Schedule[Any, Any, Int] = Schedule.unfold(1)(_ * 2)

// Add each output Duration as a delay to the next interval
val delayedPowers: Schedule[Any, Any, Duration] =
  Schedule.delayed(Schedule.unfold(1)(_ * 2).map(n => (n * 100).millis))
```

## Core Operations

Instance methods on `Schedule` transform, filter, combine, and observe schedules. All are `final` and return a `WithState[..., ...]` with the concrete state type visible.

### Combining Schedules

These operators merge two schedules into one, combining their timing and their outputs. We can intersect (both must agree to continue), union (either is enough to continue), sequence (run one then the other), pipe (output of one feeds the input of the other), or route separate inputs to separate schedules.

#### Intersection (AND)

`&&`, `zip`, and `<*>` are equivalent operators that continue only while *both* schedules want to continue, sleeping until the *later* of the two intervals (geometric intersection). `zipLeft` (alias `<*`) keeps only the left output; `zipRight` (alias `*>`) keeps only the right output. `zipWith` combines outputs with a custom function. `intersectWith` is the low-level primitive underlying `&&`:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def &&[Env1 <: Env, In1 <: In, Out2](that: Schedule[Env1, In1, Out2])(implicit
    zippable: Zippable[Out, Out2]
  ): Schedule.WithState[(self.State, that.State), Env1, In1, zippable.Out]

  final def zip[Env1 <: Env, In1 <: In, Out2](that: Schedule[Env1, In1, Out2])(implicit
    zippable: Zippable[Out, Out2]
  ): Schedule.WithState[(self.State, that.State), Env1, In1, zippable.Out]

  final def zipLeft[Env1 <: Env, In1 <: In, Out2](that: Schedule[Env1, In1, Out2])(implicit
    trace: Trace
  ): Schedule.WithState[(self.State, that.State), Env1, In1, Out]

  final def zipRight[Env1 <: Env, In1 <: In, Out2](that: Schedule[Env1, In1, Out2])(implicit
    trace: Trace
  ): Schedule.WithState[(self.State, that.State), Env1, In1, Out2]

  final def zipWith[Env1 <: Env, In1 <: In, Out2, Out3](
    that: Schedule[Env1, In1, Out2]
  )(f: (Out, Out2) => Out3)(implicit
    trace: Trace
  ): Schedule.WithState[(self.State, that.State), Env1, In1, Out3]

  final def intersectWith[Env1 <: Env, In1 <: In, Out2](
    that: Schedule[Env1, In1, Out2]
  )(f: (Intervals, Intervals) => Intervals)(implicit
    zippable: Zippable[Out, Out2]
  ): Schedule.WithState[(self.State, that.State), Env1, In1, zippable.Out]
}
```

`zip` is the named alias for `&&`; `<*>` is the operator alias. `<*` is the operator alias for `zipLeft`; `*>` is the operator alias for `zipRight`. `&&` delegates to `intersectWith` with `_.intersect(_)`.

```scala mdoc:compile-only
import zio._

// Retry up to 5 times, spaced 1 second apart — both constraints must be satisfied
val bounded: Schedule[Any, Any, (Long, Long)] =
  Schedule.recurs(5) && Schedule.spaced(1.second)

// Same intersection but output only the retry count
val countOnly: Schedule[Any, Any, Long] =
  Schedule.recurs(5).zipLeft(Schedule.spaced(1.second))

// Combine two outputs with a custom function
val labeled: Schedule[Any, Any, String] =
  Schedule.recurs(5).zipWith(Schedule.spaced(1.second))((count, _) => s"attempt $count")
```

#### Union (OR)

`||` and `either` continue as long as *either* schedule wants to continue, sleeping until the *earlier* of the two intervals (geometric union). `eitherWith` combines outputs with a custom function. `unionWith` is the low-level primitive underlying `||`:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def ||[Env1 <: Env, In1 <: In, Out2](that: Schedule[Env1, In1, Out2])(implicit
    zippable: Zippable[Out, Out2]
  ): Schedule.WithState[(self.State, that.State), Env1, In1, zippable.Out]

  final def either[Env1 <: Env, In1 <: In, Out2](
    that: Schedule[Env1, In1, Out2]
  ): Schedule.WithState[(self.State, that.State), Env1, In1, (Out, Out2)]

  final def eitherWith[Env1 <: Env, In1 <: In, Out2, Out3](
    that: Schedule[Env1, In1, Out2]
  )(f: (Out, Out2) => Out3)(implicit
    trace: Trace
  ): Schedule.WithState[(self.State, that.State), Env1, In1, Out3]

  final def unionWith[Env1 <: Env, In1 <: In, Out2](
    that: Schedule[Env1, In1, Out2]
  )(f: (Intervals, Intervals) => Intervals)(implicit
    zippable: Zippable[Out, Out2]
  ): Schedule.WithState[(self.State, that.State), Env1, In1, zippable.Out]
}
```

`either` is the named alias for `||`, explicitly typed to return `(Out, Out2)`. `||` delegates to `unionWith` with `_.union(_)`.

```scala mdoc:compile-only
import zio._

// Continue while either schedule wants to: up to 5 reps OR for up to 30 seconds
val fiveOrThirty: Schedule[Any, Any, (Long, Duration)] =
  Schedule.recurs(5) || Schedule.upTo(30.seconds)

// Custom merge of the two outputs into a single string
val merged: Schedule[Any, Any, String] =
  Schedule.recurs(5).eitherWith(Schedule.upTo(30.seconds))((count, elapsed) =>
    s"attempt $count after $elapsed"
  )
```

#### Sequencing

`andThen` (alias `++`) runs `self` to completion and then runs `that`:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def andThen[Env1 <: Env, In1 <: In, Out2 >: Out](
    that: Schedule[Env1, In1, Out2]
  ): Schedule.WithState[(self.State, that.State, Boolean), Env1, In1, Out2]

  final def andThenEither[Env1 <: Env, In1 <: In, Out2](
    that: Schedule[Env1, In1, Out2]
  ): Schedule.WithState[(self.State, that.State, Boolean), Env1, In1, Either[Out, Out2]]
}
```

`andThen` merges the outputs via a common supertype `Out2 >: Out`. `andThenEither` (alias `<||>`) preserves the left/right distinction in the output as `Either[Out, Out2]`.

```scala mdoc:compile-only
import zio._

// Retry immediately 3 times, then switch to spaced retries
val quickThenSlow: Schedule[Any, Any, Long] =
  Schedule.recurs(3) ++ Schedule.spaced(1.second)

// Same, but tag which phase produced each output
val tagged: Schedule[Any, Any, Either[Long, Long]] =
  Schedule.recurs(3).andThenEither(Schedule.spaced(1.second))
```

#### Piping

`>>>` pipes the output of `self` into the input of `that`. `<<<` is the reversed form, and `compose` is its named alias:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def >>>[Env1 <: Env, Out2](
    that: Schedule[Env1, Out, Out2]
  ): Schedule.WithState[(self.State, that.State), Env1, In, Out2]

  final def <<<[Env1 <: Env, In2](
    that: Schedule[Env1, In2, In]
  ): Schedule.WithState[(that.State, self.State), Env1, In2, Out]

  final def compose[Env1 <: Env, In2](
    that: Schedule[Env1, In2, In]
  ): Schedule.WithState[(that.State, self.State), Env1, In2, Out]
}
```

The combined decision takes the maximum (later) of both intervals. `compose` is the named alias for `<<<`: `self <<< that` and `self.compose(that)` are equivalent.

```scala mdoc:compile-only
import zio._

// Produce exponential delays, then pipe them into a schedule that outputs elapsed time
val exponentialElapsed: Schedule[Any, Any, Duration] =
  Schedule.exponential(1.second) >>> Schedule.elapsed
```

#### Input Routing

These operators split or route inputs between two independent schedules:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def ***[Env1 <: Env, In2, Out2](
    that: Schedule[Env1, In2, Out2]
  ): Schedule.WithState[(self.State, that.State), Env1, (In, In2), (Out, Out2)]

  final def +++[Env1 <: Env, In2, Out2](
    that: Schedule[Env1, In2, Out2]
  ): Schedule.WithState[(self.State, that.State), Env1, Either[In, In2], Either[Out, Out2]]

  final def |||[Env1 <: Env, Out1 >: Out, In2](
    that: Schedule[Env1, In2, Out1]
  ): Schedule.WithState[(self.State, that.State), Env1, Either[In, In2], Out1]

  final def first[X]: Schedule.WithState[(self.State, Unit), Env, (In, X), (Out, X)]
  final def second[X]: Schedule.WithState[(Unit, self.State), Env, (X, In), (X, Out)]

  final def left[X]: Schedule.WithState[(self.State, Unit), Env, Either[In, X], Either[Out, X]]
  final def right[X]: Schedule.WithState[(Unit, self.State), Env, Either[X, In], Either[X, Out]]
}
```

`***` takes a tuple input `(In, In2)` and applies `self` to the first element and `that` to the second. Both schedules must want to continue — if either emits `Done`, the combined schedule stops. When both continue, the next wakeup is the earlier of the two intervals (union on timing, unlike `&&` which uses the later of the two). `+++` routes `Left[In]` to `self` and `Right[In2]` to `that`, outputting `Either[Out, Out2]`. `|||` is `(self +++ that).map(_.merge)` — it routes `Either` inputs but merges the output to a single type `Out1`. `first` applies `self` to the first element of a pair, passing the second through unchanged. `second` applies `self` to the second element of a pair. `left` applies `self` to `Left` inputs and passes `Right` through unchanged. `right` applies `self` to `Right` inputs and passes `Left` through unchanged.

```scala mdoc:compile-only
import zio._

// Apply different recurrence counts to a pair of inputs simultaneously
val pairSchedule: Schedule[Any, (Int, String), (Long, Long)] =
  Schedule.recurs(5) *** Schedule.recurs(3)

// Route Either inputs to the appropriate schedule
val eitherSchedule: Schedule[Any, Either[Int, String], Either[Long, Long]] =
  Schedule.recurs(5) +++ Schedule.recurs(3)
```

### Transforming

These methods change the type or value of a schedule's inputs or outputs without affecting its recurrence logic.

#### Mapping Outputs

`map` and `mapZIO` transform every output value the schedule produces:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def map[Out2](f: Out => Out2):
    Schedule.WithState[self.State, Env, In, Out2]

  final def mapZIO[Env1 <: Env, Out2](f: Out => URIO[Env1, Out2]):
    Schedule.WithState[self.State, Env1, In, Out2]
}
```

`map` applies a pure function to each output. `mapZIO` applies an effectful function that may use services from `Env1`.

```scala mdoc:compile-only
import zio._

// Turn a count into a human-readable message
val messages: Schedule[Any, Any, String] =
  Schedule.recurs(5).map(n => s"attempt ${n + 1} of 5")

// Map each output through an effectful logger
val logged: Schedule[Any, Any, Long] =
  Schedule.recurs(5).mapZIO(n => ZIO.logInfo(s"step $n").as(n))
```

#### Constant Output

`as` and `unit` replace the schedule's output with a fixed value:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def as[Out2](out2: => Out2):
    Schedule.WithState[self.State, Env, In, Out2]

  final def unit:
    Schedule.WithState[self.State, Env, In, Unit]
}
```

`as(value)` replaces every output with `value`. `unit` is `as(())`.

```scala mdoc:compile-only
import zio._

// Discard the count and output a constant string
val asString: Schedule[Any, Any, String] = Schedule.recurs(5).as("retried")

// Discard output entirely
val noOutput: Schedule[Any, Any, Unit] = Schedule.recurs(5).unit
```

#### Mapping Inputs

`contramap`, `contramapZIO`, `dimap`, and `dimapZIO` transform the input type before the schedule observes it:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def contramap[Env1 <: Env, In2](f: In2 => In):
    Schedule.WithState[self.State, Env, In2, Out]

  final def contramapZIO[Env1 <: Env, In2](f: In2 => URIO[Env1, In]):
    Schedule.WithState[self.State, Env1, In2, Out]

  final def dimap[In2, Out2](f: In2 => In, g: Out => Out2):
    Schedule.WithState[self.State, Env, In2, Out2]

  final def dimapZIO[Env1 <: Env, In2, Out2](
    f: In2 => URIO[Env1, In],
    g: Out => URIO[Env1, Out2]
  ): Schedule.WithState[self.State, Env1, In2, Out2]
}
```

`contramap(f)` applies `f` to convert `In2` into the expected `In` before each step. `dimap(f, g)` combines an input transformation with an output transformation. The `ZIO` variants accept effectful transformations.

```scala mdoc:compile-only
import zio._

// Adapt a schedule that expects Throwable to accept String error messages
val forThrowable: Schedule[Any, Throwable, Long] = Schedule.recurs(5)

val forString: Schedule[Any, String, Long] =
  forThrowable.contramap((msg: String) => new RuntimeException(msg))

// Transform both input and output simultaneously
val dimapped: Schedule[Any, String, String] =
  forThrowable.dimap[String, String](
    s => new RuntimeException(s),
    n => s"step $n"
  )
```

#### Passing Input Through as Output

`passthrough` discards the schedule's own output and substitutes the current input instead:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def passthrough[In1 <: In]:
    Schedule.WithState[self.State, Env, In1, In1]
}
```

The schedule still controls *when* recurrences happen; each output carries the input value rather than the schedule's computed value.

```scala mdoc:compile-only
import zio._

// Retry 5 times; output the error at each step instead of the count
val errorPassthrough: Schedule[Any, Throwable, Throwable] =
  Schedule.recurs(5).passthrough
```

### Filtering and Guards

These operators stop the schedule early based on conditions applied to the input or output at each step.

#### Input-Based Guards

`check`, `checkZIO`, `whileInput`, `whileInputZIO`, `untilInput`, and `untilInputZIO` stop the schedule based on the input value:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def check[In1 <: In](test: (In1, Out) => Boolean):
    Schedule.WithState[self.State, Env, In1, Out]

  final def checkZIO[Env1 <: Env, In1 <: In](test: (In1, Out) => URIO[Env1, Boolean]):
    Schedule.WithState[self.State, Env1, In1, Out]

  final def whileInput[In1 <: In](f: In1 => Boolean):
    Schedule.WithState[self.State, Env, In1, Out]

  final def whileInputZIO[Env1 <: Env, In1 <: In](f: In1 => URIO[Env1, Boolean]):
    Schedule.WithState[self.State, Env1, In1, Out]

  final def untilInput[In1 <: In](f: In1 => Boolean):
    Schedule.WithState[self.State, Env, In1, Out]

  final def untilInputZIO[Env1 <: Env, In1 <: In](
    f: In1 => URIO[Env1, Boolean]
  ): Schedule.WithState[self.State, Env1, In1, Out]
}
```

`check(test)` receives both the input and the current output and stops when `test` returns `false`. `whileInput(f)` is `check((in, _) => f(in))`. `untilInput(f)` is the inverse — it stops when `f(input)` is `true`.

```scala mdoc:compile-only
import zio._

// Retry with exponential backoff, but only while the error is an IOException
val ioRetry: Schedule[Any, Throwable, Duration] =
  Schedule.exponential(100.millis).whileInput[Throwable] {
    case _: java.io.IOException => true
    case _                      => false
  }

// Stop once input count reaches 50
val untilFifty: Schedule[Any, Int, Long] =
  Schedule.forever.untilInput[Int](_ >= 50)
```

#### Output-Based Guards

`whileOutput`, `whileOutputZIO`, `untilOutput`, and `untilOutputZIO` stop based on the schedule's own output value:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def whileOutput(f: Out => Boolean):
    Schedule.WithState[self.State, Env, In, Out]

  final def whileOutputZIO[Env1 <: Env](f: Out => URIO[Env1, Boolean]):
    Schedule.WithState[self.State, Env1, In, Out]

  final def untilOutput(f: Out => Boolean):
    Schedule.WithState[self.State, Env, In, Out]

  final def untilOutputZIO[Env1 <: Env](f: Out => URIO[Env1, Boolean]):
    Schedule.WithState[self.State, Env1, In, Out]
}
```

`whileOutput(f)` stops when `f(output)` is `false`. `untilOutput(f)` stops when `f(output)` is `true`.

```scala mdoc:compile-only
import zio._

// Stop exponential backoff once a single delay would exceed 10 seconds
val cappedBackoff: Schedule[Any, Any, Duration] =
  Schedule.exponential(100.millis).whileOutput(_ <= 10.seconds)

// Stop once elapsed time surpasses 1 minute
val timedOut: Schedule[Any, Any, Duration] =
  Schedule.elapsed.untilOutput(_ >= 1.minute)
```

### Timing and Delays

These methods control how long the schedule sleeps between steps.

#### Adding Delays

`addDelay` and `addDelayZIO` add extra delay on top of whatever interval the schedule already produces:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def addDelay(f: Out => Duration):
    Schedule.WithState[self.State, Env, In, Out]

  final def addDelayZIO[Env1 <: Env](f: Out => URIO[Env1, Duration]):
    Schedule.WithState[self.State, Env1, In, Out]
}
```

`addDelay(f)` computes an extra duration from the current output and adds it to each interval. `addDelayZIO(f)` does the same with an effectful function.

```scala mdoc:compile-only
import zio._

// Add a fixed 500ms extra delay to every recurrence
val extraDelay: Schedule[Any, Any, Long] =
  Schedule.forever.addDelay(_ => 500.millis)

// Add a delay proportional to the retry count
val linearExtra: Schedule[Any, Any, Long] =
  Schedule.forever.addDelay(count => (count * 100).millis)
```

#### Scaling the Delay

`delayed` (instance method) and `delayedZIO` transform the existing delay duration through a mapping function:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def delayed(f: Duration => Duration):
    Schedule.WithState[self.State, Env, In, Out]

  final def delayedZIO[Env1 <: Env](f: Duration => URIO[Env1, Duration]):
    Schedule.WithState[self.State, Env1, In, Out]
}
```

`delayed(f)` replaces each interval's delay with `f(currentDelay)`, scaling or offsetting the existing delay without changing the schedule's output type.

:::note
This is the *instance method* `schedule.delayed(f: Duration => Duration)`. The companion constructor `Schedule.delayed(schedule)` is a different method: it wraps a schedule that already outputs `Duration` values and adds those durations as delays. See [Primitives and Building Blocks](#primitives-and-building-blocks).
:::

```scala mdoc:compile-only
import zio._

// Double every delay produced by the exponential schedule
val doubledBackoff: Schedule[Any, Any, Duration] =
  Schedule.exponential(100.millis).delayed(_ * 2)

// Cap any single delay at 30 seconds
val cappedDelay: Schedule[Any, Any, Duration] =
  Schedule.exponential(100.millis).delayed(d => if (d > 30.seconds) 30.seconds else d)
```

#### Low-Level Delay Modification

`modifyDelay` and `modifyDelayZIO` are the primitives underlying `addDelay` and `delayed`. They expose both the current output and the current delay together:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def modifyDelay(f: (Out, Duration) => Duration):
    Schedule.WithState[self.State, Env, In, Out]

  final def modifyDelayZIO[Env1 <: Env](f: (Out, Duration) => URIO[Env1, Duration]):
    Schedule.WithState[self.State, Env1, In, Out]
}
```

Both `addDelayZIO` and `delayedZIO` delegate to `modifyDelayZIO`. Reach for these when you need both the output value and the delay at the same time to compute the new delay.

```scala mdoc:compile-only
import zio._

// Multiply each delay by the step count (uses both output and delay)
val scaledByCount: Schedule[Any, Any, Long] =
  Schedule.forever.modifyDelay((count, delay) => delay * (count + 1))
```

#### Jitter

`jittered` randomly perturbs the delay at each step, reducing thundering-herd problems when many effects retry simultaneously:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def jittered:
    Schedule.WithState[self.State, Env, In, Out]

  final def jittered(min: Double, max: Double):
    Schedule.WithState[self.State, Env, In, Out]
}
```

The no-arg form randomises each delay in the range `[0.8 × delay, 1.2 × delay]`. The two-argument form randomises in `[min × delay, max × delay]`.

:::caution
The no-arg `jittered` form keeps the *average* delay unchanged (factor ≈ 1.0), which spreads execution times slightly but does not reduce the overall retry load. Under high retry pressure — for example, when many clients retry simultaneously after a service restart — use `jittered(0.0, 1.0)` instead. That range reduces the amortized delay to 50% of the original, actively preventing a load spike.
:::

:::note
`jittered` no longer requires `Random` in `Env`. In ZIO 2, `Random` is a built-in runtime service and is used internally without appearing in the schedule's environment type.
:::

```scala mdoc:compile-only
import zio._

// Exponential backoff with mild jitter (average delay unchanged)
val mildJitter: Schedule[Any, Any, Duration] =
  Schedule.exponential(100.millis).jittered

// Full jitter: each delay in [0, currentDelay], amortized to 50% of original
val fullJitter: Schedule[Any, Any, Duration] =
  Schedule.exponential(100.millis).jittered(0.0, 1.0)
```

#### Extracting Delays as Output

`delays` replaces the schedule's output type with the actual sleep duration before each step:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def delays: Schedule.WithState[self.State, Env, In, Duration]
}
```

This is useful for observing or logging the actual wait times produced by a complex schedule.

```scala mdoc:compile-only
import zio._

// Observe the delay durations produced by exponential backoff
val backoffDelays: Schedule[Any, Any, Duration] =
  Schedule.exponential(1.second).delays
```

#### Bounding Elapsed Time

The instance method `upTo` wraps an existing schedule and stops it once total elapsed time exceeds a bound:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def upTo(duration: Duration):
    Schedule.WithState[(self.State, Option[OffsetDateTime]), Env, In, Out]
}
```

This is distinct from the companion constructor `Schedule.upTo(duration)`, which creates a *new* schedule that itself outputs elapsed time. The instance method applies the bound to `self`, preserving `self`'s output type.

```scala mdoc:compile-only
import zio._

// Retry with exponential backoff but stop after 1 minute total
val timedBackoff: Schedule[Any, Any, Duration] =
  Schedule.exponential(100.millis).upTo(1.minute)
```

#### Auto-Resetting on Inactivity

`resetAfter` and `resetWhen` restart a schedule from its initial state under specific conditions:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def resetAfter(duration: Duration):
    Schedule.WithState[(self.State, Option[OffsetDateTime]), Env, In, Out]

  final def resetWhen(f: Out => Boolean):
    Schedule.WithState[self.State, Env, In, Out]
}
```

`resetAfter(d)` resets the schedule to its initial state whenever `d` has elapsed since the last step (an inactivity reset). `resetWhen(f)` resets whenever `f(output)` is `true`.

:::note
In early ZIO 2, `resetWhen` reset the schedule only once rather than on every trigger, causing a regression from ZIO 1 behaviour. This regression is now fixed: the schedule resets on every step where the predicate returns `true`.
:::

```scala mdoc:compile-only
import zio._

// Allow up to 5 retries; reset the counter after 10 seconds of inactivity
val resilient: Schedule[Any, Any, Long] =
  Schedule.recurs(5).resetAfter(10.seconds)

// Reset the exponential backoff whenever it would reach 5 seconds
val cappedAndReset: Schedule[Any, Any, Duration] =
  Schedule.exponential(100.millis).resetWhen(_ >= 5.seconds)
```

### Accumulation and Folding

These instance methods accumulate the outputs of a schedule into a summary value.

#### Collecting Outputs

The instance `collectAll`, `collectWhile`, `collectWhileZIO`, `collectUntil`, and `collectUntilZIO` wrap `self` and collect its outputs into a `Chunk`:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def collectAll[Out1 >: Out]:
    Schedule.WithState[(self.State, Chunk[Out1]), Env, In, Chunk[Out1]]

  final def collectWhile[Out1 >: Out](f: Out => Boolean):
    Schedule.WithState[(self.State, Chunk[Out1]), Env, In, Chunk[Out1]]

  final def collectWhileZIO[Env1 <: Env, Out1 >: Out](
    f: Out => URIO[Env1, Boolean]
  ): Schedule.WithState[(self.State, Chunk[Out1]), Env1, In, Chunk[Out1]]

  final def collectUntil[Out1 >: Out](f: Out => Boolean):
    Schedule.WithState[(self.State, Chunk[Out1]), Env, In, Chunk[Out1]]

  final def collectUntilZIO[Env1 <: Env, Out1 >: Out](
    f: Out => URIO[Env1, Boolean]
  ): Schedule.WithState[(self.State, Chunk[Out1]), Env1, In, Chunk[Out1]]
}
```

Unlike the companion-object `Schedule.collectAll[A]` which collects *inputs* passed to the schedule, these instance methods collect the *outputs* of the wrapped schedule `self`. `collectWhile(f)` stops and emits the accumulated `Chunk` when `f(output)` is `false`. `collectUntil(f)` stops when `f(output)` is `true`.

:::note
The first output emitted by `collectAll` and related folding operations is never an empty `Chunk`. An earlier implementation emitted the initial accumulator immediately, producing an empty chunk before any real outputs accumulated. This has been corrected.
:::

```scala mdoc:compile-only
import zio._

// Collect all delay durations over 5 exponential steps
val collectedDelays: Schedule[Any, Any, Chunk[Duration]] =
  Schedule.exponential(100.millis).delays.collectAll

// Collect exponential delays until one would exceed 5 seconds
val smallDelays: Schedule[Any, Any, Chunk[Duration]] =
  Schedule.exponential(100.millis).delays.collectUntil(_ >= 5.seconds)
```

#### Folding Outputs

`fold` and `foldZIO` accumulate the schedule's outputs into a single summary value `Z`:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def fold[Z](z: Z)(f: (Z, Out) => Z):
    Schedule.WithState[(self.State, Z), Env, In, Z]

  final def foldZIO[Env1 <: Env, Z](z: Z)(f: (Z, Out) => URIO[Env1, Z]):
    Schedule.WithState[(self.State, Z), Env1, In, Z]
}
```

At each step, `f` combines the running accumulator with the current output. The schedule continues according to its own logic; the output emitted at each step is the current accumulated `Z`.

```scala mdoc:compile-only
import zio._

// Sum all retry counts
val sumCounts: Schedule[Any, Any, Long] =
  Schedule.recurs(5).fold(0L)(_ + _)

// Build a bracketed log string from each output
val history: Schedule[Any, Any, String] =
  Schedule.recurs(5).fold("")((acc, n) => s"$acc[$n]")
```

#### Counting Repetitions

`repetitions` replaces the schedule's output with a running count of how many times the schedule has fired:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def repetitions:
    Schedule.WithState[(self.State, Long), Env, In, Long]
}
```

It is implemented as `fold(0L)((n, _) => n + 1L)`.

```scala mdoc:compile-only
import zio._

// Count steps of an exponential backoff schedule, ignoring the delay output
val stepCount: Schedule[Any, Any, Long] =
  Schedule.exponential(100.millis).repetitions
```

### Inspecting and Simulating

`run` simulates the schedule against a list of inputs without performing any actual sleeping:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def run(now: OffsetDateTime, input: Iterable[In]):
    URIO[Env, Chunk[Out]]
}
```

It feeds each element of `input` as a successive step, collecting the outputs. No real time passes — the schedule advances its internal state as if those inputs had arrived. This is primarily useful in tests and for inspecting the shape of a schedule's outputs.

```scala mdoc:compile-only
import zio._

val expSchedule: Schedule[Any, Any, Duration] = Schedule.exponential(1.minute)

// Inspect the first 5 delay values without sleeping
val simulate: URIO[Any, Chunk[Duration]] =
  Clock.currentDateTime.flatMap(now => expSchedule.run(now, List.fill(5)(())))
// => Chunk(PT1M, PT2M, PT4M, PT8M, PT16M)
```

### Looping and Low-Level Control

These methods give direct access to how a schedule's execution loop behaves.

#### Looping Forever

The instance method `forever` resets `self` to its initial state every time it reaches `Done`, creating an infinite cyclic loop:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def forever: Schedule.WithState[self.State, Env, In, Out]
}
```

This is distinct from the companion `Schedule.forever` value, which is an always-recurring counting schedule. The instance method wraps any finite schedule and makes it repeat cyclically.

```scala mdoc:compile-only
import zio._

// Cycle through 5 retries indefinitely: 0,1,2,3,4,0,1,2,3,4,...
val cyclic: Schedule[Any, Any, Long] = Schedule.recurs(5).forever
```

#### Reconsidering Every Decision

`reconsider` and `reconsiderZIO` intercept every `step` decision and allow overriding both the decision and the output:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def reconsider[Out2](
    f: (State, Out, Decision) => Either[Out2, (Out2, Interval)]
  ): Schedule.WithState[self.State, Env, In, Out2]

  final def reconsiderZIO[Env1 <: Env, In1 <: In, Out2](
    f: (State, Out, Decision) => URIO[Env1, Either[Out2, (Out2, Interval)]]
  ): Schedule.WithState[self.State, Env1, In1, Out2]
}
```

Returning `Left(out2)` means stop; returning `Right((out2, interval))` means continue with the given interval and new output.

:::tip
`reconsider` is a low-level building block. For most use cases, prefer `check` or `whileOutput` to stop on a condition, `addDelay` or `delayed` to adjust timing, and `map` to transform the output. Reach for `reconsider` only when you need simultaneous access to the schedule's raw state, its current output, and its pending decision.
:::

```scala mdoc:compile-only
import zio._

// Stop the schedule early when the output reaches 3, regardless of its own logic
val stopsAt3: Schedule[Any, Any, Long] =
  Schedule.recurs(10).reconsider { (_, out, decision) =>
    decision match {
      case Schedule.Decision.Done          => Left(out)
      case Schedule.Decision.Continue(ivs) =>
        if (out >= 3L) Left(out)
        else Right((out, Schedule.Interval.after(ivs.start)))
    }
  }
```

### Observability and Lifecycle

These methods attach side effects to a schedule without altering its recurrence logic.

#### Tapping Inputs and Outputs

`tapInput` and `tapOutput` run a side-effecting function at each step without changing the schedule's behaviour:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def tapInput[Env1 <: Env, In1 <: In](f: In1 => URIO[Env1, Any]):
    Schedule.WithState[self.State, Env1, In1, Out]

  final def tapOutput[Env1 <: Env](f: Out => URIO[Env1, Any]):
    Schedule.WithState[self.State, Env1, In, Out]
}
```

`tapInput` is useful for logging or recording each input the schedule sees; `tapOutput` covers the computed output side:

```scala mdoc:compile-only
import zio._

// Log each error before retrying
val debugRetry: Schedule[Any, Throwable, Long] =
  Schedule.recurs(5).tapInput[Any, Throwable](e => ZIO.logError(s"Error: ${e.getMessage}"))

// Log each retry count as a step output
val debugSteps: Schedule[Any, Any, Long] =
  Schedule.recurs(5).tapOutput(n => ZIO.logInfo(s"Attempt $n"))
```

#### Observing Decisions

`onDecision` runs a side effect for every decision — both `Continue` and `Done`:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def onDecision[Env1 <: Env](
    f: (State, Out, Decision) => URIO[Env1, Any]
  ): Schedule.WithState[self.State, Env1, In, Out]
}
```

The function receives the current state, the output, and the full `Decision` value, making it suitable for detailed audit logging or metric emission:

```scala mdoc:compile-only
import zio._

// Log the decision at each step without altering schedule behaviour
val observed: Schedule[Any, Any, Long] =
  Schedule.recurs(5).onDecision { case (_, out, decision) =>
    ZIO.logInfo(s"step=$out decision=$decision")
  }
```

#### Finalisation

`ensuring` runs a finalizer when the schedule reaches `Done`:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def ensuring(finalizer: UIO[Any]):
    Schedule.WithState[self.State, Env, In, Out]
}
```

:::caution
`ensuring` fires only when the schedule's decision loop reaches `Done`. If the fiber running the repeat or retry loop is interrupted before the schedule finishes, `finalizer` does not run. For interruption-safe cleanup, use `ZIO#ensuring` at the effect level instead.
:::

```scala mdoc:compile-only
import zio._

// Log a message when the retry schedule is exhausted
val withCleanup: Schedule[Any, Any, Long] =
  Schedule.recurs(5).ensuring(ZIO.logInfo("Schedule exhausted"))
```

### Environment

These methods supply or narrow the environment a schedule requires.

#### Eliminating the Environment

`provideEnvironment` eliminates the schedule's environment requirement by supplying a full `ZEnvironment[Env]`:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def provideEnvironment(env: ZEnvironment[Env]):
    Schedule.WithState[self.State, Any, In, Out]
}
```

#### Narrowing the Environment

`provideSomeEnvironment` narrows the environment via a transformation function, mapping a broader `Env2` down to the required `Env`:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def provideSomeEnvironment[Env2](
    f: ZEnvironment[Env2] => ZEnvironment[Env]
  ): Schedule.WithState[self.State, Env2, In, Out]
}
```

Together, `provideEnvironment` and `provideSomeEnvironment` allow a schedule to be constructed against a service interface and then wired to a concrete implementation at the call site:

```scala mdoc:compile-only
import zio._

trait MyService { def label: String }

// A schedule that requires MyService to log each step
val labeledSteps: Schedule[MyService, Any, Long] =
  Schedule.recurs(5).tapOutput(n =>
    ZIO.serviceWithZIO[MyService](svc => ZIO.logInfo(s"[${svc.label}] step $n"))
  )

// Eliminate the requirement by providing a live instance
val standalone: Schedule[Any, Any, Long] =
  labeledSteps.provideEnvironment(
    ZEnvironment(new MyService { def label = "prod" })
  )
```

### Manual Driving

`driver` produces a `Driver` that lets you step the schedule one input at a time, with the runtime handling the actual sleep between steps:

```scala
trait Schedule[-Env, -In, +Out] { self =>
  final def driver: UIO[Schedule.Driver[self.State, Env, In, Out]]
}
```

Most users interact with a schedule through `ZIO#repeat` or `ZIO#retry`. Call `driver` only when you need direct per-step control over schedule advancement — for example, when building a custom retry loop or integrating with an external event source. See the [`Driver` nested type](#driver) for the complete field-level API.

```scala mdoc:compile-only
import zio._

val manualDriving: ZIO[Any, Nothing, Unit] = for {
  driver <- Schedule.recurs(3).driver
  _      <- driver.next(()).ignore   // advance step 1; ignore Done signal
  _      <- driver.next(()).ignore   // advance step 2
  _      <- driver.last.orDie        // retrieve the last output produced
} yield ()
```

## Nested Types

`Schedule` defines five nested types that appear throughout its API: `WithState`, `Decision`, `Interval`, `Intervals`, and `Driver`.

### `WithState` Type Alias

`WithState` makes the abstract `State` type member visible as a type-level argument:

```scala
object Schedule {
  type WithState[State0, -Env, -In0, +Out0] = Schedule[Env, In0, Out0] { type State = State0 }
}
```

Every companion-object factory and every instance combinator returns a `WithState[S, ...]` where `S` is the concrete state type for that particular schedule. For example, `Schedule.recurs(5)` returns `WithState[Long, Any, Any, Long]` — a `Long` counter is the state. When two schedules are combined, the state becomes a product: `Schedule.recurs(5) && Schedule.spaced(1.second)` returns `WithState[(Long, Long), Any, Any, (Long, Long)]`.

Retaining `WithState` in type signatures lets the compiler verify state compatibility — for example when providing a schedule to `driver` and then reading its state via `driver.state`.

### `Decision`

`Decision` is the type returned (as part of a triple) by `step`. It signals whether the schedule should continue or stop:

```scala
object Schedule {
  sealed trait Decision

  object Decision {
    final case class Continue(interval: Intervals) extends Decision
    object Continue {
      def apply(interval: Interval): Decision = Continue(Intervals(interval))
    }
    case object Done extends Decision
  }
}
```

#### `Decision.Continue`

`Continue(interval: Intervals)` instructs the runtime to sleep until `interval.start` before running the next step. The companion `apply(interval: Interval)` wraps a single `Interval` in an `Intervals` automatically, making it convenient to return a single time window.

#### `Decision.Done`

`Done` instructs the runtime to stop the repeat or retry loop immediately, returning the most recent output as the result of the overall operation.

We can pattern-match on a `Decision` to implement custom logic:

```scala mdoc:compile-only
import zio._
import zio.Schedule.Decision

def describeDecision(d: Decision): String = d match {
  case Decision.Continue(interval) =>
    s"Continue; next step starts at ${interval.start}"
  case Decision.Done =>
    "Finished"
}
```

### `Interval`

`Interval` represents a half-open time interval `[start, end)`:

```scala
object Schedule {
  sealed abstract class Interval private (val start: OffsetDateTime, val end: OffsetDateTime) {
    final def <(that: Interval): Boolean
    final def isEmpty: Boolean
    final def intersect(that: Interval): Interval
    final def max(that: Interval): Interval
    final def min(that: Interval): Interval
    final def nonEmpty: Boolean
    final def size: Duration
  }

  object Interval {
    def apply(start: OffsetDateTime, end: OffsetDateTime): Interval
    def after(start: OffsetDateTime): Interval
    def before(end: OffsetDateTime): Interval
    val empty: Interval
  }
}
```

`Interval.after(start)` creates an interval with no upper bound — used by schedules that always continue. `Interval.before(end)` creates an interval with no lower bound. `Interval.empty` has `start == end`. The `apply` constructor canonicalises: if `start > end`, the result is `empty`. A zero-width interval where `start == end` is valid and is not collapsed to `empty`.

| Method                 | Description                                                                 |
|------------------------|-----------------------------------------------------------------------------|
| `size: Duration`       | Width of the interval as a nanosecond-precise `Duration`                    |
| `intersect(that)`      | Overlapping sub-interval; `empty` if the two intervals do not overlap       |
| `min(that)`            | The interval whose end comes first                                          |
| `max(that)`            | The interval whose start comes last                                         |
| `<(that): Boolean`     | `true` if `self` ends before `that` starts                                  |

### `Intervals`

`Intervals` is a sorted, non-overlapping set of `Interval` values. `Decision.Continue` carries an `Intervals` rather than a bare `Interval` to support schedules that identify multiple valid recurrence windows simultaneously:

```scala
object Schedule {
  sealed abstract case class Intervals private (intervals: List[Interval]) {
    def &&(that: Intervals): Intervals
    def ||(that: Intervals): Intervals
    def union(that: Intervals): Intervals
    def intersect(that: Intervals): Intervals
    def start: OffsetDateTime
    def end: OffsetDateTime
    def <(that: Intervals): Boolean
    def nonEmpty: Boolean
    def max(that: Intervals): Intervals
  }

  object Intervals {
    def apply(intervals: Interval*): Intervals
    val empty: Intervals
  }
}
```

`Intervals.start` returns the start of the earliest contained interval; `Intervals.end` returns the end of the earliest contained interval. `&&` computes a geometric intersection; `||` computes a geometric union. The `intersectWith` and `unionWith` instance methods on `Schedule` delegate directly to these operators.

### `Driver`

`Driver` is the low-level handle returned by `schedule.driver`. It lets you advance the schedule one step at a time:

```scala
object Schedule {
  final case class Driver[+State, -Env, -In, +Out](
    next: In => ZIO[Env, None.type, Out],
    last: IO[NoSuchElementException, Out],
    reset: UIO[Unit],
    state: UIO[State]
  )
}
```

Each field serves a distinct role:

- `next(in)` — advance the schedule by one step for the given input. Fails with `None` (type `None.type`) when the schedule is done.
- `last` — retrieve the most recent output produced by `next`. Fails with `NoSuchElementException` if `next` has never succeeded.
- `reset` — return the schedule to its `initial` state, discarding all accumulated state.
- `state` — read the current internal state as a `UIO[State]` without advancing the schedule.

We can use a `Driver` to build a custom retry loop:

```scala mdoc:compile-only
import zio._

def customRetryLoop[R, E, A](
  effect: ZIO[R, E, A],
  schedule: Schedule[R, E, Long]
): ZIO[R, E, A] =
  schedule.driver.flatMap { driver =>
    def loop: ZIO[R, E, A] =
      effect.foldZIO(
        failure = e =>
          driver.next(e).foldZIO(
            _ => ZIO.fail(e),   // schedule done — re-raise the last error
            _ => loop           // schedule continues — retry
          ),
        success = a => ZIO.succeed(a)
      )
    loop
  }
```

:::note
The `state` field was added to `Driver` after the initial ZIO 2.0 release. If you encounter an early ZIO 2 RC build that lacks this field, updating to ZIO 2.1.x will restore it.
:::

## Integration

`Schedule` integrates with `ZIO` through three families of methods: `repeat*`, `retry*`, and `schedule*`. The type parameters of the schedule must align with those of the effect:

| ZIO method              | Schedule parameter       | Mapping                                                                            |
|-------------------------|--------------------------|------------------------------------------------------------------------------------|
| `repeat(s)`             | `Schedule[R1, A, B]`     | `In = A`: the schedule observes the effect's *success* value                       |
| `repeat(s)`             | `Schedule[R1, A, B]`     | `Out = B`: the final result of `repeat`                                            |
| `retry(policy)`         | `Schedule[R1, E, S]`     | `In = E`: the schedule observes the effect's *error* value                         |
| `retry(policy)`         | `Schedule[R1, E, S]`     | `Out = S`: the schedule output (discarded; last error is re-raised on `Done`)      |
| `retryOrElse(policy, f)` | `Schedule[R1, E, S]`    | `S` is passed to `f` as the last schedule output                                   |
| `schedule(s)`           | `Schedule[R1, Any, B]`   | `In = Any`: the effect's own output is ignored                                     |

Both `ZIO#repeat` and `ZIO#retry` run the effect *first*, then consult the schedule. "Once" means one *additional* execution after the first.

### Repeating Effects — `ZIO#repeat`

These ten methods run the effect repeatedly according to a schedule or inline condition:

```scala
trait ZIO[-R, +E, +A] { self =>
  final def repeat[R1 <: R, B](schedule: => Schedule[R1, A, B])(implicit
    trace: Trace
  ): ZIO[R1, E, B]

  final def repeatN(n: => Int): ZIO[R, E, A]

  final def repeatOrElse[R1 <: R, E2, B](
    schedule: => Schedule[R1, A, B],
    orElse: (E, Option[B]) => ZIO[R1, E2, B]
  ): ZIO[R1, E2, B]

  final def repeatOrElseEither[R1 <: R, B, E2, C](
    schedule0: => Schedule[R1, A, B],
    orElse: (E, Option[B]) => ZIO[R1, E2, C]
  ): ZIO[R1, E2, Either[C, B]]

  final def repeatUntil(p: A => Boolean): ZIO[R, E, A]
  final def repeatUntilEquals[A1 >: A](a: => A1): ZIO[R, E, A1]
  final def repeatUntilZIO[R1 <: R](f: A => URIO[R1, Boolean]): ZIO[R1, E, A]

  final def repeatWhile(p: A => Boolean): ZIO[R, E, A]
  final def repeatWhileEquals[A1 >: A](a: => A1): ZIO[R, E, A1]
  final def repeatWhileZIO[R1 <: R](f: A => URIO[R1, Boolean]): ZIO[R1, E, A]
}
```

`repeat(schedule)` repeats until the schedule stops, returning the final schedule output `B`. The first effect failure terminates the loop immediately. `repeatN(n)` repeats `n` additional times without a schedule, returning the last effect output `A`. `repeatOrElse(schedule, orElse)` calls `orElse(error, lastOutput)` on the first effect failure — `Option[B]` is `None` if the schedule has not yet produced any output. `repeatOrElseEither` returns `Either[C, B]` to distinguish the fallback path (`Left(c)`) from the success path (`Right(b)`). `repeatUntil(p)` repeats until the success value satisfies `p`. `repeatWhile(p)` repeats while `p` holds. The `ZIO` variants accept effectful predicates.

```scala mdoc:compile-only
import zio._

val tick: ZIO[Any, Nothing, Unit] = ZIO.logInfo("tick")

// Repeat 5 additional times, returning the last count output (4L)
val fiveTicks: ZIO[Any, Nothing, Long] =
  tick.repeat(Schedule.recurs(5))

// Repeat at most 5 times with exponential backoff; output is (count, delay)
val scheduled: ZIO[Any, Nothing, (Long, Duration)] =
  tick.repeat(Schedule.recurs(5) && Schedule.exponential(100.millis))

// Repeat a failing effect, recovering on exhaustion
val withFallback: ZIO[Any, Nothing, Long] =
  ZIO.fail("oops").repeatOrElse(
    Schedule.recurs(3),
    (err: String, lastOut: Option[Long]) => ZIO.succeed(lastOut.getOrElse(-1L))
  )
```

### Retrying Effects — `ZIO#retry`

These ten methods retry the effect when it fails, driving a schedule with the error value:

```scala
trait ZIO[-R, +E, +A] { self =>
  final def retry[R1 <: R, S](
    policy: => Schedule[R1, E, S]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E, A]

  final def retryN(n: => Int)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E, A]

  final def retryOrElse[R1 <: R, A1 >: A, S, E1](
    policy: => Schedule[R1, E, S],
    orElse: (E, S) => ZIO[R1, E1, A1]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E1, A1]

  final def retryOrElseEither[R1 <: R, Out, E1, B](
    schedule0: => Schedule[R1, E, Out],
    orElse: (E, Out) => ZIO[R1, E1, B]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E1, Either[B, A]]

  final def retryUntil(f: E => Boolean)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E, A]
  final def retryUntilEquals[E1 >: E](e: => E1)(implicit ev: CanFail[E1], trace: Trace): ZIO[R, E1, A]
  final def retryUntilZIO[R1 <: R](
    f: E => URIO[R1, Boolean]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E, A]

  final def retryWhile(f: E => Boolean)(implicit ev: CanFail[E], trace: Trace): ZIO[R, E, A]
  final def retryWhileEquals[E1 >: E](e: => E1)(implicit ev: CanFail[E1], trace: Trace): ZIO[R, E1, A]
  final def retryWhileZIO[R1 <: R](
    f: E => URIO[R1, Boolean]
  )(implicit ev: CanFail[E], trace: Trace): ZIO[R1, E, A]
}
```

`retry(policy)` retries on every failure according to `policy`, re-raising the last error when `policy` stops. `retryN(n)` retries up to `n` times without a schedule. `retryOrElse(policy, orElse)` calls `orElse(lastError, lastScheduleOutput)` when the schedule is exhausted. `retryOrElseEither` returns `Either[B, A]`, distinguishing the fallback path (`Left(b)`) from eventual success (`Right(a)`). The `Until` and `While` variants stop retrying once the error satisfies (or no longer satisfies) an inline predicate — no schedule object needed.

:::note
All `retry*` methods require an implicit `CanFail[E]`, which prevents calling them on effects with error type `Nothing`. An effect typed `ZIO[R, Nothing, A]` cannot fail, so retrying it would be a compile error.
:::

```scala mdoc:compile-only
import zio._

val flaky: ZIO[Any, String, Int] = ZIO.fail("transient error")

// Retry with exponential backoff and full jitter, stopping after 1 minute
val resilient: ZIO[Any, String, Int] =
  flaky.retry(
    Schedule.exponential(100.millis).jittered(0.0, 1.0).upTo(1.minute)
  )

// Retry 3 times, then run a fallback
val withFallback: ZIO[Any, Nothing, Int] =
  flaky.retryOrElse(
    Schedule.recurs(3),
    (err: String, _: Long) => ZIO.logError(s"Gave up: $err").as(-1)
  )

// Retry only while the error message indicates a transient condition
val selectiveRetry: ZIO[Any, String, Int] =
  flaky.retryWhile(_.startsWith("transient"))
```

### Scheduling Effects — `ZIO#schedule`

These three methods run an effect on a schedule where the effect's output is irrelevant to the scheduling decision:

```scala
trait ZIO[-R, +E, +A] { self =>
  final def schedule[R1 <: R, B](schedule: => Schedule[R1, Any, B])(implicit
    trace: Trace
  ): ZIO[R1, E, B]

  final def scheduleFrom[R1 <: R, A1 >: A, B](a: => A1)(
    schedule0: => Schedule[R1, A1, B]
  ): ZIO[R1, E, B]

  final def scheduleFork[R1 <: R, B](schedule: => Schedule[R1, Any, B])(implicit
    trace: Trace
  ): ZIO[R1 with Scope, Nothing, Fiber.Runtime[E, B]]
}
```

`schedule(s)` runs the effect according to `s`, discarding the effect's output (the schedule takes `Any` as input) and returning the last schedule output `B`. `scheduleFrom(a)(s)` is similar but provides an explicit initial value `a` that the *first* schedule step receives, allowing the schedule's initial decision to depend on a prior result. `scheduleFork(s)` runs the schedule in a new fiber attached to the current `Scope` — the fiber terminates when the scope closes.

```scala mdoc:compile-only
import zio._

val sideEffect: ZIO[Any, Nothing, Unit] = ZIO.logInfo("tick")

// Run the side effect every second, returning the last count output
val ticker: ZIO[Any, Nothing, Long] =
  sideEffect.schedule(Schedule.spaced(1.second))

// Run in a forked fiber scoped to the enclosing resource scope
val forked: ZIO[Scope, Nothing, Fiber.Runtime[Nothing, Long]] =
  sideEffect.scheduleFork(Schedule.spaced(1.second))
```
