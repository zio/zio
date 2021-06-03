---
id: zsink
title: "ZSink"
---

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

### From Success and Failure

Similar to the `ZStream` data type, we can create a `ZSink` using `fail` and `succeed` methods.

A sink that doesn't consume any element of type `String` from its upstream and successes with a value of `Int` type:

```scala mdoc:silent:nest
val succeed: ZSink[Any, Nothing, String, String, Int] = ZSink.succeed[String, Int](5)
```

A sink that doesn't consume any element of type `Int` from its upstream and fails with a message of `String` type:

```scala mdoc:silent:nest
val failed : ZSink[Any, String, Int, Int, Nothing] = ZSink.fail[String, Int]("fail!")
```

### Collecting Elements

To collect all elements into a `Chunk[A]`:

```scala mdoc:silent:nest
val stream    : UStream[Int]    = UStream(1, 2, 3, 4, 5)
val collection: UIO[Chunk[Int]] = stream.run(ZSink.collectAll[Int])
// Output: Chunk(1, 2, 3, 4, 5)
```

Collecting the first element into an option (returns `None` for empty streams):

```scala mdoc:silent
ZSink.head[Int]
```

Ignoring all the input, used in implementation of `stream.runDrain`:

```scala mdoc:silent
ZSink.drain
```

Sink that intentionally fails with given type:

```scala mdoc:silent
ZSink.fail("Boom")
```

Basic fold accumulation of received elements:

```scala mdoc:silent
ZSink.foldLeft[Int, Int](0)(_ + _)
```

A fold with short circuiting:

```scala mdoc:silent
ZSink.fold(0)(sum => sum >= 10)((acc, n: Int) => acc + n)
```

## Transforming sinks

Having created the sink, we can transform it with provided operations.

Running two sinks in parallel and returning the one that completed earlier:
```scala mdoc:silent
Sink.foldLeft[Int, Int](0)(_ + _).race(Sink.head[Int])
```

For transforming given input into some sink we can use `contramap` which is `C => A` where `C` is input type and `A` is sink elements type:

```scala mdoc:silent
Sink.collectAll[String].contramap[Int](_.toString + "id")
```

A `dimap` is an extended `contramap` that additionally transforms sink's output:

```scala mdoc:silent
Sink.collectAll[String].dimap[Int, Chunk[String]](_.toString + "id", _.take(10))
```
