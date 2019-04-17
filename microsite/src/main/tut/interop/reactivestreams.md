---
layout: docs
section: interop
title:  "Reactive Streams"
---

# {{page.title}}

Checkout the `interop-reactiveStreams` module for inter-operation support.

### Reactive Streams `Producer` and `Subscriber`

**ZIO** integrates with [Reactive Streams](http://reactivestreams.org) by providing conversions from `zio.stream.Stream` to `org.reactivestreams.Publisher`
and from `zio.stream.Sink` to `org.reactivestreams.Subscriber` and vice versa. Simply import `import scalaz.zio.interop.reactiveStreams._` to make the 
conversions available.

### Examples

First, let's get a few imports out of the way.

```scala mdoc:silent
import org.reactivestreams.example.unicast._
import scalaz.zio._
import scalaz.zio.interop.reactiveStreams._
import scalaz.zio.stream._

val runtime = new DefaultRuntime {}
```

We use the following `Publisher` and `Subscriber` for the examples: 

```scala mdoc
val publisher = new RangePublisher(3, 10)
val subscriber = new SyncSubscriber[Int] {
  override protected def whenNext(v: Int): Boolean = {
    print(s"$v, ")
    true
  }
}
```

#### Publisher to Stream

A `Publisher` used as a `Stream` buffers up to `qSize` elements. If possible, `qSize` should be
a power of two for best performance. The default is 16.

```scala mdoc
val streamFromPublisher = publisher.toStream(qSize = 16)
runtime.unsafeRun(
  streamFromPublisher.run(Sink.collect[Integer])
)
```

#### Subscriber to Sink

When running a `Stream` to a `Subscriber`, a side channel is needed for signalling failures.
For this reason `toSink` returns a tuple of `Promise` and `Sink`. The `Promise` must be failed
on `Stream` failure. The type parameter on `toSink` is the error type of *the Stream*. 

```scala mdoc
val asSink = subscriber.toSink[Throwable]
val failingStream = Stream.range(3, 13) ++ Stream.fail(new RuntimeException("boom!"))
runtime.unsafeRun(
  asSink.flatMap { case (errorP, sink) =>
    failingStream.run(sink).catchAll(errorP.fail)
  }
)
```

#### Stream to Publisher

```scala mdoc
val stream = Stream.range(3, 13)
runtime.unsafeRun(
  stream.toPublisher.flatMap { publisher =>
    UIO(publisher.subscribe(subscriber))
  }
)
```

#### Sink to Subscriber

`toSubscriber` returns a `Subscriber` and an `IO` which completes with the result of running the 
`Sink` or the error if the `Publisher` fails.
A `Sink` used as a `Subscriber` buffers up to `qSize` elements. If possible, `qSize` should be
a power of two for best performance. The default is 16.

```scala mdoc
val sink = Sink.collect[Integer]
runtime.unsafeRun(
  sink.toSubscriber(qSize = 16).flatMap { case (subscriber, result) => 
    UIO(publisher.subscribe(subscriber)) *> result
  }
)
```
