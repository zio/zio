---
id: retrying
title: "Retrying"
---

```scala mdoc:invisible
import zio._
```

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
