---
id: combinators
title: "Schedule Combinators"
---

```scala mdoc:invisible
import zio._
```

Schedules define stateful, possibly effectful, recurring schedules of events, and compose in a variety of ways. Combinators allow us to take schedules and combine them together to get other schedules and if we have combinators with just the right properties. Then in theory we should be able to solve an infinite number of problems, with only a few combinators and few base schedules.

## Composition

Schedules compose in the following primary ways:

* **Union**. This performs the union of the intervals of two schedules.
* **Intersection**. This performs the intersection of the intervals of two schedules.
* **Sequencing**. This concatenates the intervals of one schedule onto another.

### Union

Combines two schedules through union, by recurring if either schedule wants to
recur, using the minimum of the two delays between recurrences.

|                     | `s1`                | `s2`                | `s1` &#124; &#124; `s2`   |
|---------------------|---------------------|---------------------|---------------------------|
| Type                | `Schedule[R, A, B]` | `Schedule[R, A, C]` | `Schedule[R, A, (B, C)]`  |
| Continue: `Boolean` | `b1`                | `b2`                | `b1` &#124; &#124; `b2`   |
| Delay: `Duration`   | `d1`                | `d2`                | `d1.min(d2)`              |
| Emit: `(A, B)`      | `a`                 | `b`                 | `(a, b)`                  |

We can combine two schedule through union with `||` operator:

```scala mdoc:silent
val expCapped = Schedule.exponential(100.milliseconds) || Schedule.spaced(1.second)
```

### Intersection

Combines two schedules through the intersection, by recurring only if both schedules want to recur, using the maximum of the two delays between recurrences.

|                     | `s1`                | `s2`                | `s1 && s2`               |
|---------------------|---------------------|---------------------|--------------------------|
| Type                | `Schedule[R, A, B]` | `Schedule[R, A, C]` | `Schedule[R, A, (B, C)]` |
| Continue: `Boolean` | `b1`                | `b2`                | `b1 && b2`               |
| Delay: `Duration`   | `d1`                | `d2`                | `d1.max(d2)`             |
| Emit: `(A, B)`      | `a`                 | `b`                 | `(a, b)`                 |


We can intersect two schedule with `&&` operator:

```scala mdoc:silent
val expUpTo10 = Schedule.exponential(1.second) && Schedule.recurs(10)
```

### Sequencing

Combines two schedules sequentially, by following the first policy until it ends, and then following the second policy.

|                   | `s1`                | `s2`                | `s1 andThen s2`     |
|-------------------|---------------------|---------------------|---------------------|
| Type              | `Schedule[R, A, B]` | `Schedule[R, A, C]` | `Schedule[R, A, C]` |
| Delay: `Duration` | `d1`                | `d2`                | `d1 + d2`           |
| Emit: `B`         | `a`                 | `b`                 | `b`                 |


We can sequence two schedule by using `andThen`:

```scala mdoc:silent
val sequential = Schedule.recurs(10) andThen Schedule.spaced(1.second)
```

## Piping

Combine two schedules by piping the output of the first schedule to the input of the other. Effects described by the first schedule will always be executed before the effects described by the second schedule.

|                   | `s1`                | `s2`                | `s1 >>> s2`         |
|-------------------|---------------------|---------------------|---------------------|
| Type              | `Schedule[R, A, B]` | `Schedule[R, B, C]` | `Schedule[R, A, C]` |
| Delay: `Duration` | `d1`                | `d2`                | `d1 + d2`           |
| Emit: `B`         | `a`                 | `b`                 | `b`                 | 

We can pipe two schedule by using `>>>` operator:

```scala mdoc:silent
val totalElapsed = Schedule.spaced(1.second) <* Schedule.recurs(5) >>> Schedule.elapsed
```

## Jittering

A `jittered` is a combinator that takes one schedule and returns another schedule of the same type except for the delay which is applied randomly:

| Function   | Input Type                 | Output Type                          |
|------------|----------------------------|--------------------------------------|
| `jittered` |                            | `Schedule[Env with Random, In, Out]` |
| `jittered` | `min: Double, max: Double` | `Schedule[Env with Random, In, Out]` |

We can jitter any schedule by calling `jittered` on it:

```scala mdoc:silent
val jitteredExp = Schedule.exponential(10.milliseconds).jittered
```

When a resource is out of service due to overload or contention, retrying and backing off doesn't help us. If all failed API calls are backed off to the same point of time, they cause another overload or contention. Jitter adds some amount of randomness to the delay of the schedule. This helps us to avoid ending up accidentally synchronizing and taking the service down by accident.

The form with parameters `min` and `max` creates a new schedule where the new interval size is randomly distributed between `min * old interval` and `max * old interval`.

## Collecting

A `collectAll` is a combinator that when we call it on a schedule, produces a new schedule that collects the outputs of the first schedule into a chunk.

| Function     | Input Type               | Output Type                     |
|--------------|--------------------------|---------------------------------|
| `collectAll` | `Schedule[Env, In, Out]` | `Schedule[Env, In, Chunk[Out]]` |

In the following example, we are catching all recurrence of schedule into `Chunk`, so at the end, it would contain `Chunk(0, 1, 2, 3, 4)`:

```scala mdoc:silent
val collect = Schedule.recurs(5).collectAll
```

## Filtering

We can filter inputs or outputs of a schedule with `whileInput` and `whileOutput`. Alse ZIO schedule has an effectful version of these two functions, `whileInputZIO` and `whileOutputZIO`.

| Function         | Input Type                   | Output Type                |
|------------------|------------------------------|----------------------------|
| `whileInput`     | `In1 => Boolean`             | `Schedule[Env, In1, Out]`  |
| `whileOutput`    | `Out => Boolean`             | `Schedule[Env, In, Out]`   |
| `whileInputZIO`  | `In1 => URIO[Env1, Boolean]` | `Schedule[Env1, In1, Out]` |
| `whileOutputZIO` | `Out => URIO[Env1, Boolean]` | `Schedule[Env1, In, Out]`  |

In following example we collect all emiting outputs before reaching the 5 output, so it would return `Chunk(0, 1, 2, 3, 4)`:

```scala mdoc:silent
val res = Schedule.unfold(0)(_ + 1).whileOutput(_ < 5).collectAll
```

## Mapping

There are two versions for mapping schedules, `map` and its effectful version `mapZIO`.

| Function | Input Type                   | Output Type                |
|----------|------------------------------|----------------------------|
| `map`    | `f: Out => Out2`             | `Schedule[Env, In, Out2]`  |
| `mapZIO` | `f: Out => URIO[Env1, Out2]` | `Schedule[Env1, In, Out2]` |

## Left/Right Ap

Sometimes when we intersect two schedules with the `&&` operator, we just need to ignore the left or the right output.
- * `*>` ignore the left output
- * `<*` ignore the right output

## Modifying

Modifies the delay of a schedule:

```scala mdoc:silent
val boosted = Schedule.spaced(1.second).delayed(_ => 100.milliseconds)
```

## Tapping

Whenever we need to effectfully process each schedule input/output, we can use `tapInput` and `tapOutput`.

We can use these two functions for logging purposes:

```scala mdoc:silent
val tappedSchedule = Schedule.count.whileOutput(_ < 5).tapOutput(o => Console.printLine(s"retrying $o").orDie)
```
