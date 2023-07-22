---
id: cyclicbarrier
title: "CyclicBarrier"
---

A synchronization aid that allows a set of fibers to all wait for each other to reach a common barrier point.

CyclicBarriers are useful in programs involving a fixed sized party of fibers that must occasionally wait for each
other. The barrier is called cyclic because it can be re-used after the waiting fibers are released.

A CyclicBarrier supports an optional action command that is run once per barrier point, after the last fiber in the
party arrives, but before any fibers are released. This barrier action is useful for updating shared-state before any of
the parties continue.

## Operations

### Creation

| Method                                                      | Definition                                       |
|-------------------------------------------------------------|--------------------------------------------------|
| `make(parties: Int): UIO[CyclicBarrier]`                    | Makes an `CyclicBarrier` with n parties          | 
| `make(parties: Int, action: UIO[Any]): UIO[CyclicBarrier]`  | Makes an `CyclicBarrier` with parties and action | 

### Use

| Method                   | Definition                                                                                  |
|--------------------------|---------------------------------------------------------------------------------------------|
| `parties: Int`           | The number of parties required to trip this barrier.                                        |
| `waiting: UIO[Int]`      | The number of parties currently waiting at the barrier.                                     |
| `await: IO[Unit, Int]`   | Waits until all parties have invoked await on this barrier. Fails if the barrier is broken. |
| `reset: UIO[Unit]`       | Resets the barrier to its initial state. Breaks any waiting party.                          |
| `isBroken: UIO[Boolean]` | Queries if this barrier is in a broken state.                                               |

## Example Usage

Construction:

```scala
import zio.concurrent.CyclicBarrier

for {
  barrier  <- CyclicBarrier.make(100)
  isBroken <- barrier.isBroken  
  waiting  <- barrier.waiting
} yield assert(!isBroken && waiting == 0)
```

Releasing the barrier:

```scala
import zio.concurrent.CyclicBarrier
import zio._

for {
  barrier <- CyclicBarrier.make(2)
  f1      <- barrier.await.fork
  _       <- f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
  f2      <- barrier.await.fork
  ticket1 <- f1.join
  ticket2 <- f2.join
} yield assert(ticket1 == 1 && ticket2 == 0)
```

Releasing the barrier and performing the action:

```scala
import zio.concurrent.CyclicBarrier
import zio._

for {
  promise <- Promise.make[Nothing, Unit]
  barrier <- CyclicBarrier.make(2, promise.succeed(()))
  f1      <- barrier.await.fork
  _       <- f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
  f2      <- barrier.await.fork
  _       <- f1.join
  _       <- f2.join
  isComplete <- promise.isDone
} yield assert(isComplete)
```

Releases the barrier and cycles:

```scala
import zio.concurrent.CyclicBarrier

for {
  barrier <- CyclicBarrier.make(2)
  f1      <- barrier.await.fork
  _       <- f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
  f2      <- barrier.await.fork
  ticket1 <- f1.join
  ticket2 <- f2.join
  f3      <- barrier.await.fork
  _       <- f3.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
  f4      <- barrier.await.fork
  ticket3 <- f3.join
  ticket4 <- f4.join
} yield assert(ticket1 == 1 && ticket2 == 0 && ticket3 == 1 && ticket4 == 0)
```

Breaks on reset:

```scala
import zio.concurrent.CyclicBarrier

for {
  barrier <- CyclicBarrier.make(100)
  f1      <- barrier.await.fork
  f2      <- barrier.await.fork
  _       <- f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
  _       <- f2.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
  _       <- barrier.reset
  res1    <- f1.await
  res2    <- f2.await
} yield ()
```

Breaks on party interruption:

```scala
import zio.concurrent.CyclicBarrier
import zio._
import zio.duration._
import zio.test.environment.TestClock

for {
  barrier   <- CyclicBarrier.make(100)
  f1        <- barrier.await.timeout(1.second).fork
  f2        <- barrier.await.fork
  _         <- f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
  _         <- f2.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
  isBroken1 <- barrier.isBroken
  _         <- TestClock.adjust(1.second)
  isBroken2 <- barrier.isBroken
  res1      <- f1.await
  res2      <- f2.await
} yield assert(!isBroken1 && isBroken2)
```
