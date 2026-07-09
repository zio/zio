---
id: schedule
title: "Schedule Step by Step — Retry and Repeat Policies in ZIO"
sidebar_label: "Retry and Repeat Policies with Schedule"
description: "Learn how ZIO's Schedule works as a pure state machine, and build retry and repeat policies from primitives, composition, and the Driver API."
keywords: ["Schedule Step Function", "Exponential Backoff", "Schedule Composition", "Retry Policy", "Schedule Driver API", "Repeat Policy", "Schedule Primitives"]
---
## Introduction

`Schedule[-Env, -In, +Out]` is ZIO's composable policy for recurring effects. A schedule does not schedule threads or set timers — it is a pure step function that, given the current time and the latest value from your effect, returns exactly three things: a fresh internal state, an output value, and a `Decision` that is either `Continue(interval)` (sleep until `interval.start`, then run again) or `Done` (stop).

The ZIO runtime reads each `Decision` and sleeps accordingly. The schedule stays purely functional; no mutable state or threads are involved until a `Driver` interprets it inside a running fiber.

We start in Section 1 by putting schedules to work right away with `.repeat` and `.retry`. Then we peel back the layers one at a time: the step-function contract, primitive factory methods, output transformations, binary composition, and finally the `Driver` API for manual orchestration.

**By the end of this tutorial you will be able to:**
- Explain the three-part `step` contract and trace a schedule's execution by hand
- Use `Schedule.recurs`, `Schedule.spaced`, `Schedule.exponential`, `Schedule.fixed`, and `Schedule.forever` as standalone policies
- Transform schedule output with `Schedule#map`, `Schedule#as`, and `Schedule#collectAll`
- Compose two schedules with `&&`, `||`, and `++` to express real-world policies
- Drive a schedule manually through the `Driver` API to observe each step without sleeping

**Prerequisites:** Familiarity with `ZIO[R, E, A]`, `flatMap`, `map`, `orDie`, `ZIO.succeed`, `ZIO.fail`, and the conceptual meaning of `ZIO.repeat` and `ZIO.retry`.

---

## 1. Using a Schedule with repeat and retry

A `Schedule` value plugs directly into `ZIO#repeat` or `ZIO#retry`. The runtime takes care of sleeping and looping; all we provide is the policy.

`ZIO#repeat(schedule)` runs the effect once immediately, then checks the schedule after each success. It keeps repeating as long as the schedule says `Continue`, and returns the schedule's last output value when the schedule says `Done`:

```scala mdoc:compile-only
import zio._

object HelloRepeat extends ZIOAppDefault {
  def run =
    ZIO.unit                           // the effect to repeat
      .repeat(Schedule.recurs(4))      // repeat 4 additional times = 5 runs total
      .flatMap(n => Console.printLine(s"Final schedule output: $n"))
      .orDie
}
```

Running `HelloRepeat` prints:

```
Final schedule output: 4
```

The value `4` is the last output emitted by `Schedule.recurs(4)` — the 0-based index of the final step. The effect itself ran five times: once initially and four more times directed by the schedule.

We can make that run-count concrete by using a `Ref` as a counter:

```scala mdoc:compile-only
import zio._

object RepeatCounter extends ZIOAppDefault {
  def run = for {
    counter <- Ref.make(0)              // starts at zero
    _       <- counter.update(_ + 1)   // increments by one on every run
                 .repeat(Schedule.recurs(3))
    count   <- counter.get
    _       <- Console.printLine(s"Effect ran $count times")
  } yield ()
  // 1 initial run + 3 repeats = 4 total
}
```

```
Effect ran 4 times
```

`ZIO#retry(policy)` works in the other direction: the schedule receives the *error* value after each failure, and the runtime retries as long as the policy says `Continue`. We can observe this with an always-failing effect:

```scala mdoc:compile-only
import zio._

object RetryOnce extends ZIOAppDefault {
  // Increments a counter, then fails with the new count as the error message
  def alwaysFail(ref: Ref[Int]): IO[String, Nothing] =
    ref.updateAndGet(_ + 1).flatMap(n => ZIO.fail(s"Error: $n"))

  def run = for {
    ref    <- Ref.make(0)
    result <- alwaysFail(ref)
                .retry(Schedule.once)              // allow exactly 1 retry
                .catchAll(err => ZIO.succeed(err)) // capture the final error
    _      <- Console.printLine(result)
  } yield ()
}
```

```
Error: 2
```

`Schedule.once` permits one retry, so the effect runs twice in total. The first attempt produces `"Error: 1"` and the second `"Error: 2"`. Since both fail, the final error is `"Error: 2"`.

:::tip
`ZIO#repeat` consumes effect *successes*; `ZIO#retry` consumes effect *errors*. The schedule's `In` type must match whichever channel it reads.
:::

---

## 2. The step-function contract: initial, step, and Decision

Every `Schedule` is a pure state machine defined by exactly two abstract members:

```scala
trait Schedule[-Env, -In, +Out] extends Serializable {
  type State
  def initial: State
  def step(now: OffsetDateTime, in: In, state: State)(implicit trace: Trace)
    : ZIO[Env, Nothing, (State, Out, Decision)]
}
```

`initial` provides the seed value for the opaque `State` type. `step` takes three arguments — the current wall-clock time, the latest input from the effect, and the current state — and returns a `ZIO` that produces a three-element tuple: the next state, an output value, and a `Decision`.

`Decision` is a sealed ADT with two cases:

```scala
sealed trait Decision

object Decision {
  final case class Continue(interval: Intervals) extends Decision
  case object Done                               extends Decision
}
```

`Continue(interval)` tells the runtime to sleep until `interval.start` and then run the effect again, passing the new state to the next `step` call. `Done` tells the runtime to stop and return the schedule's last output.

To see the step function in action without any real sleeping, every `Schedule` exposes a `run` method that simulates steps using the `interval.start` of each `Continue` as the `now` for the next call:

```scala
trait Schedule[-Env, -In, +Out] extends Serializable {
  final def run(now: OffsetDateTime, input: Iterable[In]): URIO[Env, Chunk[Out]]
}
```

`run` does not sleep — it steps through the schedule as quickly as the list of inputs allows, collecting each output into a `Chunk`. This makes it ideal for understanding what a schedule produces.

We can build a schedule from scratch using `Schedule.unfold`, which constructs a state machine from an initial value and a transition function:

```scala
object Schedule {
  def unfold[A](a: => A)(f: A => A): Schedule.WithState[A, Any, Any, A]
}
```

Each call to `step` emits the current state as the output, then advances to `f(state)`, and always continues:

```scala mdoc:compile-only
import zio._

object StepContract extends ZIOAppDefault {
  // A schedule that emits 1, 2, 4, 8, 16, ... (doubling state)
  val doublingSchedule: Schedule[Any, Any, Long] =
    Schedule.unfold(1L)(_ * 2)    // initial = 1; each step: new state = state * 2

  def run = for {
    now    <- Clock.currentDateTime
    // run simulates 5 steps without sleeping
    output <- doublingSchedule.run(now, List.fill(5)(()))
    _      <- Console.printLine(output)
  } yield ()
}
```

```
Chunk(1,2,4,8,16)
```

The five inputs are consumed one per step. Each step emits the *current* state (before the doubling), so the outputs are 1, 2, 4, 8, 16. Because `Schedule.unfold` always returns `Continue`, `Schedule#run` stops only when it exhausts the input list.



Everything in `Schedule`'s companion object — `Schedule.recurs`, `Schedule.exponential`, `Schedule.spaced`, `Schedule.forever` — is built by composing `Schedule.unfold` with small transformations. Understanding `Schedule.unfold` means we can read any schedule's source and reason about its behaviour.

---

## 3. Primitive factory methods: recurs, spaced, exponential, forever, and friends

The `Schedule` companion object provides ready-made schedules for the most common policies. All of them are defined in terms of `unfold`, `forever`, and delay helpers, so they share the same three-part contract we just studied.

Here are the most frequently used factory methods along with what they emit and when they stop:

```scala mdoc:compile-only
import zio._

// recurs(n): repeat exactly n additional times; emits 0-based step index
val thrice: Schedule[Any, Any, Long] =
  Schedule.recurs(3)            // emits 0, 1, 2 (3 steps), then Done

// forever: repeat without bound; emits the step index
val always: Schedule[Any, Any, Long] =
  Schedule.forever              // emits 0, 1, 2, 3, ... indefinitely

// once: repeat exactly once; emits Unit
val one: Schedule[Any, Any, Unit] =
  Schedule.once                 // emits () once, then Done

// stop: never recur; emits Unit and immediately Done
val noop: Schedule[Any, Any, Unit] =
  Schedule.stop

// spaced(d): repeat with a fixed gap of d after each run; emits the step index
val everySecond: Schedule[Any, Any, Long] =
  Schedule.spaced(1.second)     // gap = 1 second between runs

// fixed(d): wall-clock-aligned window; does not pile up if the effect runs late
val onTheClock: Schedule[Any, Any, Long] =
  Schedule.fixed(10.seconds)    // emits 0, 1, 2, ... on each 10-second window

// exponential(base, factor): delay grows as base * factor^n; emits the delay Duration
val backoff: Schedule[Any, Any, Duration] =
  Schedule.exponential(100.millis)   // 100ms, 200ms, 400ms, 800ms, ...

// fibonacci(one): delay follows the fibonacci sequence; emits the delay Duration
val fibBackoff: Schedule[Any, Any, Duration] =
  Schedule.fibonacci(100.millis)     // 100ms, 100ms, 200ms, 300ms, 500ms, ...
```

`Schedule.exponential(base, factor)` is defined as:

```scala
object Schedule {
  def exponential(base: Duration, factor: Double = 2.0): Schedule.WithState[Long, Any, Any, Duration] =
    delayed[Any, Any](forever.map(i => base * math.pow(factor, i.doubleValue)))
}
```

It maps the `forever` step index through the exponential formula and feeds the resulting duration to `delayed`, which converts output durations into actual sleep intervals. The output emitted at each step is the delay *for that step*, not the cumulative time.

We can confirm the doubling sequence using `schedule.run`:

```scala mdoc:compile-only
import zio._

object ExponentialDemo extends ZIOAppDefault {
  def run = for {
    now    <- Clock.currentDateTime
    delays <- Schedule.exponential(1.minute).run(now, List.fill(5)(()))
    _      <- Console.printLine(delays.map(_.render).mkString(", "))
  } yield ()
}
```

```
1 m, 2 m, 4 m, 8 m, 16 m
```

Each element is the delay that would be slept before the corresponding repetition. The `Schedule#run` method advances the simulated clock by each interval without actually sleeping, so this completes instantly regardless of the durations involved.

:::note
`Schedule.spaced(d)` adds a fixed gap *after* each run, measuring from the moment the effect finishes. `Schedule.fixed(d)` aligns to wall-clock windows: if an effect runs long and misses its window, it does not pile up extra runs — it simply waits for the next window boundary.
:::

---

## 4. Transforming schedule output with Schedule#map, Schedule#as, and Schedule#collectAll

Because `Out` is just the second element of the tuple returned by `Schedule#step`, we can transform it with the same combinators we use on any functor: `Schedule#map`, `Schedule#as`, `Schedule#fold`, and `Schedule#collectAll`. These transformations affect only what the schedule *emits* — they do not change when or whether it continues.

`Schedule#map` applies a function to every output value:

```scala mdoc:compile-only
import zio._

object MapDemo extends ZIOAppDefault {
  // Turn the raw step index into a human-readable message
  val labeled: Schedule[Any, Any, String] =
    Schedule.recurs(3).map(n => s"attempt ${n + 1} of 4")

  def run = for {
    now    <- Clock.currentDateTime
    labels <- labeled.run(now, List.fill(4)(()))
    _      <- ZIO.foreach(labels)(Console.printLine(_))
  } yield ()
}
```

```
attempt 1 of 4
attempt 2 of 4
attempt 3 of 4
attempt 4 of 4
```

`Schedule#as` replaces every output with a constant value, which is useful when the step index carries no meaning in your domain:

```scala mdoc:compile-only
import zio._

// Retry up to 5 times; the schedule output is () rather than a raw Long
val silentRetry: Schedule[Any, Any, Unit] =
  Schedule.recurs(5).as(())
```

`Schedule#collectAll` accumulates *every* output into a `Chunk`. The schedule continues for as long as the underlying schedule would continue, and returns all outputs together as a single `Chunk` once it is done:

```scala mdoc:compile-only
import zio._

object CollectAllDemo extends ZIOAppDefault {
  def run =
    ZIO.unit
      .repeat(Schedule.recurs(5).collectAll)
      .flatMap(chunk => Console.printLine(chunk))
      .orDie
}
```

```
Chunk(0,1,2,3,4,5)
```

`Schedule.recurs(5)` emits the step index 0 through 5 (because `Schedule.recurs(n)` uses `Schedule.forever.whileOutput(_ < n)` and the final output at the `Done` step is `n`). `Schedule#collectAll` gathers all six outputs into a single `Chunk` that `ZIO#repeat` returns.

:::tip
`Schedule#delays` and `Schedule#repetitions` are specialized aliases: `Schedule#delays` converts a schedule's output to the delay `Duration` between steps, and `Schedule#repetitions` converts it to the number of times the schedule has continued so far.
:::

---

## 5. Composing two schedules with &&, ||, and ++

The real power of `Schedule` emerges when we combine two policies. ZIO provides three primary binary composition operators, each merging the `Decision` values from both schedules in a different way.

### Intersection with `&&`

`s1 && s2` continues only when *both* schedules say `Continue`, taking the *later* of the two start intervals. The output is a tuple `(out1, out2)`. If either schedule says `Done`, the combined schedule also says `Done`:

```scala mdoc:compile-only
import zio._

object IntersectionDemo extends ZIOAppDefault {
  // Exponential backoff, capped at 3 additional retries
  val cappedBackoff: Schedule[Any, Any, (Duration, Long)] =
    Schedule.exponential(1.minute) && Schedule.recurs(2)

  def run = for {
    now    <- Clock.currentDateTime
    output <- cappedBackoff.run(now, 1 to 10)
    _      <- ZIO.foreachDiscard(output) { case (d, n) =>
                Console.printLine(s"step $n: delay ${d.render}")
              }
  } yield ()
}
```

```
step 0: delay 1 m
step 1: delay 2 m
step 2: delay 4 m
```

Although we supplied 10 inputs, `Schedule.recurs(2)` fires `Done` after its third step (outputs 0, 1, 2). The intersection respects that limit, stopping after exactly 3 elements even though `exponential` would continue indefinitely. This is the standard pattern for "exponential backoff with a maximum retry count."

### Union with `||`

`s1 || s2` continues when *either* schedule says `Continue`, taking the *earlier* of the two start intervals. The output is again a tuple `(out1, out2)`. The combined schedule only stops when *both* say `Done`:

```scala mdoc:compile-only
import zio._

// Keep backing off exponentially OR at most every 30 seconds — whichever fires sooner
val boundedExp: Schedule[Any, Any, (Duration, Long)] =
  Schedule.exponential(500.millis) || Schedule.spaced(30.seconds)
```

`boundedExp` never waits longer than 30 seconds between retries because `spaced(30.seconds)` always fires with a 30-second interval. The exponential component dominates the early steps (500ms, 1s, 2s, …) and the spaced component kicks in as a ceiling once the exponential delay exceeds 30 seconds.

### Sequencing with `++`

`s1 ++ s2` (also spelled `s1.andThen(s2)`) runs the first schedule to completion, then hands control to the second. The output type is the wider of the two `Out` types:

```scala mdoc:compile-only
import zio._

// Retry 3 times immediately, then switch to a 5-second cadence for up to 10 more attempts
val multiPhase: Schedule[Any, Any, Long] =
  Schedule.recurs(3) ++ (Schedule.spaced(5.seconds) && Schedule.recurs(9)).map(_._2)
```

We can verify sequencing behaviour by running a combined schedule and observing the total step count:

```scala mdoc:compile-only
import zio._

object AndThenDemo extends ZIOAppDefault {
  // 3 immediate steps (recurs(2)), then 3 spaced steps ((spaced && recurs(2)).map)
  val twoFastThenThreeSlow: Schedule[Any, Any, Long] =
    Schedule.recurs(2) ++ (Schedule.spaced(1.second) && Schedule.recurs(2)).map(_._2)

  def run = for {
    now    <- Clock.currentDateTime
    output <- twoFastThenThreeSlow.run(now, 1 to 10)
    _      <- Console.printLine(s"Total steps: ${output.length}")
  } yield ()
}
```

```
Total steps: 6
```

The first phase (`recurs(2)`) contributes 2 steps with no delay. The second phase (`spaced(1.second) && recurs(3)`) contributes 3 steps with a 1-second gap each. Together the schedule runs exactly 5 additional repetitions before stopping.

### Piping with `>>>`

`s1 >>> s2` pipes the *output* of `s1` as the *input* to `s2`. This is less common than the three operators above but is useful when the output of one schedule drives the logic of another:

```scala mdoc:compile-only
import zio._

// Measure cumulative time elapsed across all retry delays
val cumulativeDelay: Schedule[Any, Any, Duration] =
  Schedule.exponential(1.second) >>> Schedule.elapsed
```

`Schedule.elapsed` emits the total elapsed time since the first step. By piping the exponential delay output into `Schedule.elapsed`, we get a schedule whose output is the running sum of all delays so far.

---

## 6. Driving a schedule manually with the Driver API

Every `Schedule` can produce a `Driver` — a stateful, effectful runner that exposes one step at a time. `ZIO.repeat` and `ZIO.retry` use `Driver` internally, and we can use it directly for testing or for building custom orchestration logic.

We obtain a `Driver` by calling `Schedule#driver`, which returns a `UIO[Schedule.Driver[State, Env, In, Out]]`:

```scala
final case class Driver[+State, -Env, -In, +Out](
  next  : In => ZIO[Env, None.type, Out],  // advance one step; fails with None when Done
  last  : IO[NoSuchElementException, Out], // retrieve the most recent output
  reset : UIO[Unit],                       // restore initial state
  state : UIO[State]                       // inspect the current state
)
```

`Driver#next(in)` does everything in one effect: it calls `Schedule#step`, sleeps for the interval if the decision is `Continue`, and returns the output. When the schedule returns `Done`, `Driver#next` stores the final output in `Driver#last` and then *fails* with `None.type`. This failure is how the driver signals that the schedule is exhausted.

Here we drive `Schedule.recurs(3)` step by step. Because `Schedule.recurs` uses zero-duration intervals, no real sleeping occurs, so we do not need `TestClock`:

```scala mdoc:compile-only
import zio._

object DriverDemo extends ZIOAppDefault {
  def run = for {
    driver <- Schedule.recurs(3).driver     // acquire the stateful runner

    // Three successful steps: next returns the step index (0, 1, 2)
    out0   <- driver.next(())
    _      <- Console.printLine(s"Step 0 → $out0")

    out1   <- driver.next(())
    _      <- Console.printLine(s"Step 1 → $out1")

    out2   <- driver.next(())
    _      <- Console.printLine(s"Step 2 → $out2")

    // The fourth step: whileOutput(_ < 3) fires Done because 3 < 3 = false.
    // next fails with None; last holds the output produced at the Done step.
    _      <- driver.next(()).catchAll { _ =>
                driver.last.flatMap(n => Console.printLine(s"Done  → last output was $n"))
              }
  } yield ()
}
```

```
Step 0 → 0
Step 1 → 1
Step 2 → 2
Done  → last output was 3
```

The first three calls to `driver.next(())` succeed because `Schedule.forever.whileOutput(_ < 3)` emits 0, 1, and 2 with `Continue` decisions. On the fourth call, `Schedule.forever` would emit 3, but `whileOutput(_ < 3)` sees `3 < 3 = false` and changes the `Continue` to `Done`. The driver stores output `3` in `Driver#last` and returns a failing effect.

`Driver#reset` restores `Schedule#initial` so we can replay the schedule from the beginning — useful in tests where we want to reuse the same driver across multiple assertions.

:::tip
`Schedule#run(now, inputs)` (the pure simulation method from Section 2) is equivalent to manually driving a schedule with a `Driver` and ignoring the sleep intervals. Use `Schedule#run` for unit tests on schedule output; use `Driver` when you need full control over the effect loop, such as streaming results or integrating with external rate-limiters.
:::

---

## 7. Putting It All Together

We now have everything we need to write a realistic HTTP retry policy. The scenario: an HTTP endpoint is flaky and fails the first three attempts. We want to retry with exponential backoff, but cap the number of extra attempts at five. Each failure should be logged before the next attempt:

```scala mdoc:compile-only
import zio._

object HttpRetryApp extends ZIOAppDefault {

  // Simulates an HTTP request: fails the first three attempts, then succeeds
  def httpRequest(counter: Ref[Int]): IO[String, String] =
    counter.updateAndGet(_ + 1).flatMap { attempt =>
      if (attempt <= 3) ZIO.fail(s"503 Service Unavailable (attempt $attempt)")
      else ZIO.succeed("200 OK")
    }

  // Policy: exponential backoff with a hard cap of 5 additional attempts.
  // && requires both schedules to say Continue; the earlier to stop wins.
  // The output is (Duration, Long) — the delay and the retry count.
  val retryPolicy: Schedule[Any, Any, (Duration, Long)] =
    Schedule.exponential(100.millis) && Schedule.recurs(5)

  def run = for {
    counter <- Ref.make(0)
    result  <- httpRequest(counter)
                 .tapError(err => Console.printLine(s"  Request failed: $err"))
                 .retry(retryPolicy)
                 .catchAll(err => ZIO.succeed(s"Exhausted retries: $err"))
    _       <- Console.printLine(s"Final result: $result")
  } yield ()
}
```

```
  Request failed: 503 Service Unavailable (attempt 1)
  Request failed: 503 Service Unavailable (attempt 2)
  Request failed: 503 Service Unavailable (attempt 3)
Final result: 200 OK
```

Walking through the moving parts:

- `httpRequest(counter)` fails with a `String` error for the first three calls and succeeds on the fourth.
- `ZIO#tapError(...)` logs each failure *before* the retry decision, so the log line appears immediately when the request fails rather than after any backoff delay.
- `retryPolicy` is built from two schedules intersected with `&&`. `Schedule.exponential(100.millis)` contributes the delay: 100 ms, 200 ms, 400 ms, 800 ms, and 1 600 ms. `Schedule.recurs(5)` contributes the count: stop after 5 retries. Because the request succeeds on attempt 4, only 3 retries are actually needed.
- `.catchAll(...)` converts a terminal failure — when both the initial attempt and all retries are exhausted — into a plain success value, so the program does not crash.

To add jitter (randomized delay scaling, which reduces retry storms in distributed systems), replace `Schedule.exponential(100.millis)` with `Schedule.exponential(100.millis).jittered`, which multiplies each delay by a random factor between 0.8 and 1.2:

```scala mdoc:compile-only
import zio._

// Same policy with ±20% jitter applied to every exponential delay
val jitteredPolicy: Schedule[Any, Any, (Duration, Long)] =
  Schedule.exponential(100.millis).jittered && Schedule.recurs(5)
```

`Schedule#jittered` internally uses `zio.Random` to generate the scaling factor, which is why the `Env` type parameter of the schedule remains `Any` (Random is part of the default ZIO environment).

---

## 8. Running the Examples

The companion source files for this tutorial live in the `zio-examples/schedule-basics` directory of the ZIO repository. Clone the repository and change into that directory:

```bash
git clone https://github.com/zio/zio.git
cd zio/zio-examples/schedule-basics
```

To compile all examples without running them:

```bash
sbt compile
```

To run each example individually from the sbt shell:

```bash
sbt "runMain schedulebasics.HelloRepeat"
sbt "runMain schedulebasics.RepeatCounter"
sbt "runMain schedulebasics.RetryOnce"
sbt "runMain schedulebasics.StepContract"
sbt "runMain schedulebasics.ExponentialDemo"
sbt "runMain schedulebasics.MapDemo"
sbt "runMain schedulebasics.CollectAllDemo"
sbt "runMain schedulebasics.IntersectionDemo"
sbt "runMain schedulebasics.AndThenDemo"
sbt "runMain schedulebasics.DriverDemo"
sbt "runMain schedulebasics.HttpRetryApp"
```

`Schedule` is part of ZIO core and requires no extra dependency. Add ZIO core to your own project with:

```sbt
libraryDependencies += "dev.zio" %% "zio" % "@VERSION@"
```

---

## 9. What You've Learned

This tutorial traced a complete path from "use a schedule" to "understand how a schedule works internally":

- **The step-function contract.** Every `Schedule` is a pure state machine with `Schedule#initial: State` and `Schedule#step(now, in, state): ZIO[Env, Nothing, (State, Out, Decision)]`. The runtime reads each `Decision` and sleeps; the schedule never touches threads.
- **Primitive factories.** `Schedule.recurs(n)`, `Schedule.spaced(d)`, `Schedule.exponential(base)`, `Schedule.fixed(d)`, `Schedule.forever`, `Schedule.once`, and `Schedule.stop` are all built from `Schedule.unfold` and delay helpers. Reading their implementations in terms of the step contract is now straightforward.
- **Output transformation.** `Schedule#map`, `Schedule#as`, and `Schedule#collectAll` transform `Out` without affecting when or whether the schedule continues. `Schedule#collectAll` gathers every per-step output into a `Chunk` returned at the end.
- **Binary composition.** `&&` intersects two schedules (both must agree to continue; later interval wins). `||` unions them (either can continue; earlier interval wins). `++` sequences them (first runs to Done, then second takes over). `>>>` pipes the output of one schedule as the input of another.
- **The Driver API.** `Schedule#driver` returns a `UIO[Driver[State, Env, In, Out]]` whose `Driver#next(in)` steps once — sleeping inside the ZIO runtime — and fails with `None.type` when the schedule is exhausted. `ZIO.repeat` and `ZIO.retry` use this same API internally.

---

## 10. Where to Go Next

With the step-function mental model in hand, the following topics are natural next steps:

- **[Schedule reference](../../reference/schedule/index.md).** The reference documentation for `Schedule` covers every combinator and factory method in detail, including `windowed`, `upTo`, `resetAfter`, `whileInput`, `untilOutput`, `addDelay`, `modifyDelayZIO`, and the cron-like helpers `secondOfMinute`, `minuteOfHour`, `hourOfDay`, `dayOfWeek`, and `dayOfMonth`.
- **[Built-in schedules](../../reference/schedule/built-in-schedules.md).** The built-in schedules reference page catalogs every factory method in the `Schedule` companion object with concise descriptions and signatures.
- **[Retry strategies](../../reference/schedule/retrying.md).** The retrying reference page shows patterns for combining schedules with error types, including filtering which errors trigger a retry using `Schedule#whileInput`.
- **[Repetition patterns](../../reference/schedule/repetition.md).** The repetition reference page covers `ZIO#repeatUntil`, `ZIO#repeatWhile`, and `ZIO#repeatN` for common cases where a full schedule is not needed.
- **ZStream scheduling.** `ZStream.fromSchedule(schedule)` treats any schedule as a finite or infinite source of `Out` values, stepping once per emitted element. This connects scheduling to the broader stream processing API.
- **Test scheduling.** ZIO Test's `TestClock` lets you advance simulated time instantly, eliminating real sleeping in tests that use `spaced`, `exponential`, or any other delay-based schedule. This is the standard approach for unit-testing retry and repeat logic.
