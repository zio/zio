---
id: retrying
title: "Retrying"
sidebar_label: "4. Retrying"
---

When we are building applications we want to be resilient in the face of a transient failure. This is where we need to retry to overcome these failures.

There are a number of useful methods on the ZIO data type for retrying failed effects:

## `ZIO#retry`

The most basic of these is `ZIO#retry`, which takes a `Schedule` and returns a new effect that will retry the first effect if it fails, according to the specified policy:

```scala
trait ZIO[-R, +E, +A] {
  def retry[R1 <: R, S](policy: => Schedule[R1, E, S]): ZIO[R1, E, A]
}
```

In this example, we try to read from a file. If we fail to do that, it will try five more times:

```scala mdoc:invisible
import zio._
import java.io.{ FileNotFoundException, IOException }

def readFile(s: String): ZIO[Any, IOException, Array[Byte]] =
  ZIO.attempt(???).refineToOrDie[IOException]
```

```scala mdoc:compile-only
import zio._

val retriedOpenFile: ZIO[Any, IOException, Array[Byte]] =
  readFile("primary.data").retry(Schedule.recurs(5))
```

## `ZIO#retryN`

In case of failure, a ZIO effect can be retried as many times as specified:

```scala mdoc:compile-only
import zio._

val file = readFile("primary.data").retryN(5)
```

## `ZIO#retryOrElse`

The next most powerful function is `ZIO#retryOrElse`, which allows specification of a fallback to use, if the effect does not succeed with the specified policy:

```scala
trait ZIO[-R, +E, +A] {
  def retryOrElse[R1 <: R, A1 >: A, S, E1](
    policy: => Schedule[R1, E, S],
    orElse: (E, S) => ZIO[R1, E1, A1]
  ): ZIO[R1, E1, A1] =
}
```

The `orElse` is the recovery function that has two inputs:

1. The last error message
2. Schedule output

So based on these two values, we can decide what to do as the fallback operation. Let's try an example:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    Random
      .nextIntBounded(11)
      .flatMap { n =>
        if (n < 9)
          ZIO.fail(s"$n is less than 9!").debug("failed")
        else
          ZIO.succeed(n).debug("succeeded")
      }
      .retryOrElse(
        policy = Schedule.recurs(5),
        orElse = (lastError, scheduleOutput: Long) =>
          ZIO.debug(s"after $scheduleOutput retries, we couldn't succeed!") *>
            ZIO.debug(s"the last error message we received was: $lastError") *>
            ZIO.succeed(-1)
      )
      .debug("the final result")
}
```

## `ZIO#retryOrElseEither`

This operator is almost the same as the **`ZIO#retryOrElse`** except it will return either result of the original or the fallback operation:

```scala mdoc:compile-only
import zio._

trait LocalConfig
trait RemoteConfig

def readLocalConfig: ZIO[Any, Throwable, LocalConfig] = ???
def readRemoteConfig: ZIO[Any, Throwable, RemoteConfig] = ???

val result: ZIO[Any, Throwable, Either[RemoteConfig, LocalConfig]] =
  readLocalConfig.retryOrElseEither(
    schedule0 = Schedule.fibonacci(1.seconds),
    orElse = (_, _: Duration) => readRemoteConfig
  )
```

## `ZIO#retryUntil`/`ZIO#retryUntilZIO`

We can retry an effect until a condition on the error channel is satisfied:

```scala
trait ZIO[-R, +E, +A] {
  def retryUntil(f: E => Boolean): ZIO[R, E, A]
  def retryUntilZIO[R1 <: R](f: E => URIO[R1, Boolean]): ZIO[R1, E, A]
}
```

Assume we have defined the following remote service call:

```scala mdoc:silent
sealed trait  ServiceError extends Exception
case object TemporarilyUnavailable extends ServiceError
case object DataCorrupted          extends ServiceError

def remoteService: ZIO[Any, ServiceError, Unit] = ???
```

In the following example, we repeat the failed remote service call until we reach the `DataCorrupted` error:

```scala mdoc:compile-only
remoteService.retryUntil(_ == DataCorrupted)
```

To provide an effectful predicate we use the `ZIO#retryUntilZIO` operator.

## `ZIO#retryUntilEqual`

Like the previous operator, it tries until its error is equal to the specified error:

```scala mdoc:compile-only
remoteService.retryUntilEquals(DataCorrupted)
```

## `ZIO#retryWhile`/`ZIO#retryWhileZIO`

Unlike the `ZIO#retryUntil` it will retry the effect while its error satisfies the specified predicate:

```scala
trait ZIO[-R, +E, +A] {
  def retryWhile(f: E => Boolean): ZIO[R, E, A]
  def retryWhileZIO[R1 <: R](f: E => URIO[R1, Boolean]): ZIO[R1, E, A]
}
```

In the following example, we repeat the failed remote service call while we have the `TemporarilyUnavailable` error:

```scala mdoc:compile-only
remoteService.retryWhile(_ == TemporarilyUnavailable)
```

To provide an effectful predicate we use the `ZIO#retryWhileZIO` operator.

## `ZIO#retryWhileEquals`

Like the previous operator, it tries while its error is equal to the specified error:

```scala mdoc:compile-only
remoteService.retryWhileEquals(TemporarilyUnavailable)
```
