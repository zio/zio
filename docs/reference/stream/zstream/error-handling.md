---
id: error-handling
title: "Error Handling"
---

## Recovering from Failure

If we have a stream that may fail, we might need to recover from the failure and run another stream, the `ZStream#orElse` takes another stream, so when the failure occurs it will switch over to the provided stream:

```scala mdoc:invisible
import java.net.URL
import scala.concurrent.TimeoutException
import java.io.{BufferedReader, FileReader, FileInputStream, IOException}
```

```scala mdoc:silent:nest
import zio.stream._

val s1 = ZStream(1, 2, 3) ++ ZStream.fail("Oh! Error!") ++ ZStream(4, 5)
val s2 = ZStream(6, 7, 8)

val stream = s1.orElse(s2)
// Output: 1, 2, 3, 6, 7, 8
```

Another variant of `orElse` is `ZStream#orElseEither`, which distinguishes elements of the two streams using the `Either` data type. Using this operator, the result of the previous example should be `Left(1), Left(2), Left(3), Right(6), Right(7), Right(8)`.

ZIO stream has `ZStream#catchAll` which is powerful version of `ZStream#orElse`. By using `catchAll` we can decide what to do based on the type and value of the failure:

```scala mdoc:silent:nest
val first =
  ZStream(1, 2, 3) ++
    ZStream.fail("Uh Oh!") ++
    ZStream(4, 5) ++
    ZStream.fail("Ouch")

val second = ZStream(6, 7, 8)
val third = ZStream(9, 10, 11)

val stream = first.catchAll {
  case "Uh Oh!" => second
  case "Ouch"   => third
}
// Output: 1, 2, 3, 6, 7, 8
```

## Recovering from Defects

If we need to recover from all causes of failures including defects we should use the `ZStream#catchAllCause` method:

```scala mdoc:silent:nest
val s1 = ZStream(1, 2, 3) ++ ZStream.dieMessage("Oh! Boom!") ++ ZStream(4, 5)
val s2 = ZStream(7, 8, 9)

val stream = s1.catchAllCause(_ => s2)
// Output: 1, 2, 3, 7, 8, 9
```

## Recovery from Some Errors

If we need to recover from specific failure we should use `ZStream#catchSome`:

```scala mdoc:silent:nest
val s1 = ZStream(1, 2, 3) ++ ZStream.fail("Oh! Error!") ++ ZStream(4, 5)
val s2 = ZStream(7, 8, 9)
val stream = s1.catchSome {
  case "Oh! Error!" => s2
}
// Output: 1, 2, 3, 7, 8, 9
```

And, to recover from a specific cause, we should use `ZStream#catchSomeCause` method:

```scala mdoc:silent:nest
import zio._
import zio.Cause._
import zio.stream._

val s1 = ZStream(1, 2, 3) ++ ZStream.dieMessage("Oh! Boom!") ++ ZStream(4, 5)
val s2 = ZStream(7, 8, 9)
val stream = s1.catchSomeCause { case Die(value, _) => s2 }
```

## Recovering to ZIO Effect

If our stream encounters an error, we can provide some cleanup task as ZIO effect to our stream by using the `ZStream#onError` method:

```scala mdoc:silent:nest
import zio._
import zio.stream._

val stream = 
  (ZStream(1, 2, 3) ++ ZStream.dieMessage("Oh! Boom!") ++ ZStream(4, 5))
    .onError(_ => Console.printLine("Stream application closed! We are doing some cleanup jobs.").orDie)
```

## Retry a Failing Stream

When a stream fails, it can be retried according to the given schedule to the `ZStream#retry` operator:

```scala mdoc:silent:nest
val numbers = ZStream(1, 2, 3) ++ 
  ZStream
    .fromZIO(
      Console.print("Enter a number: ") *> Console.readLine
        .flatMap(x =>
          x.toIntOption match {
            case Some(value) => ZIO.succeed(value)
            case None        => ZIO.fail("NaN")
          }
        )
    )
    .retry(Schedule.exponential(1.second))
```

## From/To Either

Sometimes, we might be working with legacy API which does error handling with the `Either` data type. We can _absolve_ their error types into the ZStream effect using `ZStream.absolve`:

```scala mdoc:silent:nest
def legacyFetchUrlAPI(url: URL): Either[Throwable, String] = ???

def fetchUrl(
    url: URL
): ZStream[Any, Throwable, String] = 
  ZStream.fromZIO(
    ZIO.attemptBlocking(legacyFetchUrlAPI(url))
  ).absolve
```

The type of this stream before absolving is `ZStream[Any, Throwable, Either[Throwable, String]]`, this operation let us submerge the error case of an `Either` into the `ZStream` error type.

We can do the opposite by exposing an error of type `ZStream[R, E, A]` as a part of the `Either` by using `ZStream#either`:

```scala mdoc:silent:nest
val inputs: ZStream[Any, Nothing, Either[IOException, String]] = 
  ZStream.fromZIO(Console.readLine).either
```

When we are working with streams of `Either` values, we might want to fail the stream as soon as the emission of the first `Left` value:

```scala mdoc:silent:nest
// Stream of Either values that cannot fail
val eitherStream: ZStream[Any, Nothing, Either[String, Int]] =
  ZStream(Right(1), Right(2), Left("failed to parse"), Right(4))

// A Fails with the first emission of the left value
val stream: ZStream[Any, String, Int] = eitherStream.rightOrFail("fail")
```

## Refining Errors

We can keep one or some errors and terminate the fiber with the rest by using `ZStream#refineOrDie`:

```scala mdoc:silent:nest
val stream: ZStream[Any, Throwable, Int] =
  ZStream.fail(new Throwable)

val res: ZStream[Any, IllegalArgumentException, Int] =
  stream.refineOrDie { case e: IllegalArgumentException => e }
```

## Timing Out

We can timeout a stream if it does not produce a value after some duration using `ZStream#timeout`, `ZStream#timeoutFail` and `timeoutFailCause` operators:

```scala mdoc:silent:nest
stream.timeoutFail(new TimeoutException)(10.seconds)
```

Or we can switch to another stream if the first stream does not produce a value after some duration:

```scala mdoc:silent:nest
val alternative = ZStream.fromZIO(ZIO.attempt(???))
stream.timeoutTo(10.seconds)(alternative)
```
