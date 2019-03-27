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

#### Publisher to Stream

A `Publisher` used as a `Stream` buffers up to `qSize` elements. If possible, `qSize` should be
a power of two for best performance. The default is 16.

```tut
import org.reactivestreams.Publisher
import org.reactivestreams.example.unicast.RangePublisher
import scalaz.zio.interop.reactiveStreams._

val publisher = new RangePublisher(3, 10)
publisher.toStream(qSize = 16)
```

#### Subscriber to Sink

When running a Stream to a Subscriber, a side channel is needed for signalling failures.
For this reason `toSink` returns a tuple of `Promise` and `Sink`. The `Promise` must be failed
on `Stream` failure.

```tut
import org.reactivestreams.example.unicast.SyncSubscriber
import scalaz.zio.interop.reactiveStreams._
import scalaz.zio.stream.Stream

val subscriber = new SyncSubscriber[Int] {
  override protected def whenNext(v: Int): Boolean = {
    println(v)
    true
  }
}
val stream = Stream.range(3, 13)
val errorAndSink = subscriber.toSink

errorAndSink.flatMap { case (errorP, sink) =>
  stream.run(sink).catchAll(errorP.fail).void
}
```

#### Stream to Publisher

```tut
import scalaz.zio.stream.Stream
import scalaz.zio.interop.reactiveStreams._

val stream = Stream.range(3, 13)
stream.toPublisher
```

#### Sink to Subscriber

A `Sink` used as a `Subscriber` buffers up to `qSize` elements. If possible, `qSize` should be
a power of two for best performance. The default is 16.

```tut
import scalaz.zio.stream.Sink
import scalaz.zio.interop.reactiveStreams._

val sink = Sink.collect[Int]
sink.toSubscriber(qSize = 16)
```

