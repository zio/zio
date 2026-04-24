---
id: operations
title: "Sink Operations"
---

Having created the sink, we can transform it with provided operations.

## contramap

Contramap is a simple combinator to change the domain of an existing function. While _map_ changes the co-domain of a function, the _contramap_ changes the domain of a function. So the _contramap_ takes a function and maps over its input.

This is useful when we have a fixed output, and our existing function cannot consume those outputs. So we can use _contramap_ to create a new function that can consume that fixed output. Assume we have a `ZSink.sum` that sums incoming numeric values, but we have a `ZStream` of `String` values. We can convert the `ZSink.sum` to a sink that can consume `String` values;

```scala mdoc:silent:nest
import zio._
import zio.stream._

val numericSum: ZSink[Any, Nothing, Int, Nothing, Int]    = 
  ZSink.sum[Int]
val stringSum : ZSink[Any, Nothing, String, Nothing, Int] = 
  numericSum.contramap((x: String) => x.toInt)

val sum: ZIO[Any, Nothing, Int] =
  ZStream("1", "2", "3", "4", "5").run(stringSum)
// Output: 15
```

## dimap

A `dimap` is an extended `contramap` that additionally transforms sink's output:

```scala mdoc:silent:nest
import zio._
import zio.stream._

// Convert its input to integers, do the computation and then convert them back to a string
val sumSink: ZSink[Any, Nothing, String, Nothing, String] =
  numericSum.dimap[String, String](_.toInt, _.toString)
  
val sum: ZIO[Any, Nothing, String] =
  ZStream("1", "2", "3", "4", "5").run(sumSink)
// Output: 15
```

## Filtering

Sinks have `ZSink#filterInput` for filtering incoming elements:

```scala mdoc:silent:nest
import zio._
import zio.stream._

ZStream(1, -2, 0, 1, 3, -3, 4, 2, 0, 1, -3, 1, 1, 6)
  .transduce(
    ZSink
      .collectAllN[Int](3)
      .filterInput[Int](_ > 0)
  )
// Output: Chunk(Chunk(1,1,3),Chunk(4,2,1),Chunk(1,1,6),Chunk())
```
