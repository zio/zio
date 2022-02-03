---
id: cyclicbarrier title: "CyclicBarrier"
---

A synchronization aid that allows a set of fibers to all wait for each other to reach a common barrier point.

CyclicBarriers are useful in programs involving a fixed sized party of fibers that must occasionally wait for each other. The barrier is called cyclic because it can be re-used after the waiting fibers are released.

A CyclicBarrier supports an optional action command that is run once per barrier point, after the last fiber in the party arrives, but before any fibers are released. This barrier action is useful for updating shared-state before any of the parties continue.

## Operations

### Creation

| Method                                                      | Definition                                       | 
|-------------------------------------------------------------|--------------------------------------------------| 
| `make(parties: Int): UIO[CyclicBarrier]`                    | Makes an `CyclicBarrier` with n parties          | 
| `make(parties: Int, action: UIO[Any]): UIO[CyclicBarrier]`  | Makes an `CyclicBarrier` with parties and action | 

### Use

| Method                  | Definition                                                                                 | 
|-------------------------|--------------------------------------------------------------------------------------------| 
| `parties: Int`          | The number of parties required to trip this barrier.                                       |
| `waiting: UIO[Int]`     | The number of parties currently waiting at the barrier.                                    |
| `await: IO[Unit, Int]`  | Waits until all parties have invoked await on this barrier. Fails if the barrier is broken.|
| `reset: UIO[Unit]`      | Resets the barrier to its initial state. Breaks any waiting party.                         |
| `isBroken: UIO[Boolean]`| Queries if this barrier is in a broken state.                                              |

## Example Usage

Construction:
```scala mdoc:silent
val barrier  = CyclicBarrier.make(parties)
val isBroken = barrier.isBroken   // false
val waiting  = barrier.waiting  // 0
```

Releasing the barrier:
```scala mdoc:silent
val barrier = CyclicBarrier.make(2)
val f1      = barrier.await.fork
f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
val f2      = barrier.await.fork
val ticket1 = f1.join  // 1
val ticket2 = f2.join  // 0
```

Releasing the barrier and performing the action:
```scala mdoc:silent
val promise    = Promise.make[Nothing, Unit]
val barrier    = CyclicBarrier.make(2, promise.succeed(()))
val f1         = barrier.await.fork
f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
val f2         = barrier.await.fork
f1.join
f2.join
isComplete = promise.isDone  // true
```

Releases the barrier and cycles:
```scala mdoc:silent
val barrier = CyclicBarrier.make(2)
val f1      = barrier.await.fork
f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
val f2      = barrier.await.fork
val ticket1 = f1.join
val ticket2 = f2.join
val f3      = barrier.await.fork
f3.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
val f4      = barrier.await.fork
val ticket3 = f3.join  // 1  
val ticket4 = f4.join  // 0
// here ticket1 is 1
// here ticket2 is 0
```

Breaks on reset:
```scala mdoc:silent
val barrier = CyclicBarrier.make(parties)
val f1      = barrier.await.fork
val f2      = barrier.await.fork
f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
f2.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
barrier.reset
val res1    = f1.await
val res2    = f2.await
```

Breaks on party interruption:
```scala mdoc:silent
val barrier   = CyclicBarrier.make(parties)
val f1        = barrier.await.timeout(1.second).fork
val f2        = barrier.await.fork
f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
f2.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
val isBroken1 = barrier.isBroken
TestClock.adjust(1.second)
val isBroken2 = barrier.isBroken
val res1      = f1.await
val res2      = f2.await
// here isBroken1 is false
// here isBroken2 is true
```
