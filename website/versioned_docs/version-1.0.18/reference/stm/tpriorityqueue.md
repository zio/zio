---
id: tpriorityqueue
title: "TPriorityQueue"
---

A `TPriorityQueue[A]` is a mutable queue that can participate in STM transactions. A `TPriorityQueue` contains values of type `A` for which an `Ordering` is defined. Unlike a `TQueue`, `take` returns the highest priority value (the value that is first in the specified ordering) as opposed to the first value offered to the queue. The ordering of elements sharing the same priority when taken from the queue is not guaranteed.

## Creating a TPriorityQueue

You can create an empty `TPriorityQueue` using the `empty` constructor:

```scala
import zio._
import zio.stm._

val minQueue: STM[Nothing, TPriorityQueue[Int]] =
  TPriorityQueue.empty
```

Notice that a `TPriorityQueue` is created with an implicit `Ordering`. By default, `take` will return the value that is first in the specified ordering. For example, in a queue of events ordered by time the earliest event would be taken first. If you want a different behavior you can use a custom `Ordering`.

```scala
val maxQueue: STM[Nothing, TPriorityQueue[Int]] =
  TPriorityQueue.empty(Ordering[Int].reverse)
```

You can also create a `TPriorityQueue` initialized with specified elements using the `fromIterable` or `make` constructors". The `fromIterable` constructor takes a `Iterable` while the `make` constructor takes a variable arguments sequence of elements.

## Offering elements to a TPriorityQueue

You can offer elements to a `TPriorityQueue` using the `offer` or `offerAll` methods. The `offerAll` method is more efficient if you want to offer more than one element to the queue at the same time.

```scala
val queue: STM[Nothing, TPriorityQueue[Int]] =
  for {
    queue <- TPriorityQueue.empty[Int]
    _     <- queue.offerAll(List(2, 4, 6, 3, 5, 6))
  } yield queue
```

## Taking elements from a TPriorityQueue

Take an element from a `TPriorityQueue` using the `take`. `take` will semantically block until there is at least one value in the queue to take. You can also use `takeAll` to immediately take all values that are currently in the queue, or `takeUpTo` to immediately take up to the specified number of elements from the queue.

```scala
val sorted: STM[Nothing, Chunk[Int]] =
  for {
    queue  <- TPriorityQueue.empty[Int]
    _      <- queue.offerAll(List(2, 4, 6, 3, 5, 6))
    sorted <- queue.takeAll
  } yield sorted
```

You can also use `takeOption` method to take the first value from the queue if it exists without suspending or the `peek` method to observe the first element of the queue if it exists without removing it from the queue.

Sometimes you want to take a snapshot of the current state of the queue without modifying it. For this the `toChunk` combinator or its variants `toList` or `toVector` are extremely helpful. These will return an immutable collection that consists of all of the elements currently in the queue, leaving the state of the queue unchanged.

## Size of a TPriorityQueue

You can check the size of the `TPriorityQueue` using the `size` method:

```scala

val size: STM[Nothing, Int] =
  for {
    queue <- TPriorityQueue.empty[Int]
    _     <- queue.offerAll(List(2, 4, 6, 3, 5, 6))
    size  <- queue.size
  } yield size
```
