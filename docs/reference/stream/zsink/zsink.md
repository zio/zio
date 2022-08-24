---
id: zsink
title: "ZSink"
---

```scala mdoc:invisible
import zio._
import zio.stream._
import zio.Console._
import java.io.IOException
import java.nio.file.{Path, Paths}
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


### Filtering

Sinks have `ZSink#filterInput` for filtering incoming elements:

```scala mdoc:silent:nest
ZStream(1, -2, 0, 1, 3, -3, 4, 2, 0, 1, -3, 1, 1, 6)
  .transduce(
    ZSink
      .collectAllN[Int](3)
      .filterInput[Int](_ > 0)
  )
// Output: Chunk(Chunk(1,1,3),Chunk(4,2,1),Chunk(1,1,6),Chunk())
```


## Concurrency and Parallelism

### Parallel Zipping

Like `ZStream`, two `ZSink` can be zipped together. Both of them will be run in parallel, and their results will be combined in a tuple:

```scala mdoc:invisible
case class Record()
```

```scala mdoc:silent:nest
val kafkaSink: ZSink[Any, Throwable, Record, Record, Unit] =
  ZSink.foreach[Any, Throwable, Record](record => ZIO.attempt(???))

val pulsarSink: ZSink[Any, Throwable, Record, Record, Unit] =
  ZSink.foreach[Any, Throwable, Record](record => ZIO.attempt(???))

val stream: ZSink[Any, Throwable, Record, Record, Unit] =
  kafkaSink zipPar pulsarSink 
```

### Racing

We are able to `race` multiple sinks, they will run in parallel, and the one that wins will provide the result of our program:

```scala mdoc:silent:nest
val stream: ZSink[Any, Throwable, Record, Record, Unit] =
  kafkaSink race pulsarSink 
```

To determine which one succeeded, we should use the `ZSink#raceBoth` combinator, it returns an `Either` result.

## Leftovers

### Collecting Leftovers

A sink consumes a variable amount of `I` elements (zero or more) from the upstream. If the upstream is finite, we can collect leftover values by calling `ZSink#collectLeftover`. It returns a tuple that contains the result of the previous sink and its leftovers:

```scala mdoc:silent:nest
val s1: ZIO[Any, Nothing, (Chunk[Int], Chunk[Int])] =
  ZStream(1, 2, 3, 4, 5).run(
    ZSink.take(3).collectLeftover
  )
// Output: (Chunk(1, 2, 3), Chunk(4, 5))


val s2: ZIO[Any, Nothing, (Option[Int], Chunk[Int])] =
  ZStream(1, 2, 3, 4, 5).run(
    ZSink.head[Int].collectLeftover
  )
// Output: (Some(1), Chunk(2, 3, 4, 5))
```

### Ignoring Leftovers

If we don't need leftovers, we can drop them by using `ZSink#ignoreLeftover`:

```scala mdoc:silent:nest
ZSink.take[Int](3).ignoreLeftover
```
