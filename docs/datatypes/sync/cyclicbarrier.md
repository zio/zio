---
id: cyclicbarrier
title: "CyclicBarrier"
---

A synchronization aid that allows a set of fibers to all wait for each other to reach a common barrier point.

CyclicBarriers are useful in programs involving a fixed sized party of fibers that must occasionally wait for each other. The barrier is called cyclic because it can be re-used after the waiting fibers are released.

## Operations

### Creation

To create a `CyclicBarrier` we must provide the number of parties, and we can also provide an optional action:

1. **number of parties**— The fibers that need to synchronize their execution are called _parties_. This number denotes how many parties must occasionally wait for each other. In other words, it specifies the number of parties required to trip the barrier.
2. **action**— An optional action command that is run once per barrier point, after the last fiber in the party arrives, but before any fibers are released. This action is useful for updating the shared state before any of the parties continue.

```scala
object CyclicBarrier {
  def make(parties: Int)                  : UIO[CyclicBarrier] = ???
  def make(parties: Int, action: UIO[Any]): UIO[CyclicBarrier] = ???
}
```

### Use

| Method                   | Definition                                                                                  |
|--------------------------|---------------------------------------------------------------------------------------------|
| `parties: Int`           | The number of parties required to trip this barrier.                                        |
| `waiting: UIO[Int]`      | The number of parties currently waiting at the barrier.                                     |
| `await: IO[Unit, Int]`   | Waits until all parties have invoked await on this barrier. Fails if the barrier is broken. |
| `reset: UIO[Unit]`       | Resets the barrier to its initial state. Breaks any waiting party.                          |
| `isBroken: UIO[Boolean]` | Queries if this barrier is in a broken state.                                               |

In the following example, we started three tasks, each one has a different working time, but they won't return until the other parties finished their jobs:

```scala mdoc:compile-only
import zio._
import zio.concurrent._

object MainApp extends ZIOAppDefault {
  def task(name: String) =
    for {
      b <- ZIO.service[CyclicBarrier]
      _ <- ZIO.debug(s"task-$name: started my job right now!")
      d <- Random.nextLongBetween(1000, 10000)
      _ <- ZIO.sleep(Duration.fromMillis(d))
      _ <- ZIO.debug(s"task-$name: finished my job and waiting for other parties to finish their jobs.")
      _ <- b.await
      _ <- ZIO.debug(s"task-$name: the barrier is just broken, so I'm going to exit immediately!")
    } yield ()

  def run =
    for {
      b    <- CyclicBarrier.make(3)
      tasks = task("1") <&> task("2") <&> task("3")
      _    <- tasks.provideService(b)
    } yield ()
}
```

## Example Usage

Construction:

```scala mdoc:silent
import zio.concurrent.CyclicBarrier

for {
  barrier  <- CyclicBarrier.make(100)
  isBroken <- barrier.isBroken  
  waiting  <- barrier.waiting
} yield assert(!isBroken && waiting == 0)
```

Releasing the barrier:

```scala mdoc:silent
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

```scala mdoc:silent
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

```scala mdoc:silent
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

```scala mdoc:silent
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

```scala mdoc:silent
import zio.concurrent.CyclicBarrier
import zio._
import zio.test.TestClock

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
