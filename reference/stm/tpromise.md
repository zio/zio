# TPromise

> `TPromise` is a mutable reference that can be set exactly once and can participate in transactions in STM.

`TPromise` is a mutable reference that can be set exactly once and can participate in transactions in STM.

## Create a TPromise

Creating a `TPromise`:

```scala

val tPromise: STM[Nothing, TPromise[String, Int]] = TPromise.make[String, Int]
```

## Complete a TPromise

In order to successfully complete a `TPromise`:

```scala

val tPromiseSucceed: UIO[TPromise[String, Int]] = for {
  tPromise <- TPromise.make[String, Int].commit
  _        <- tPromise.succeed(0).commit
} yield tPromise
```

In order to fail a `TPromise` use:

```scala

val tPromiseFail: UIO[TPromise[String, Int]] = for {
  tPromise <- TPromise.make[String, Int].commit
  _        <- tPromise.fail("failed").commit
} yield tPromise
```

Alternatively, you can use `done` combinator and complete the promise by passing it `Either[E, A]`:

```scala

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

val tPromiseOptionValue: UIO[Option[Either[String, Int]]] = for {
  tPromise <- TPromise.make[String, Int].commit
  _        <- tPromise.succeed(0).commit
  res      <- tPromise.poll.commit
} yield res
```

Alternatively, you can wait for the promise to be completed and return the value:

```scala

val tPromiseValue: IO[String, Int] = for {
  tPromise <- TPromise.make[String, Int].commit
  _        <- tPromise.succeed(0).commit
  res      <- tPromise.await.commit
} yield res
```
