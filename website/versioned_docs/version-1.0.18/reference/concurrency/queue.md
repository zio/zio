---
id: queue
title: "Queue"
---

`Queue` is a lightweight in-memory queue built on ZIO with composable and transparent back-pressure. It is fully asynchronous (no locks or blocking), purely-functional and type-safe.

A `Queue[A]` contains values of type `A` and has two basic operations: `offer`, which places an `A` in the `Queue`, and `take` which removes and returns the oldest value in the `Queue`.

```scala
import zio._

val res: UIO[Int] = for {
  queue <- Queue.bounded[Int](100)
  _ <- queue.offer(1)
  v1 <- queue.take
} yield v1
```

## Creating a queue

A `Queue` can be bounded (with a limited capacity) or unbounded.

There are several strategies to process new values when the queue is full:

- The default `bounded` queue is back-pressured: when full, any offering fiber will be suspended until the queue is able to add the item;
- A `dropping` queue will drop new items when the queue is full;
- A `sliding` queue will drop old items when the queue is full.

To create a back-pressured bounded queue:
```scala
val boundedQueue: UIO[Queue[Int]] = Queue.bounded[Int](100)
```

To create a dropping queue:
```scala
val droppingQueue: UIO[Queue[Int]] = Queue.dropping[Int](100)
```

To create a sliding queue:
```scala
val slidingQueue: UIO[Queue[Int]] = Queue.sliding[Int](100)
```

To create an unbounded queue:
```scala
val unboundedQueue: UIO[Queue[Int]] = Queue.unbounded[Int]
```

## Adding items to a queue

The simplest way to add a value to the queue is `offer`:

```scala
val res1: UIO[Unit] = for {
  queue <- Queue.bounded[Int](100)
  _ <- queue.offer(1)
} yield ()
```

When using a back-pressured queue, offer might suspend if the queue is full: you can use `fork` to wait in a different fiber.

```scala
val res2: UIO[Unit] = for {
  queue <- Queue.bounded[Int](1)
  _ <- queue.offer(1)
  f <- queue.offer(1).fork // will be suspended because the queue is full
  _ <- queue.take
  _ <- f.join
} yield ()
```

It is also possible to add multiple values at once with `offerAll`:

```scala
val res3: UIO[Unit] = for {
  queue <- Queue.bounded[Int](100)
  items = Range.inclusive(1, 10).toList
  _ <- queue.offerAll(items)
} yield ()
```

## Consuming Items from a Queue

The `take` operation removes the oldest item from the queue and returns it. If the queue is empty, this will suspend, and resume only when an item has been added to the queue. As with `offer`, you can use `fork` to wait for the value in a different fiber.

```scala
val oldestItem: UIO[String] = for {
  queue <- Queue.bounded[String](100)
  f <- queue.take.fork // will be suspended because the queue is empty
  _ <- queue.offer("something")
  v <- f.join
} yield v
```

You can consume the first item with `poll`. If the queue is empty you will get `None`, otherwise the top item will be returned wrapped in `Some`.

```scala
val polled: UIO[Option[Int]] = for {
  queue <- Queue.bounded[Int](100)
  _ <- queue.offer(10)
  _ <- queue.offer(20)
  head <- queue.poll
} yield head
```

You can consume multiple items at once with `takeUpTo`. If the queue doesn't have enough items to return, it will return all the items without waiting for more offers.

```scala
val taken: UIO[List[Int]] = for {
  queue <- Queue.bounded[Int](100)
  _ <- queue.offer(10)
  _ <- queue.offer(20)
  list  <- queue.takeUpTo(5)
} yield list
```

Similarly, you can get all items at once with `takeAll`. It also returns without waiting (an empty list if the queue is empty).

```scala
val all: UIO[List[Int]] = for {
  queue <- Queue.bounded[Int](100)
  _ <- queue.offer(10)
  _ <- queue.offer(20)
  list  <- queue.takeAll
} yield list
```

## Shutting Down a Queue

It is possible with `shutdown` to interrupt all the fibers that are suspended on `offer*` or `take*`. It will also empty the queue and make all future calls to `offer*` and `take*` terminate immediately.

```scala
val takeFromShutdownQueue: UIO[Unit] = for {
  queue <- Queue.bounded[Int](3)
  f <- queue.take.fork
  _ <- queue.shutdown // will interrupt f
  _ <- f.join // Will terminate
} yield ()
```

You can use `awaitShutdown` to execute an effect when the queue is shut down. This will wait until the queue is shut down. If the queue is already shutdown, it will resume right away.

```scala
val awaitShutdown: UIO[Unit] = for {
  queue <- Queue.bounded[Int](3)
  p <- Promise.make[Nothing, Boolean]
  f <- queue.awaitShutdown.fork
  _ <- queue.shutdown
  _ <- f.join
} yield ()
```

## Transforming queues

A `Queue[A]` is in fact a type alias for `ZQueue[Any, Any, Nothing, Nothing, A, A]`.
The signature for the expanded version is:
```scala
trait ZQueue[RA, RB, EA, EB, A, B]
```

Which is to say:
- The queue may be offered values of type `A`. The enqueueing operations require an environment of type `RA` and may fail with errors of type `EA`;
- The queue will yield values of type `B`. The dequeueing operations require an environment of type `RB` and may fail with errors of type `EB`.

Note how the basic `Queue[A]` cannot fail or require any environment for any of its operations.

With separate type parameters for input and output, there are rich composition opportunities for queues:

### ZQueue#map

The output of the queue may be mapped:

```scala
val mapped: UIO[String] = 
  for {
    queue  <- Queue.bounded[Int](3)
    mapped = queue.map(_.toString)
    _      <- mapped.offer(1)
    s      <- mapped.take
  } yield s
```

### ZQueue#mapM

We may also use an effectful function to map the output. For example,
we could annotate each element with the timestamp at which it was dequeued:

```scala
import java.util.concurrent.TimeUnit
import zio.clock._

val currentTimeMillis = currentTime(TimeUnit.MILLISECONDS)

val annotatedOut: UIO[ZQueue[Any, Clock, Nothing, Nothing, String, (Long, String)]] =
  for {
    queue <- Queue.bounded[String](3)
    mapped = queue.mapM { el =>
      currentTimeMillis.map((_, el))
    }
  } yield mapped
```

### ZQueue#contramapM

Similarly to `mapM`, we can also apply an effectful function to
elements as they are enqueued. This queue will annotate the elements
with their enqueue timestamp:

```scala
val annotatedIn: UIO[ZQueue[Clock, Any, Nothing, Nothing, String, (Long, String)]] =
  for {
    queue <- Queue.bounded[(Long, String)](3)
    mapped = queue.contramapM { el: String =>
      currentTimeMillis.map((_, el))
    }
  } yield mapped
```

This queue has the same type as the previous one, but the timestamp is
attached to the elements when they are enqueued. This is reflected in
the type of the environment required by the queue for enqueueing.

To complete this example, we could combine this queue with `mapM` to
compute the time that the elements stayed in the queue:

```scala
import zio.duration._

val timeQueued: UIO[ZQueue[Clock, Clock, Nothing, Nothing, String, (Duration, String)]] =
  for {
    queue <- Queue.bounded[(Long, String)](3)
    enqueueTimestamps = queue.contramapM { el: String =>
      currentTimeMillis.map((_, el))
    }
    durations = enqueueTimestamps.mapM { case (enqueueTs, el) =>
      currentTimeMillis
        .map(dequeueTs => ((dequeueTs - enqueueTs).millis, el))
    }
  } yield durations
```

### ZQueue#bothWith

We may also compose two queues together into a single queue that
broadcasts offers and takes from both of the queues:

```scala
val fromComposedQueues: UIO[(Int, String)] = 
  for {
    q1       <- Queue.bounded[Int](3)
    q2       <- Queue.bounded[Int](3)
    q2Mapped =  q2.map(_.toString)
    both     =  q1.bothWith(q2Mapped)((_, _))
    _        <- both.offer(1)
    iAndS    <- both.take
    (i, s)   =  iAndS
  } yield (i, s)
```

## Additional Resources

- [ZIO Queue Talk by John De Goes @ ScalaWave 2018](https://www.slideshare.net/jdegoes/zio-queue)
- [ZIO Queue Talk by Wiem Zine El Abidine @ PSUG 2018](https://www.slideshare.net/wiemzin/psug-zio-queue)
- [Elevator Control System using ZIO](https://medium.com/@wiemzin/elevator-control-system-using-zio-c718ae423c58)
- [Scalaz 8 IO vs Akka (typed) actors vs Monix](https://blog.softwaremill.com/scalaz-8-io-vs-akka-typed-actors-vs-monix-part-1-5672657169e1)
