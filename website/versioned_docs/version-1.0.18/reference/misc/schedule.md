---
id: schedule
title: "Schedule"
---


A `Schedule[Env, In, Out]` is an **immutable value** that **describes** a recurring effectful schedule, which runs in some environment `Env`, after consuming values of type `In` (errors in the case of `retry`, or values in the case of `repeat`) produces values of type `Out`, and in every step based on input values and the internal state decides to halt or continue after some delay **d**.

Schedules are defined as a possibly infinite set of intervals spread out over time. Each interval defines a window in which recurrence is possible.

When schedules are used to repeat or retry effects, the starting boundary of each interval produced by a schedule is used as the moment when the effect will be executed again.

A variety of other operators exist for transforming and combining schedules, and the companion object for `Schedule` contains all common types of schedules, both for performing retrying, as well as performing repetition.

## Repeat and Retry
Schedules allow us to define and compose flexible recurrence schedules, which can be used to **repeat** actions, or **retry** actions in the event of errors.

Repetition and retrying are two similar concepts in the domain of scheduling. It is the same concept and idea, only one of them looks for successes and the other one looks for failures. 

### Repeat
In the case of repetition, ZIO has a `ZIO#repeat` function, which takes a schedule as a repetition policy and returns another effect that describes an effect with repetition strategy according to that policy.

Repeat policies are used in the following functions:

* `ZIO#repeat` — Repeats an effect until the schedule is done.
* `ZIO#repeatOrElse` — Repeats an effect until the schedule is done, with a fallback for errors.

> _**Note:**_
>
> Scheduled recurrences are in addition to the first execution, so that `io.repeat(Schedule.once)` yields an effect that executes `io`, and then if that succeeds, executes `io` an additional time.

Let's see how we can create a repeated effect by using `ZIO#repeat` function:

```scala
val action:      ZIO[R, E, A] = ???
val policy: Schedule[R1, A, B] = ???

val repeated = action repeat policy
```

There is another version of `repeat` that helps us to have a fallback strategy in case of erros, if something goes wrong we can handle that by using the `ZIO#repeatOrElse` function, which helps up to add an `orElse` callback that will run in case of repetition failure:

```scala
val action:       ZIO[R, E, A] = ???
val policy: Schedule[R1, A, B] = ???

val orElse: (E, Option[B]) => ZIO[R1, E2, B] = ???

val repeated = action repeatOrElse (policy, orElse)
```

### Retry
In the case of retrying, ZIO has a `ZIO#retry` function, which takes a schedule as a repetition policy and returns another effect that describes an effect with repetition strategy which will retry following the failure of the original effect.

Repeat policies are used in the following functions:

* `ZIO#retry` – Retries an effect until it succeeds.
* `ZIO#retryOrElse` — Retries an effect until it succeeds, with a fallback for errors.

Let's see how we can create a repeated effect by using `ZIO#retry` function:

```scala
val action:       ZIO[R, E, A] = ???
val policy: Schedule[R1, E, S] = ???

val repeated = action retry policy

```

There is another version of `retry` that helps us to have a fallback strategy in case of erros, if something goes wrong we can handle that by using the `ZIO#retryOrElse` function, which helps up to add an `orElse` callback that will run in case of failure of repetition failure:


```scala
val action:       ZIO[R, E, A] = ???
val policy: Schedule[R1, A, B] = ???

val orElse: (E, S) => ZIO[R1, E1, A1] = ???

val repeated = action retryOrElse (policy, orElse)
```

## Base Schedules
### stop 
A schedule that does not recur, just stops and returns one `Unit` element:

```scala
val stop = Schedule.stop
```

### once 
A schedule that recurs one time an returns one `Unit` element:

```scala
val once = Schedule.once
```

### forever
A schedule that always recurs and produces number of recurrence at each run:

```scala
val forever = Schedule.forever
```

### recurs
A schedule that only recurs the specified number of times:

```scala
val recurs = Schedule.recurs(5)
```

### spaced
A schedule that recurs continuously, each repetition spaced the specified duration from the last run:

```scala
val spaced = Schedule.spaced(10.milliseconds)
```

### fixed
A schedule that recurs on a fixed interval. Returns the number of repetitions of the schedule so far:

```scala
val fixed = Schedule.fixed(10.seconds)
```

### exponential
A schedule that recurs using exponential backoff:

```scala
val exponential = Schedule.exponential(10.milliseconds)
```

### fibonacci
A schedule that always recurs, increasing delays by summing the preceding two delays (similar to the fibonacci sequence). Returns the current duration between recurrences:

```scala
val fibonacci = Schedule.fibonacci(10.milliseconds)
```
### identity
A schedule that always decides to continue. It recurs forever, without any delay. `identity` schedule consumes input, and emit the same as output (`Schedule[Any, A, A]`):

```scala
val identity = Schedule.identity[Int]
```

### unfold
A schedule that repeats one time from the specified state and iterator:

```scala
val unfold = Schedule.unfold(0)(_ + 1)
```

### succeed
Returns a schedule that repeats one time, producing the specified constant value:

```scala
val constant = Schedule.succeed(5)
```

### fromFunction
A schedule that always recurs, mapping input values through the specified function:

```scala
val inc = Schedule.fromFunction[Int, Int](_ + 1)
```

## Schedule Combinators
Schedules define stateful, possibly effectful, recurring schedules of events, and compose in a variety of ways. Combinators allow us to take schedules and combine them together to get other schedules and if we have combinators with just the right properties. Then in theory we should be able to solve an infinite number of problems, with only a few combinators and few base schedules.

### Composition
Schedules compose in the following primary ways:

 * **Union**. This performs the union of the intervals of two schedules.
 * **Intersection**. This performs the intersection of the intervals of two schedules.
 * **Sequence**. This concatenates the intervals of one schedule onto another.

#### Union
Combines two schedules through union, by recurring if either schedule wants to
recur, using the minimum of the two delays between recurrences.

|                      | `s1`                | `s2`                | `s1` &#124; &#124; `s2`                       |
|----------------------|---------------------|---------------------|--------------------------|
| Type                 | `Schedule[R, A, B]` | `Schedule[R, A, C]` | `Schedule[R, A, (B, C)]` |
| Continute: `Boolean` | `b1`                | `b2`                | `b1` &#124; &#124; `b2` |
| Delay: `Duration`    | `d1`                | `d2`                | `d1.min(d2)`             |
| Emit: `(A, B)`       | `a`                 | `b`                 | `(a, b)`                 |

We can combine two schedule through union with `||` operator:

```scala
val expCapped = Schedule.exponential(100.milliseconds) || Schedule.spaced(1.second)
```

#### Intersection
Combines two schedules through the intersection, by recurring only if both schedules want to recur, using the maximum of the two delays between recurrences.

|                      | `s1`                | `s2`                | `s1 && s2`               |
|----------------------|---------------------|---------------------|--------------------------|
| Type                 | `Schedule[R, A, B]` | `Schedule[R, A, C]` | `Schedule[R, A, (B, C)]` |
| Continute: `Boolean` | `b1`                | `b2`                | `b1 && b2`               |
| Delay: `Duration`    | `d1`                | `d2`                | `d1.max(d2)`             |
| Emit: `(A, B)`       | `a`                 | `b`                 | `(a, b)`                 |


We can intersect two schedule with `&&` operator:

```scala
val expUpTo10 = Schedule.exponential(1.second) && Schedule.recurs(10)
```

#### Sequence
Combines two schedules sequentially, by following the first policy until it ends, and then following the second policy.

|                   | `s1`                | `s2`                | `s1 andThen s2`     |
|-------------------|---------------------|---------------------|---------------------|
| Type              | `Schedule[R, A, B]` | `Schedule[R, A, C]` | `Schedule[R, A, C]` |
| Delay: `Duration` | `d1`                | `d2`                | `d1 + d2`           |
| Emit: `B`         | `a`                 | `b`                 | `b`                 |


We can sequence two schedule by using `andThen`:

```scala
val sequential = Schedule.recurs(10) andThen Schedule.spaced(1.second)
```

### Piping
Combine two schedules by piping the output of the first schedule to the input of the other. Effects described by the first schedule will always be executed before the effects described by the second schedule.

|                   | `s1`                | `s2`                | `s1 >>> s2`  |
|-------------------|---------------------|---------------------|---------------------|
| Type              | `Schedule[R, A, B]` | `Schedule[R, B, C]` | `Schedule[R, A, C]` |
| Delay: `Duration` | `d1`                | `d2`                | `d1 + d2`           |
| Emit: `B`         | `a`                 | `b`                 | `b`                 |

We can pipe two schedule by using `>>>` operator:

```scala
val totalElapsed = Schedule.spaced(1.second) <* Schedule.recurs(5) >>> Schedule.elapsed
```

### Jittering
A `jittered` is a combinator that takes one schedule and returns another schedule of the same type except for the delay which is applied randomly:

| Function   | Input Type                 | Output Type                          |
|------------|----------------------------|--------------------------------------|
| `jittered` |                            | `Schedule[Env with Random, In, Out]` |
| `jittered` | `min: Double, max: Double` | `Schedule[Env with Random, In, Out]` |

We can jitter any schedule by calling `jittered` on it:

```scala
val jitteredExp = Schedule.exponential(10.milliseconds).jittered
```

When a resource is out of service due to overload or contention, retrying and backing off doesn't help us. If all failed API calls are backed off to the same point of time, they cause another overload or contention. Jitter adds some amount of randomness to the delay of the schedule. This helps us to avoid ending up accidentally synchronizing and taking the service down by accident.

### Collecting
A `collectAll` is a combinator that when we call it on a schedule, produces a new schedule that collects the outputs of the first schedule into a chunk.

| Function     | Input Type               | Output Type                     |
|--------------|--------------------------|---------------------------------|
| `collectAll` | `Schedule[Env, In, Out]` | `Schedule[Env, In, Chunk[Out]]` |

In the following example, we are catching all recurrence of schedule into `Chunk`, so at the end, it would contain `Chunk(0, 1, 2, 3, 4)`:

```scala
val collect = Schedule.recurs(5).collectAll
```

### Filtering
We can filter inputs or outputs of a schedule with `whileInput` and `whileOutput`. Alse ZIO schedule has an effectful version of these two functions, `whileInputM` and `whileOutputM`.

| Function       | Input Type                   | Output Type                |
|----------------|------------------------------|----------------------------|
| `whileInput`   | `In1 => Boolean`             | `Schedule[Env, In1, Out]`  |
| `whileOutput`  | `Out => Boolean`             | `Schedule[Env, In, Out]`   |
| `whileInputM`  | `In1 => URIO[Env1, Boolean]` | `Schedule[Env1, In1, Out]` |
| `whileOutputM` | `Out => URIO[Env1, Boolean]` | `Schedule[Env1, In, Out]`  |

In following example we collect all emiting outputs before reaching the 5 output, so it would return `Chunk(0, 1, 2, 3, 4)`:

```scala
val res = Schedule.unfold(0)(_ + 1).whileOutput(_ < 5).collectAll
```

### Mapping
There are two versions for mapping schedules, `map` and its effectful version `mapM`.

| Function | Input Type                   | Output Type                |
|----------|------------------------------|----------------------------|
| `map`    | `f: Out => Out2`             | `Schedule[Env, In, Out2]`  |
| `mapM`   | `f: Out => URIO[Env1, Out2]` | `Schedule[Env1, In, Out2]` |

### Left/Right Ap
Sometimes when we intersect two schedules with the `&&` operator, we just need to ignore the left or the right output. 
- * `*>` ignore the left output 
- * `<*` ignore the right output

### Modifying
Modifies the delay of a schedule:

```scala
val boosted = Schedule.spaced(1.second).delayed(_ => 100.milliseconds)
```

### Tapping
Whenever we need to effectfully process each schedule input/output, we can use `tapInput` and `tapOutput`.

We can use these two functions for logging purposes:

```scala
val tappedSchedule = Schedule.count.whileOutput(_ < 5).tapOutput(o => putStrLn(s"retrying $o").orDie)
```


## Examples

Stops retrying after a specified amount of time has elapsed:

```scala
val expMaxElapsed = (Schedule.exponential(10.milliseconds) >>> Schedule.elapsed).whileOutput(_ < 30.seconds)
```

Retry only when a specific exception occurs:

```scala
import scala.concurrent.TimeoutException

val whileTimeout = Schedule.exponential(10.milliseconds) && Schedule.recurWhile[Throwable] {
  case _: TimeoutException => true
  case _ => false
}
```
