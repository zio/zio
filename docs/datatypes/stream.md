---
id: datatypes_stream
title:  "Stream"
---

A `Stream[E, A]` represents an effectful stream that can produce values of
type `A`, or potentially fail with a value of type `E`.

## Creating a Stream

```scala mdoc:silent
import zio.stream._

val stream: Stream[Nothing, Int] = Stream(1,2,3)
```

Or from an Iterable :

```scala mdoc:silent
import zio.stream._

val streamFromIterable: Stream[Nothing, Int] = Stream.fromIterable(0 to 100)
```

## Transforming a Stream

```scala mdoc:silent
import zio.stream._

val intStream: Stream[Nothing, Int] = Stream.fromIterable(0 to 100)
val stringStream: Stream[Nothing, String] = intStream.map(_.toString)
```

## Consuming a Stream

```scala mdoc:silent
import zio._
import zio.console._
import zio.stream._

val result: RIO[Console, Unit] = Stream.fromIterable(0 to 100).foreach(i => putStrLn(i.toString))
```

### Using a Sink

A `Sink[E, A0, A, B]` consumes values of type `A`, ultimately producing
either an error of type `E`, or a value of type `B` together with a remainder
of type `A0`.

You can for example reduce a `Stream` to a `ZIO` value using `Sink.foldLeft` : 

```scala mdoc:silent
import zio._
import zio.stream._

def streamReduce(total: Int, element: Int): Int = total + element
val resultFromSink: UIO[Int] = Stream(1,2,3).run(Sink.foldLeft(0)(streamReduce))
```

## Working on several streams

You can merge several streams using the `merge` method :

```scala mdoc:silent
import zio.stream._

val merged: Stream[Nothing, Int] = Stream(1,2,3).merge(Stream(2,3,4))
```

Or zipping streams : 

```scala mdoc:silent
import zio.stream._

val zippedStream: Stream[Nothing, (Int, Int)] = Stream(1,2,3).zip(Stream(2,3,4))
```

Then you would be able to reduce the stream into to a `ZIO` value : 

```scala mdoc:silent
import zio._

def tupleStreamReduce(total: Int, element: (Int, Int)) = {
  val (a,b) = element
  total + (a +b)
} 

val reducedResult: UIO[Int] = zippedStream.run(Sink.foldLeft(0)(tupleStreamReduce))
```
