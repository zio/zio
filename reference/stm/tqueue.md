# TQueue

> A `TQueue[A]` is a mutable queue that can participate in transactions in STM.

A `TQueue[A]` is a mutable queue that can participate in transactions in STM.

## Create a TQueue

Creating an empty bounded `TQueue` with specified capacity:

```scala

val tQueueBounded: STM[Nothing, TQueue[Int]] = TQueue.bounded[Int](5)
```

Creating an empty unbounded `TQueue`:

```scala

val tQueueUnbounded: STM[Nothing, TQueue[Int]] = TQueue.unbounded[Int]
```

## Put element(s) in a TQueue

In order to put an element to a `TQueue`:

```scala

val tQueueOffer: UIO[TQueue[Int]] = (for {
  tQueue <- TQueue.bounded[Int](3)
  _      <- tQueue.offer(1)
} yield tQueue).commit
```

The specified element will be successfully added to a queue if the queue is not full.
It will wait for an empty slot in the queue otherwise.

Alternatively, you can provide a list of elements:

```scala

val tQueueOfferAll: UIO[TQueue[Int]] = (for {
  tQueue <- TQueue.bounded[Int](3)
  _      <- tQueue.offerAll(List(1, 2))
} yield tQueue).commit
```

## Retrieve element(s) from a TQueue

The first element of the queue can be obtained as follows:

```scala

val tQueueTake: UIO[Int] = (for {
  tQueue <- TQueue.bounded[Int](3)
  _      <- tQueue.offerAll(List(1, 2))
  res    <- tQueue.take
} yield res).commit
```

In case the queue is empty it will block execution waiting for the element you're asking for.

This behavior can be avoided by using `poll` method that will return an element if exists or `None` otherwise:

```scala

val tQueuePoll: UIO[Option[Int]] = (for {
  tQueue <- TQueue.bounded[Int](3)
  res    <- tQueue.poll
} yield res).commit
```

Retrieving first `n` elements of the queue:

```scala

val tQueueTakeUpTo: UIO[Chunk[Int]] = (for {
  tQueue <- TQueue.bounded[Int](4)
  _      <- tQueue.offerAll(List(1, 2))
  res    <- tQueue.takeUpTo(3)
} yield res).commit
```

All elements of the queue can be obtained as follows:

```scala

val tQueueTakeAll: UIO[Chunk[Int]] = (for {
  tQueue <- TQueue.bounded[Int](4)
  _      <- tQueue.offerAll(List(1, 2))
  res    <- tQueue.takeAll
} yield res).commit
```

## Size of a TQueue

The number of elements in the queue can be obtained as follows:

```scala

val tQueueSize: UIO[Int] = (for {
  tQueue <- TQueue.bounded[Int](3)
  _      <- tQueue.offerAll(List(1, 2))
  size   <- tQueue.size
} yield size).commit
```
