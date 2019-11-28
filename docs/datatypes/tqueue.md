---
id: datatypes_tqueue
title: "TQueue"
---

A `TQueue[A]` is a mutable queue that can participate in transactions in STM.

## Create a TQueue

Creating an empty `TQueue` with specified capacity:

```scala mdoc:silent
import zio._
import zio.stm._

val tQueue: STM[Nothing, TQueue[Int]] = TQueue.make[Int](5)
```

## Put element(s) to a TQueue

In order to put an element to a `TQueue`:

```scala mdoc:silent
import zio._
import zio.stm._

val tQueueOffer: UIO[TQueue[Int]] = (for {
  tQueue <- TQueue.make[Int](3)
  _      <- tQueue.offer(1)
} yield tQueue).commit
```

The specified element will be successfully added to a queue if the queue is not full.
It will wait for an empty slot in the queue otherwise.

Alternatively, you can provide a list of elements:

```scala mdoc:silent
import zio._
import zio.stm._

val tQueueOfferAll: UIO[TQueue[Int]] = (for {
  tQueue <- TQueue.make[Int](3)
  _      <- tQueue.offerAll(List(1, 2))
} yield tQueue).commit
```

## Retrieve element(s) from a TQueue

The first element of the queue can be obtained as follows:

```scala mdoc:silent
import zio._
import zio.stm._

val tQueueTake: UIO[Int] = (for {
  tQueue <- TQueue.make[Int](3)
  _      <- tQueue.offerAll(List(1, 2))
  res    <- tQueue.take
} yield res).commit
```

Note if the queue is empty it will block execution waiting for element you're asking for.

You can avoid that behavior by using `poll` method that will return an element if exists or `None` otherwise:

```scala mdoc:silent
import zio._
import zio.stm._

val tQueuePoll: UIO[Option[Int]] = (for {
  tQueue <- TQueue.make[Int](3)
  res    <- tQueue.poll
} yield res).commit
```

Retrieving first `n` elements of the queue:

```scala mdoc:silent
import zio._
import zio.stm._

val tQueueTakeUpTo: UIO[List[Int]] = (for {
  tQueue <- TQueue.make[Int](4)
  _      <- tQueue.offerAll(List(1, 2))
  res    <- tQueue.takeUpTo(3)
} yield res).commit
```

All elements of the queue can be obtained as follows:

```scala mdoc:silent
import zio._
import zio.stm._

val tQueueTakeAll: UIO[List[Int]] = (for {
  tQueue <- TQueue.make[Int](4)
  _      <- tQueue.offerAll(List(1, 2))
  res    <- tQueue.takeAll
} yield res).commit
```

## Size of a TQueue

The number of elements in the queue can be obtained as follows:

```scala mdoc:silent
import zio._
import zio.stm._

val tQueueSize: UIO[Int] = (for {
  tQueue <- TQueue[Int].make(3)
  _      <- tQueue.offerAll(List(1, 2))
  size   <- tQueue.size
} yield size).commit
```
