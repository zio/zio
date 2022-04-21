---
id: cyclicbarrier
title: "CyclicBarrier"
---

A synchronization aid that allows a set of fibers to all wait for each other to reach a common barrier point.

CyclicBarriers are useful in programs involving a fixed sized party of fibers that must occasionally wait for each other. The barrier is called cyclic because it can be re-used after the waiting fibers are released.

## Creation

To create a `CyclicBarrier` we must provide the number of parties, and we can also provide an optional action:

1. **number of parties**— The fibers that need to synchronize their execution are called _parties_. This number denotes how many parties must occasionally wait for each other. In other words, it specifies the number of parties required to trip the barrier.
2. **action**— An optional command that is run once per barrier point, after the last fiber in the party arrives, but before any fibers are released. This action is useful for updating the shared state before any of the parties continue.

```scala
object CyclicBarrier {
  def make(parties: Int)                  : UIO[CyclicBarrier] = ???
  def make(parties: Int, action: UIO[Any]): UIO[CyclicBarrier] = ???
}
```

If we create a barrier and don't call `await` on that, the barrier is not going to be released (broken) and the number of `waiting` fibers remains zero:

```scala mdoc:silent
import zio._
import zio.concurrent.CyclicBarrier

for {
  barrier  <- CyclicBarrier.make(100, ZIO.debug("This is a release action!"))
  isBroken <- barrier.isBroken  
  waiting  <- barrier.waiting
} yield assert(!isBroken && waiting == 0)
```

## Internals

Each `CyclicBarrier` has the following internal _private_ properties, knowing them helps us to have a deep understanding of how `CyclicBarrier` works:

```scala mdoc:compile-only
class CyclicBarrier private (
  private val _parties: Int,
  private val _waiting: Ref[Int],
  private val _lock: Ref[Promise[Unit, Unit]],
  private val _action: UIO[Any],
  private val _broken: Ref[Boolean]
)
```

Let's introduce each one:

1. `_parties`— The fibers that need to synchronize their execution are called _parties_. It is an immutable property and will be assigned when we create a `CyclicBarrier` using one of the `make` constructors of the `CyclicBarrier`.
2. `_waiting`— This is a mutable property that denotes the number of already fibers waiting for the release of the barrier:
  1. When we call `reset` on a `CyclicBarrier` this number will be reset to zero.
  2. When we call `await` on a `CyclicBarrier` inside a fiber, it will return a value of type `IO[Unit, Int]`:
     - If the barrier isn't broken, it will suspend the fiber (`waiting`) until all parties have invoked `await` on this barrier. When the number of '_waiting` fibers reaches the number of '_parties', all parties that are suspended due to the `await` method will resume and continue processing. Before resuming all waiting fibers, the `_waiting` number will be reset to zero.
     - If the barrier is broken, it will fail with the type of `Unit`.
  3. To access this property, we can use the `waiting` member of a `CyclicBarrier` which returns `UIO[Int]`.
3. `_lock`— This is a mutable property that contains a `Promise[Unit, Unit]`:
  - When a barrier is _released_, the value of this promise internally will be succeeded with a `Unit` value.
  - When a barrier is _broken_, the value of this promise internally will be failed with a `Unit` value.
  - There is no public API for changing the value of this property.
4. `_action`— When we create a `CyclicBarrier` we can provide an effectful _action_ which will be executed when the barrier is released before any of the parties continue.
5. `_broken`— This is a mutable property which denotes that whether the barrier is broken or not:
  - The default value of `_broken` is `false`.
  - When one of the `_waiting` fibers is interrupted, the barrier will be broken and the value of `_broken` will be changed to `true`.
  - We can access this value using `isBroken` method on a `CyclicBarrier`.

## Operations

| Method                   | Definition                                                                                  |
|--------------------------|---------------------------------------------------------------------------------------------|
| `parties: Int`           | The number of parties required to trip this barrier.                                        |
| `waiting: UIO[Int]`      | The number of parties currently waiting at the barrier.                                     |
| `await: IO[Unit, Int]`   | Waits until all parties have invoked await on this barrier. Fails if the barrier is broken. |
| `reset: UIO[Unit]`       | Resets the barrier to its initial state. Breaks any waiting party.                          |
| `isBroken: UIO[Boolean]` | Queries if this barrier is in a broken state.                                               |

## Simple Example

In the following example, we started three tasks, each one has a different working time, but they won't return until the other parties finished their jobs:

```scala mdoc:compile-only
import zio._
import zio.concurrent.CyclicBarrier

object MainApp extends ZIOAppDefault {
  def task(name: String) =
    for {
      b <- ZIO.service[CyclicBarrier]
      _ <- ZIO.debug(s"task-$name: started my job right now!")
      d <- Random.nextLongBetween(1000, 10000)
      _ <- ZIO.sleep(Duration.fromMillis(d))
      _ <- ZIO.debug(s"task-$name: finished my job and waiting for other parties to finish their jobs")
      _ <- b.await 
      _ <- ZIO.debug(s"task-$name: the barrier is now broken, so I'm going to exit immediately!")
    } yield ()

  def run =
    for {
      b    <- CyclicBarrier.make(3)
      tasks = task("1") <&> task("2") <&> task("3")
      _    <- tasks.provideService(b)
    } yield ()
}
```

## Cyclic Example

ّIf we change the previous example and add more than three tasks, the first three arriving tasks will be blocked and wait for synchronization. After the barrier is broken, the next three tasks will be blocked on the next barrier. **This process will be executed again and again for further tasks. This is why we say that the barrier is cyclic**:

```scala mdoc:compile-only
import zio._
import zio.concurrent.CyclicBarrier

object MainApp extends ZIOAppDefault {

  def task(name: String) =
    for {
      b <- ZIO.service[CyclicBarrier]
      _ <- ZIO.debug(s"task-$name: started my job right now!")
      d <- Random.nextLongBetween(1000, 10000)
      _ <- ZIO.sleep(Duration.fromMillis(d))
      _ <- ZIO.debug(s"task-$name: finished my job and waiting for other parties to finish their jobs")
      _ <- b.await
      _ <- ZIO.debug(s"task-$name: the barrier is now broken, so I'm going to exit immediately!")
    } yield ()

  def run =
    for {
      b <- CyclicBarrier.make(
             parties = 3,
             action = ZIO.debug(
               "The barrier is released right now!" +
                 "I can do some effectful actions on release of barrier."
             )
           )
      tasks = task("1") <&>
                task("2") <&>
                task("3") <&>
                task("4") <&>
                task("5")
      _ <- tasks.provideService(b)
    } yield ()
}
```

In this example after breakage of the barrier by proceeding with `task 1`, `task 2`, and `task 3`, the `CyclicBarrier` will be reset to the initial state, so other tasks can come in and `await` on the barrier. So here, `task 4` and `task 5`, proceed with their job and finally wait for all parties to come into the barrier point, but in this example, as we didn't provide `task 6`, the remaining tasks will block the execution of the whole program, infinitely; because the number of waiting fibers are not equal to `parties`.

If we add another concurrent task (e.g. `task("6")`) to our list of tasks, finally the next group of jobs that are waiting for each other will trip the barrier.

## Barrier Break on Resets

If we call `reset` method on a `CyclicBarrier` two following cases might happen:
- If number of `waiting` fibers is more than zero, then j

```scala mdoc:compile-only
import zio._
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

## All-or-None Breakage Model

The `CyclicBarrier` uses an all-or-none breakage model for failed synchronization attempts: If a fiber leaves a barrier point prematurely because of interruption, failure, or timeout, all other fibers waiting at that barrier point will also leave abnormally:

```scala mdoc:compile-only
import zio._
import zio.concurrent.CyclicBarrier
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
