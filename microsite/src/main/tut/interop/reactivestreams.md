---
layout: docs
section: interop
title:  "Reactive Streams"
---

# {{page.title}}

Checkout `interop-reactiveStreams` module for inter-operation support.

### Reactive Streams `Producer` and `Subscriber`

**ZIO** integrates with [Reactive Streams](http://reactivestreams.org) by providing conversions from `zio.stream.Stream` to `org.reactivestreams.Publisher`
and from `zio.stream.Sink` to `org.reactivestreams.Subscriber` and vice versa.

#### Example

```scala
import org.reactivestreams.{ Publisher, Subscriber }
import scalaz.zio.{ Task, UIO }
import scalaz.zio.interop.reactiveStreams._
import scalaz.zio.stream.{ Sink, Stream }

val publisher: UIO[Publisher[Int]] = Stream.fromIterable(List(1, 2, 3)).toPublisher

def subscriber[T]: UIO[(Subscriber[T], Task[List[T]])] = Sink.collect[T].toSubscriber()
```

