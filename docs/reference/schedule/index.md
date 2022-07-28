---
id: index 
title: "Scheduling ZIO Effects"
---

```scala mdoc:invisible
import zio._
```

A `Schedule[Env, In, Out]` is an **immutable value** that **describes** a recurring effectful schedule, which runs in some environment `Env`, after consuming values of type `In` (errors in the case of `retry`, or values in the case of `repeat`) produces values of type `Out`, and in every step based on input values and the internal state decides to halt or continue after some delay **d**.

Schedules are defined as a possibly infinite set of intervals spread out over time. Each interval defines a window in which recurrence is possible.

When schedules are used to repeat or retry effects, the starting boundary of each interval produced by a schedule is used as the moment when the effect will be executed again.

A variety of other operators exist for transforming and combining schedules, and the companion object for `Schedule` contains all common types of schedules, both for performing retrying, as well as performing repetition.
