---
id: datatypes_sink
title:  "Sink"
---

A `ZSink[R, E, A, B]` is used to consume elements produced by a stream.
You can think of this sink as a function that will consume a variable 
amount of `A` elements (could be 0, 1, or many!), might fail with an
error of type `E`, and will eventually yield a value of type `B`.

A `ZSink` is passed to `ZStream#run` as an argument:

```scala mdoc:silent
import zio._
import zio.stream._

val stream = ZStream.fromIterable(1 to 1000)

val sink = ZSink.sum[Int]

stream.run(sink)
```

## Creating sinks

The `zio.stream` provides numerous kinds of sinks to use.

Collecting all elements into `Chunk[A]`:

```scala mdoc:silent
ZSink.collectAll[Int]
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

For transforming given input into some sink we can use `contramap` which
is `C => A` where `C` is input type and `A` is sink elements type:

```scala mdoc:silent
Sink.collectAll[String].contramap[Int](_.toString + "id")
```

A `dimap` is an extended `contramap` that additionally transforms sink's output:

```scala mdoc:silent
Sink.collectAll[String].dimap[Int, Chunk[String]](_.toString + "id", _.take(10))
```
