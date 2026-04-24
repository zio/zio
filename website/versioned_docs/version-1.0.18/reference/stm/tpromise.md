---
id: tpromise
title: "TPromise"
---

`TPromise` is a mutable reference that can be set exactly once and can participate in transactions in STM.

## Create a TPromise

Creating a `TPromise`:

```scala
import zio._
import zio.stm._

val tPromise: STM[Nothing, TPromise[String, Int]] = TPromise.make[String, Int]
```

## Complete a TPromise

In order to successfully complete a `TPromise`:

```scala
import zio._
import zio.stm._

val tPromiseSucceed: UIO[TPromise[String, Int]] = for {
  tPromise <- TPromise.make[String, Int].commit
  _        <- tPromise.succeed(0).commit
} yield tPromise
```

In order to fail a `TPromise` use:

```scala
import zio._
import zio.stm._

val tPromiseFail: UIO[TPromise[String, Int]] = for {
  tPromise <- TPromise.make[String, Int].commit
  _        <- tPromise.fail("failed").commit
} yield tPromise
```

Alternatively, you can use `done` combinator and complete the promise by passing it `Either[E, A]`:

```scala
import zio._
import zio.stm._

val tPromiseDoneSucceed: UIO[TPromise[String, Int]] = for {
  tPromise <- TPromise.make[String, Int].commit
  _        <- tPromise.done(Right(0)).commit
} yield tPromise

val tPromiseDoneFail: UIO[TPromise[String, Int]] = for {
  tPromise <- TPromise.make[String, Int].commit
  _        <- tPromise.done(Left("failed")).commit
} yield tPromise
```

Once the value is set, any following attempts to set it will result in `false`.

## Retrieve the value of a TPromise

Returns the result if the promise has already been completed or a `None` otherwise:

```scala
import zio._
import zio.stm._

val tPromiseOptionValue: UIO[Option[Either[String, Int]]] = for {
  tPromise <- TPromise.make[String, Int].commit
  _        <- tPromise.succeed(0).commit
  res      <- tPromise.poll.commit
} yield res
```

Alternatively, you can wait for the promise to be completed and return the value:

```scala
import zio._
import zio.stm._

val tPromiseValue: IO[String, Int] = for {
  tPromise <- TPromise.make[String, Int].commit
  _        <- tPromise.succeed(0).commit
  res      <- tPromise.await.commit
} yield res
```
