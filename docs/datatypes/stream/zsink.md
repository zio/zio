---
id: zsink
title: "ZSink"
---
```scala mdoc:invisible
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import java.io.IOException
```

## Introduction

A `ZSink[R, E, I, L, Z]` is used to consume elements produced by a `ZStream`. You can think of a sink as a function that will consume a variable amount of `I` elements (could be 0, 1, or many!), might fail with an error of type `E`, and will eventually yield a value of type `Z` together with a remainder of type `L` as leftover.

To consume a stream using `ZSink` we can pass `ZSink` to the `ZStream#run` function:

```scala mdoc:silent
import zio._
import zio.stream._

val stream = ZStream.fromIterable(1 to 1000)
val sink   = ZSink.sum[Int]
val sum    = stream.run(sink)
```

## Creating sinks

The `zio.stream` provides numerous kinds of sinks to use.

### Common Constructors

**ZSink.head** — It creates a sink containing the first element, returns `None` for empty streams:

```scala mdoc:silent:nest
val sink: ZSink[Any, Nothing, Int, Int, Option[Int]] = ZSink.head[Int]
val head: ZIO[Any, Nothing, Option[Int]]             = ZStream(1, 2, 3, 4).run(sink)
// Result: Some(1)
``` 

**ZSink.last** — It consumes all elements of a stream and returns the last element of the stream:

```scala mdoc:silent:nest
val sink: ZSink[Any, Nothing, Int, Nothing, Option[Int]] = ZSink.last[Int]
val last: ZIO[Any, Nothing, Option[Int]]                 = ZStream(1, 2, 3, 4).run(sink)
// Result: Some(4)
```

**ZSink.count** — A sink that consumes all elements of the stream and counts the number of elements fed to it:

```scala mdoc:silent:nest
val sink : ZSink[Any, Nothing, Int, Nothing, Int] = ZSink.sum[Int]
val count: ZIO[Any, Nothing, Int]                 = ZStream(1, 2, 3, 4, 5).run(sink)
// Result: 5
```

**ZSink.sum** — A sink that consumes all elements of the stream and sums incoming numeric values:

```scala mdoc:silent:nest
val sink : ZSink[Any, Nothing, Int, Nothing, Int] = ZSink.sum[Int]
val sum: ZIO[Any, Nothing, Int]                 = ZStream(1, 2, 3, 4, 5).run(sink)
// Result: 15
```

**ZSink.take** — A sink that takes the specified number of values and result in a `Chunk` data type:

```scala mdoc:silent:nest
val sink  : ZSink[Any, Nothing, Int, Int, Chunk[Int]] = ZSink.take[Int](3)
val stream: ZIO[Any, Nothing, Chunk[Int]]             = ZStream(1, 2, 3, 4, 5).run(sink)
// Result: Chunk(1, 2, 3)
```

**ZSink.drain** — A sink that ignores its inputs:

```scala mdoc:silent:nest
val drain: ZSink[Any, Nothing, Any, Nothing, Unit] = ZSink.drain
```

**ZSink.timed** — A sink that executes the stream and times its execution:

```scala mdoc:silent
val timed: ZSink[Clock, Nothing, Any, Nothing, Duration] = ZSink.timed
val stream: ZIO[Clock, Nothing, Long] =
  ZStream(1, 2, 3, 4, 5).fixed(2.seconds).run(timed).map(_.getSeconds)
// Result: 10
```

**ZSink.foreach** — A sink that executes the provided effectful function for every element fed to it:

```scala mdoc:silent:nest
val printer: ZSink[Console, IOException, Int, Int, Unit] =
  ZSink.foreach((i: Int) => zio.console.putStrLn(i.toString))
val stream : ZIO[Console, IOException, Unit]             =
  ZStream(1, 2, 3, 4, 5).run(printer)
```

### From Success and Failure

Similar to the `ZStream` data type, we can create a `ZSink` using `fail` and `succeed` methods.

A sink that doesn't consume any element of type `String` from its upstream and successes with a value of `Int` type:

```scala mdoc:silent:nest
val succeed: ZSink[Any, Nothing, String, String, Int] = ZSink.succeed[String, Int](5)
```

A sink that doesn't consume any element of type `Int` from its upstream and intentionally fails with a message of `String` type:

```scala mdoc:silent:nest
val failed : ZSink[Any, String, Int, Int, Nothing] = ZSink.fail[String, Int]("fail!")
```

## Operations

Having created the sink, we can transform it with provided operations.

### contramap

Contramap is a simple combinator to change the domain of an existing function. While _map_ changes the co-domain of a function, the _contramap_ changes the domain of a function. So the _contramap_ takes a function and maps over its input.

This is useful when we have a fixed output, and our existing function cannot consume those outputs. So we can use _contramap_ to create a new function that can consume that fixed output. Assume we have a `ZSink.sum` that sums incoming numeric values, but we have a `ZStream` of `String` values. We can convert the `ZSink.sum` to a sink that can consume `String` values;

```scala mdoc:silent:nest
val numericSum: ZSink[Any, Nothing, Int, Nothing, Int]    = 
  ZSink.sum[Int]
val stringSum : ZSink[Any, Nothing, String, Nothing, Int] = 
  numericSum.contramap((x: String) => x.toInt)

val sum: ZIO[Any, Nothing, Int] =
  ZStream("1", "2", "3", "4", "5").run(stringSum)
// Output: 15
```

### dimap

A `dimap` is an extended `contramap` that additionally transforms sink's output:

```scala mdoc:silent:nest
// Convert its input to integers, do the computation and then convert them back to a string
val sumSink: ZSink[Any, Nothing, String, Nothing, String] =
  numericSum.dimap[String, String](_.toInt, _.toString)
  
val sum: ZIO[Any, Nothing, String] =
  ZStream("1", "2", "3", "4", "5").run(sumSink)
// Output: 15
```

### Collecting

To create a sink that collects all elements of a stream into a `Chunk[A]`, we can use `ZSink.collectAll`:

```scala mdoc:silent:nest
val stream    : UStream[Int]    = UStream(1, 2, 3, 4, 5)
val collection: UIO[Chunk[Int]] = stream.run(ZSink.collectAll[Int])
// Output: Chunk(1, 2, 3, 4, 5)
```

We can collect all elements into a `Set`:

```scala mdoc:silent:nest
val collectAllToSet: ZSink[Any, Nothing, Int, Nothing, Set[Int]] = ZSink.collectAllToSet[Int]
val stream: ZIO[Any, Nothing, Set[Int]] = ZStream(1, 3, 2, 3, 1, 5, 1).run(collectAllToSet)
// Output: Set(1, 3, 2, 5)
```

Or we can collect and merge them into a `Map[K, A]` using a merge function. In the following example, we use `(_:Int) % 3` to determine map keys and, we provide `_ + _` function to merge multiple elements with the same key:

```scala mdoc:silent:nest
val collectAllToMap: ZSink[Any, Nothing, Int, Nothing, Map[Int, Int]] = ZSink.collectAllToMap((_: Int) % 3)(_ + _)
val stream: ZIO[Any, Nothing, Map[Int, Int]] = ZStream(1, 3, 2, 3, 1, 5, 1).run(collectAllToMap)
// Output: Map(1 -> 3, 0 -> 6, 2 -> 7)
```

### Folding

Basic fold accumulation of received elements:

```scala mdoc:silent
ZSink.foldLeft[Int, Int](0)(_ + _)
```

A fold with short-circuiting has a termination predicate that determines the end of the folding process:

```scala mdoc:silent
ZStream.iterate(0)(_ + 1).run(
  ZSink.fold(0)(sum => sum <= 10)((acc, n: Int) => acc + n)
)
// Output: 15
```

## Concurrency

### Racing

We are able to `race` multiple sinks, they will run in parallel, and the one that wins will provide the result of our program:

```scala mdoc:invisible
case class Record()
```

```scala mdoc:silent
val kafkaSink: ZSink[Any, Throwable, Record, Record, Unit] =
  ZSink.foreach[Any, Throwable, Record](record => ZIO.effect(???))

val pulsarSink: ZSink[Any, Throwable, Record, Record, Unit] =
  ZSink.foreach[Any, Throwable, Record](record => ZIO.effect(???))

val res: ZSink[Any, Throwable, Record, Record, Unit] =
  kafkaSink race pulsarSink 
```

To determine which one succeeded, we should use the `ZSink#raceBoth` combinator, it returns an `Either` result.

## Leftovers

### Exposing Leftovers

A sink consumes a variable amount of `I` elements (zero or more) from the upstream. If the upstream is finite, we can expose leftover values by calling `ZSink#exposeLeftOver`. It returns a tuple that contains the result of the previous sink and its leftovers:

```scala mdoc:silent:nest
val s1: ZIO[Any, Nothing, (Chunk[Int], Chunk[Int])] =
  ZStream(1, 2, 3, 4, 5).run(
    ZSink.take(3).exposeLeftover
  )
// Output: (Chunk(1, 2, 3), Chunk(4, 5))


val s2: ZIO[Any, Nothing, (Option[Int], Chunk[Int])] =
  ZStream(1, 2, 3, 4, 5).run(
    ZSink.head[Int].exposeLeftover
  )
// Output: (Some(1), Chunk(2, 3, 4, 5))
```

### Dropping Leftovers

If we don't need leftovers, we can drop them by using `ZSink#dropLeftover`:

```scala mdoc:silent:nest
ZSink.take[Int](3).dropLeftover
```
