---
id: repetition
title: "Repetition"
---

```scala mdoc:invisible
import zio._
```

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

There is another version of `repeat` that helps us to have a fallback strategy in case of errors, if something goes wrong we can handle that by using the `ZIO#repeatOrElse` function, which helps up to add an `orElse` callback that will run in case of repetition failure:

```scala
val action:       ZIO[R, E, A] = ???
val policy: Schedule[R1, A, B] = ???

val orElse: (E, Option[B]) => ZIO[R1, E2, B] = ???

val repeated = action repeatOrElse (policy, orElse)
```
