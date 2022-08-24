---
id: index
title: "Introduction to ZSink"
---

A `ZSink[R, E, I, L, Z]` is used to consume elements produced by a `ZStream`. You can think of a sink as a function that will consume a variable amount of `I` elements (could be 0, 1, or many!), might fail with an error of type `E`, and will eventually yield a value of type `Z` together with a remainder of type `L` as leftover.

To consume a stream using `ZSink` we can pass `ZSink` to the `ZStream#run` function:


```scala mdoc:silent
import zio._
import zio.stream._

val stream = ZStream.fromIterable(1 to 1000)
val sink   = ZSink.sum[Int]
val sum    = stream.run(sink)
```
