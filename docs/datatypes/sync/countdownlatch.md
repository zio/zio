---
id: countdownlatch
title: "CountdownLatch"
---
A synchronization aid that allows one or more fibers to wait until a set of operations being performed in other fibers completes.

A `CountDownLatch` is initialized with a given count. The `await` method block until the current count reaches zero due to invocations of the `countDown` method, after which all waiting fibers are released and any subsequent invocations of `await` return immediately. This is a one-shot phenomenon -- the count cannot be reset. If you need a version that
resets the count, consider using a [`CyclicBarrier`](cyclicbarrier.md).

A `CountDownLatch` is a versatile synchronization tool and can be used for a number of purposes. A `CountDownLatch` initialized with a count of one serves as a simple on/off latch, or gate: all fibers invoking `await` wait at the gate until it is opened by a fiber invoking `countDown`. A `CountDownLatch`initialized to N can be used to make one fiber wait until N fibers have completed some action, or some action has been completed N times.

A useful property of a `CountDownLatch` is that it doesn't require that fibers calling `countDown` wait for the count to reach zero before proceeding, it simply prevents any fiber from proceeding past an `await`until all fibers could pass.

## Creation

To create a `CountDownLatch` we can simply use the `make` constructor. It takes an initial number, for the countdown counter:

```scala
object CountdownLatch {
  def make(n: Int): IO[Option[Nothing], CountdownLatch]
}
```

## Operations

There are two important operations defined on `CountdownLatch`:

```scala
class CountdownLatch {
  val countDown: UIO[Unit]
  val await: UIO[Unit]
}
```

The **`countDown`** operation decrements the count of the latch, releasing all waiting fibers if the count reaches zero, and the **`await`** operation causes the current fiber to wait until the latch has counted down to zero.

## Examples

### Simple on/off Latch

We can simply create an on/off latch using `Promise`. In the following example, we don't want to start the `consume` process before the first `50` number appears in the queue. As it requires a simple on/of latch we can implement that using the `Promise` data type:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  def consume(queue: Queue[Int]): UIO[Nothing] =
    queue.take
      .flatMap(i => ZIO.debug(s"consumed: $i"))
      .forever

  def produce(queue: Queue[Int], latch: Promise[Nothing, Unit]): UIO[Nothing] =
    (Random
      .nextIntBounded(100)
      .tap(i => queue.offer(i))
      .tap(i => ZIO.when(i == 50)(latch.succeed(()))) *> ZIO.sleep(500.millis)).forever

  def run =
    for {
      latch <- Promise.make[Nothing, Unit]
      queue <- Queue.unbounded[Int]
      _     <- produce(queue, latch) <&> (latch.await *> consume(queue))
    } yield ()
}
```

Alternatively, we can have an on/off latch using `CountDownLatch` with an initial count of _one_:

```scala mdoc:compile-only
import zio._
import zio.concurrent._

object MainApp extends ZIOAppDefault {

  def consume(queue: Queue[Int]): UIO[Nothing] =
    queue.take
      .flatMap(i => ZIO.debug(s"consumed: $i"))
      .forever

  def produce(queue: Queue[Int], latch: CountdownLatch): UIO[Nothing] =
    (Random
      .nextIntBounded(100)
      .tap(i => queue.offer(i))
      .tap(i => ZIO.when(i == 50)(latch.countDown)) *> ZIO.sleep(500.millis)).forever

  def run =
    for {
      latch <- CountdownLatch.make(1)
      queue <- Queue.unbounded[Int]
      _     <- produce(queue, latch) <&> (latch.await *> consume(queue))
    } yield ()
}
```

### Advanced Latches

We can solve more advanced problems by increasing the initial count of `CountdownLatch`.

Assume we had several producers concurrently in the previous example and the consumer was required to wait until at least five 50 numbers were added to the queue before they were allowed to consume. We can do this as follows:

```scala mdoc:compile-only
import zio._
import zio.concurrent._

object MainApp extends ZIOAppDefault {

  def consume(queue: Queue[Int]): UIO[Nothing] =
    queue.take
      .flatMap(i => ZIO.debug(s"consumed: $i"))
      .forever

  def produce(queue: Queue[Int], latch: CountdownLatch): UIO[Nothing] =
    (Random
      .nextIntBounded(100)
      .tap(i => queue.offer(i))
      .tap(i => ZIO.when(i == 50)(latch.countDown)) *> ZIO.sleep(500.millis)).forever

  def run =
    for {
      latch <- CountdownLatch.make(5)
      queue <- Queue.unbounded[Int]
      p = ZIO.collectAllParDiscard(ZIO.replicate(10)(produce(queue, latch)))
      c = latch.await *> consume(queue)
      _     <-  p <&> c
    } yield ()
}
```

In this example, 10 producers are producing numbers concurrently, and the consumer is waiting for its condition to be fulfilled to start the consumption process.
