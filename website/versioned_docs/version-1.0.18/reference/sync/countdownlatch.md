---
id: countdownlatch
title: "CountdownLatch"
---
A synchronization aid that allows one or more fibers to wait until a set of operations being performed in other fibers
completes.

A `CountDownLatch` is initialized with a given count. The `await` method block until the current count reaches zero due
to invocations of the `countDown` method, after which all waiting fibers are released and any subsequent invocations
of `await` return immediately. This is a one-shot phenomenon -- the count cannot be reset. If you need a version that
resets the count, consider using a [[CyclicBarrier]].

A `CountDownLatch` is a versatile synchronization tool and can be used for a number of purposes. A `CountDownLatch`
initialized with a count of one serves as a simple on/off latch, or gate: all fibers invoking `await` wait at the gate
until it is opened by a fiber invoking `countDown`. A `CountDownLatch`initialized to N can be used to make one fiber
wait until N fibers have completed some action, or some action has been completed N times.

A useful property of a `CountDownLatch` is that it doesn't require that fibers calling `countDown` wait for the count to
reach zero before proceeding, it simply prevents any fiber from proceeding past an `await`until all fibers could pass.

## Operations

### Creation

| Method                                                      | Definition                    |
|-------------------------------------------------------------|-------------------------------|
| `make(n: Int): IO[Option[Nothing], CountdownLatch]`         | Makes a new `CountdownLatch`. |

### Use

| Method                 | Definition                                                                                 |
|------------------------|--------------------------------------------------------------------------------------------|
| `await: UIO[Unit]`     | Causes the current fiber to wait until the latch has counted down to zero.                 |
| `countDown: UIO[Unit]` | Decrements the count of the latch, releasing all waiting fibers if the count reaches zero. |
| `count: UIO[Int]`      | Returns the current count.                                                                 |

## Example Usage

```scala
import zio._
import zio.concurrent.CountdownLatch

for {
  latch  <- CountdownLatch.make(100)
  count  <- Ref.make(0)
  ps     <- ZIO.collectAll(List.fill(10)(Promise.make[Nothing, Unit]))
  _      <- ZIO.forkAll(ps.map(p => latch.await *> count.update(_ + 1) *> p.succeed(())))
  _      <- latch.countDown.repeat(Schedule.recurs(99))
  _      <- ZIO.foreach_(ps)(_.await)
  result <- count.get
} yield assert(result == 10)
```