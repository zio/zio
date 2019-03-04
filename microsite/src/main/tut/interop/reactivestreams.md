---
layout: docs
section: interop
title:  "Reactive Streams"
---

# {{page.title}}

Checkout `interop-reactiveStreams` module for inter-operation support.

### Reactive Streams `Producer` and `Subscriber`

**ZIO** integrates with [Reactive Streams](http:reactivestreams.org) by providing conversions from `zio.stream.Stream` to `org.reactivestreams.Publisher`
and from `zio.stream.Sink` to `org.reactivestreams.Subscriber`.

#### Example

```scala
import org.reactivestreams.{ Publisher, Subscriber }
import scalaz.zio.interop.reactiveStreams._
import scalaz.zio.stream.{ Sink, Stream }

val publisher: Publisher[Int] = Stream.fromIterable(List(1, 2, 3)).toPublisher

def subscriber[T]: Subscriber[T] = Sink.collect[T].toSubscriber()
```

