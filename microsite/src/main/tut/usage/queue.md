---
layout: docs
section: usage
title:  "Queues"
---

# Queue

`Queue` is a lightweight in-memory queue built on ZIO with composable and transparent back-pressure. It is fully asynchronous (no locks or blocking), purely-functional and type-safe.

A `Queue[A]` contains values of type `A` and has two basic operations: `offer` which places an `A` in the `Queue`, and `take` which removes and returns the oldest value in the `Queue`.

```tut:silent
import scalaz.zio._

val res: IO[Nothing, Int] = for {
  queue <- Queue.bounded[Int](100)
  _ <- queue.offer(1)
  v1 <- queue.take
} yield v1
```

## Creating a queue

A `Queue` can be bounded (with a limited capacity) or unbounded.
There are various strategies to process new values when the queue is full:
- the default `bounded` queue is back-pressured: when full, any offering fiber will be suspended until the queue is able to add the item
- a `dropping` queue will drop new items when the queue is full
- a `sliding` queue will drop old items when the queue is full

To create a back-pressured bounded queue:
```tut:silent
val queue: IO[Nothing, Queue[Int]] = Queue.bounded[Int](100)
```

To create a dropping queue:
```tut:silent
val queue: IO[Nothing, Queue[Int]] = Queue.dropping[Int](100)
```

To create a sliding queue:
```tut:silent
val queue: IO[Nothing, Queue[Int]] = Queue.sliding[Int](100)
```

To create an unbounded queue:
```tut:silent
val queue: IO[Nothing, Queue[Int]] = Queue.unbounded[Int]
```

## Adding items to a queue

The simplest way to add a value to the queue is `offer`:
```tut:silent
val res: IO[Nothing, Unit] = for {
  queue <- Queue.bounded[Int](100)
  _ <- queue.offer(1)
} yield ()
```

When using a back-pressured queue, offer might be suspended if the queue is full: you can use `fork` to wait in a different fiber.
```tut:silent
val res: IO[Nothing, Unit] = for {
  queue <- Queue.bounded[Int](1)
  _ <- queue.offer(1)
  f <- queue.offer(1).fork // will be suspended because the queue is full
  _ <- queue.take
  _ <- f.join
} yield ()
```

It is also possible to add multiple values at once with `offerAll`:
```tut:silent
val res: IO[Nothing, Unit] = for {
  queue <- Queue.bounded[Int](100)
  items = Range.inclusive(1, 10).toList
  _ <- queue.offerAll(items)
} yield ()
```

## Consuming items from a queue

`take` removes the oldest item from the queue and returns it. If the queue is empty, this will return a computation that resumes when an item has been added to the queue: you can use `fork` to wait for an item in a different fiber.
```tut:silent
val res: IO[Nothing, String] = for {
  queue <- Queue.bounded[String](100)
  f <- queue.take.fork // will be suspended because the queue is empty
  _ <- queue.offer("something")
  v <- f.join
} yield v
```

You can consume multiple items at once with `takeUpTo`. If the queue doesn't have enough items to return, it will return all the items without waiting for more offers.
```tut:silent
val res: IO[Nothing, List[Int]] = for {
  queue <- Queue.bounded[Int](100)
  _ <- queue.offer(10)
  _ <- queue.offer(20)
  list  <- queue.takeUpTo(5)
} yield list
```

Similarly, you can get all items at once with `takeAll`. It also returns without waiting (an empty list if the queue is empty).
```tut:silent
val res: IO[Nothing, List[Int]] = for {
  queue <- Queue.bounded[Int](100)
  _ <- queue.offer(10)
  _ <- queue.offer(20)
  list  <- queue.takeAll
} yield list
```

## Shutting down a queue

It is possible with `shutdown` to interrupt all the fibers that are suspended on `offer*` or `take*`. It will also empty the queue and make all future calls to `offer*` and `take*` terminate immediately.

```tut:silent
val res: IO[Nothing, Unit] = for {
  queue <- Queue.bounded[Int](3)
  f <- queue.take.fork
  _ <- queue.shutdown // will interrupt f
  _ <- f.join // will throw
} yield ()
```

You can use `awaitShutdown` to execute an action when the queue is shut down. This will wait until the queue is shut down. If the queue is already shutdown, it will resume right away.
```tut:silent
val res: IO[Nothing, Unit] = for {
  queue <- Queue.bounded[Int](3)
  p <- Promise.make[Nothing, Boolean]
  f <- queue.awaitShutdown.fork
  _ <- queue.shutdown
  _ <- f.join
} yield ()
```

## Additional resources and examples

- [ZIO Queue Talk by John De Goes @ ScalaWave 2018](https://www.slideshare.net/jdegoes/zio-queue)
- [ZIO Queue Talk by Wiem Zine Elabidine @ PSUG 2018](https://www.slideshare.net/wiemzin/psug-zio-queue)
- [Elevator Control System using ZIO](https://medium.com/@wiemzin/elevator-control-system-using-zio-c718ae423c58)
- [Scalaz 8 IO vs Akka (typed) actors vs Monix](https://blog.softwaremill.com/scalaz-8-io-vs-akka-typed-actors-vs-monix-part-1-5672657169e1)