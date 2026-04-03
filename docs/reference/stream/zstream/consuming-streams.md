---
id: consuming-streams
title: "Consuming Streams"
---

```scala mdoc:silent
import zio._
import zio.Console._
import zio.stream._

val result: Task[Unit] = ZStream.fromIterable(0 to 100).foreach(printLine(_))
```

### Using a Sink

To consume a stream using `ZSink` we can pass `ZSink` to the `ZStream#run` function:

```scala mdoc:silent
val sum: UIO[Int] = ZStream(1,2,3).run(ZSink.sum)
```

### Using fold

The `ZStream#fold` method executes the fold operation over the stream of values and returns a `ZIO` effect containing the result:

```scala mdoc:silent:nest
val s1: ZIO[Any, Nothing, Int] = ZStream(1, 2, 3, 4, 5).runFold(0)(_ + _)
val s2: ZIO[Any, Nothing, Int] = ZStream.iterate(1)(_ + 1).runFoldWhile(0)(_ <= 5)(_ + _)
```

### Using foreach

Using `ZStream#foreach` is another way of consuming elements of a stream. It takes a callback of type `O => ZIO[R1, E1, Any]` which passes each element of a stream to this callback:

```scala mdoc:silent:nest
ZStream(1, 2, 3).foreach(printLine(_))
```
