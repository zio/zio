---
id: index 
title: "Scheduling ZIO Effects"
---

```scala mdoc:invisible
import zio._
```

A `Schedule[Env, In, Out]` is an **immutable value** that **describes** a recurring effectful schedule, which runs in some environment `Env`, after consuming values of type `In` (errors in the case of `retry`, or values in the case of `repeat`) produces values of type `Out`, and in every step based on input values and the internal state decides to halt or continue after some delay **d**.

Schedules are defined as a possibly infinite set of intervals spread out over time. Each interval defines a window in which recurrence is possible.

[Repetition](repetition.md) and [retrying](retrying.md) are two similar concepts in the domain of scheduling. It is the same concept and idea, only one of them looks for successes and the other one looks for failures. 

When schedules are used to repeat or retry effects, the starting boundary of each interval produced by a schedule is used as the moment when the effect will be executed again. 

Schedules allow us to define and compose flexible recurrence schedules, which can be used to **repeat** actions, or **retry** actions in the event of errors. We will discuss them on the following pages.

A variety of [combinators](combinators.md) exist for transforming and combining schedules, and the companion object for `Schedule` contains [all common types of schedules](built-in-schedules.md), both for performing retrying and repetition.
