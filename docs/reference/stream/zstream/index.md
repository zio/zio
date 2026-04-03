---
id: index
title: "Introduction to ZStream"
---

A `ZStream[R, E, O]` is a description of a program that, when evaluated, may emit zero or more values of type `O`, may fail with errors of type `E`, and uses an environment of type `R`.

One way to think of `ZStream` is as a `ZIO` program that could emit multiple values. As we know, a `ZIO[R, E, A]` data type, is a functional effect which is a description of a program that needs an environment of type `R`, it may end with an error of type `E`, and in case of success, it returns a value of type `A`. The important note about `ZIO` effects is that in the case of success they always end with exactly one value. There is no optionality here, no multiple infinite values, we always get exact value:

```scala mdoc:invisible
import zio._
import zio.Cause.Die
import zio.Console._
import zio.stm.{STM, TQueue}
import java.io.{BufferedReader, FileReader, FileInputStream, IOException}
import java.nio.file.{Files, Path, Paths}
import java.nio.file.Path._
import java.net.URL
import java.lang.IllegalArgumentException
import scala.concurrent.TimeoutException
```

```scala mdoc:silent:nest
val failedEffect: ZIO[Any, String, Nothing]       = ZIO.fail("fail!")
val oneIntValue : ZIO[Any, Nothing, Int]          = ZIO.succeed(3)
val oneListValue: ZIO[Any, Nothing, List[Int]]    = ZIO.succeed(List(1, 2, 3))
val oneOption   : ZIO[Any, Nothing , Option[Int]] = ZIO.succeed(None)
```

A functional stream is pretty similar, it is a description of a program that requires an environment of type `R` and it may signal with errors of type `E` and it yields `O`, but the difference is that it will yield zero or more values.

So a `ZStream` represents one of the following cases in terms of its elements:
- **An Empty Stream** — It might end up empty; which represent an empty stream, e.g. `ZStream.empty`.
- **One Element Stream** — It can represent a stream with just one value, e.g. `ZStream.succeed(3)`.
- **Multiple Finite Element Stream** — It can represent a stream of finite values, e.g. `ZStream.range(1, 10)`
- **Multiple Infinite Element Stream** — It can even represent a stream that _never ends_ as an infinite stream, e.g. `ZStream.iterate(1)(_ + 1)`.

```scala mdoc:silent:nest
import zio.stream.ZStream
val emptyStream         : ZStream[Any, Nothing, Nothing]   = ZStream.empty
val oneIntValueStream   : ZStream[Any, Nothing, Int]       = ZStream.succeed(4)
val oneListValueStream  : ZStream[Any, Nothing, List[Int]] = ZStream.succeed(List(1, 2, 3))
val finiteIntStream     : ZStream[Any, Nothing, Int]       = ZStream.range(1, 10)
val infiniteIntStream   : ZStream[Any, Nothing, Int]       = ZStream.iterate(1)(_ + 1)
```

Another example of a stream is when we're pulling a Kafka topic or reading from a socket. There is no inherent definition of an end there. Stream elements arrive at some point, or even they might never arrive at any point.

Based on type parameters of `ZStream`, there are 4 types of streams:

1. `ZStream[Any, Nothing, O]` — A stream that emits `O` values and cannot fail.
2. `ZStream[Any, Throwable, O]` — A stream that emits `O` values and can fail with `Throwable`.
3. `ZStream[Any, Nothing, Nothing]` — A stream that emits no elements.
4. `ZStream[R, E, O]` — A stream that requires access to the `R` service, can fail with error of type `E` and emits `O` values.
