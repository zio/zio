---
id: mvar 
title: "MVar"
---

An `MVar[A]` is a mutable location that is either empty or contains a value of type `A`. So the `MVar` acts like a _single-element buffer_.

It has two fundamental operations:
- `put` which fills an `MVar` if it is empty and blocks otherwise.
- `take` which empties an `MVar` if it is full and blocks otherwise.

So we can put something into it, making it full, or take something out, making it empty, and in two cases, it will block the calling fiber:
- If it is full and the calling fiber tries to put something in it.
- If it is empty and the calling fiber tries to take something out of it.

These two features of `MVar` make it possible to synchronize multiple fibers.

`MVar` can be used in multiple different ways:
- As synchronized mutable variables
- As channels, with `take` and `put` as `receive` and `send`
- As a binary semaphore `MVar[Unit]`, with `take` and `put` as `wait` and `signal`

They were introduced in the paper [Concurrent Haskell](#http://research.microsoft.com/~simonpj/papers/concurrent-haskell.ps.gz) by Simon Peyton Jones, Andrew Gordon and Sigbjorn Finne.

## Simple On/Off Latch

We can use an `MVar` to implement a simple on/off latch:

```scala mdoc:compile-only
import zio._
import zio.concurrent.MVar

object MainApp extends ZIOAppDefault {

  def job1(latch: MVar[Unit]) =
    for {
      _ <- ZIO.debug("Job 1: I started my work")
      _ <- ZIO.sleep(5.second)
      _ <- ZIO.debug("Job 1: I finished my work")
      _ <- latch.put(())
    } yield ()

  def job2(latch: MVar[Unit]) = for {
    _ <- ZIO.debug("Job 2: I'm waiting for job 1 to finish its work")
    _ <- latch.take
    _ <- ZIO.debug("Job 2: I'm starting my work")
    _ <- ZIO.sleep(4.second)
    _ <- ZIO.debug("Job 2: I finished my work")
  } yield ()

  def run =
    MVar.empty[Unit].flatMap { latch =>
      job1(latch) <&> job2(latch)
    }
}
```

In the above example, we created an empty `MVar`, and then we created two `ZIO` workflows that will be executed concurrently. The first one will wait for the second one to finish its work. But the second one at some point in its execution will need to synchronize with the first one. It needs to make sure that the first one has finished its work before it continues its own work.

## Binary Semaphore

Assume we have a function `inc` that takes a `Ref[Int]` and increments its value by one as below:

```scala mdoc:compile-only
import zio._
import zio.concurrent.MVar

object MainApp extends ZIOAppDefault {

  def inc(ref: Ref[Int]) =
    for {
      v <- ref.get
      result = v + 1
      _ <- ref.set(result)
    } yield ()
    
  def run =
    for {
      ref <- Ref.make(0)
      _ <- ZIO.foreachParDiscard(1 to 100)(_ => inc(ref))
      _ <- ref.get.debug("result")
    } yield ()

}
```

When we perform the `inc` function, 100 times, we expect the final value of the `ref` to be 100. But if we run the program multiple times, we will get different results. This is because the `inc` function is not atomic, and the `ref` may be updated by another thread between the time we read it and the time we write it.

So we need a way to ensure that between the time we read the ref and the time we write to it, no other threads will be able to make changes to it.

We know that `Ref` has the `update` operation that is atomic. So if we rewrite the `inc` as below, our program will work as expected:

```scala mdoc:invisible
import zio._
```

```scala mdoc:compile-only
def inc(ref: Ref[Int]) =
  ref.update(_ + 1)
```

```scala mdoc:invisible:reset

```

Although the solution to this problem is `Ref#update`, we want to use `MVar` to implement the same functionality for pedagogical purposes. So let's see how we can do that using `MVar`:

```scala mdoc:compile-only
import zio._
import zio.concurrent.MVar

object MainApp extends ZIOAppDefault {

  def inc(ref: Ref[Int]) =
    for {
      v <- ref.get
      result = v + 1
      _ <- ref.set(result)
    } yield ()
    
  def run =
    for {
      semaphore <- MVar.make[Unit](())
      ref <- Ref.make(0)
      _ <- ZIO.foreachParDiscard(1 to 100) { _ =>
          for {
            _ <- semaphore.take     // acquire
            _ <- inc(ref)
            _ <- semaphore.put(())  // release
          } yield ()
      }
      _ <- ref.get.debug("result")
    } yield ()

}
```

So we used the `take` as `acquire` and the `put` as the `release` operation of the binary semaphore.

Note that, in the above solution, if any interruption occurs while we have acquired the semaphore (between `acquire` and `release` operations), the semaphore will not be released. So to prevent such a situation, we need to make sure that we always release the semaphore whether the critical section runs successfully or not. Let's model the whole solution in a new data type called `BinarySemaphore`:

```scala mdoc:silent
import zio._
import zio.concurrent.MVar

class BinarySemaphore private (mvar: MVar[Unit]) {
  def acquire: ZIO[Any, Nothing, Unit] = mvar.take

  def release: ZIO[Any, Nothing, Unit] = mvar.put(())

  def guard[R, E, A](
      region: ZIO[R, E, A]
  ): ZIO[R, E, A] =
    ZIO.acquireReleaseWith(acquire)(_ => release)(_ => region)
}

object BinarySemaphore {
  def make(): ZIO[Any, Nothing, BinarySemaphore] =
    MVar.make(()).map(new BinarySemaphore(_))
}
```

Now we can apply the `guard` function to the `inc` function of the previous example:

```scala mdoc:compile-only
import zio._
import zio.concurrent.MVar

object MainApp extends ZIOAppDefault {

  def inc(ref: Ref[Int]) =
    for {
      v <- ref.get
      result = v + 1
      _ <- ref.set(result)
    } yield ()

  def run =
    for {
      semaphore <- BinarySemaphore.make()
      ref <- Ref.make(0)
      _ <- ZIO.foreachParDiscard(1 to 100) { _ =>
        semaphore.guard(inc(ref))
      }
      _ <- ref.get.debug("result")
    } yield ()

}
```

```scala mdoc:invisible:reset

```

## Synchronized Mutable Variable

We can have synchronized mutable variables using the `MVar` data type:

```scala mdoc:compile-only
import zio._
import zio.concurrent.MVar

object MainApp extends ZIOAppDefault {
  def inc(state: MVar[Int]) =
    state.update(_ + 1)

  def run =
    MVar
      .make(0)
      .flatMap(s => ZIO.foreachParDiscard(1 to 100)(_ => inc(s)) *> s.take)
      .debug("result")
}
```

In this case, we executed the same `inc` workflow 100 times concurrently. All the concurrent fibers access the same shared mutable variable called `state` in a synchronized way. In this case, we used the `update`, a safe operation that will atomically update the value of `MVar`.

A question that may be raised is that can we compose `take` and `update` to implement the same functionality for the `inc` workflow as below?

```scala mdoc:invisible
import zio._
import zio.concurrent.MVar
```

```scala mdoc:compile-only
def inc(state: MVar[Int]) =
  state.take.flatMap(s => state.put(s + 1))
```

```scala mdoc:invisible:reset

```

Can we say this is the same as the previous `inc` function? No, because although the `take` and `put` are atomic by themselves, their composition is not. So in a real-world scenario, in a concurrent environment it is possible that in between the `take` and `put` operations, the `state` is modified by another fiber. So this is why we used the `update` operation instead, which is an atomic operation.

## Producer/Consumer Channel

We can use an `MVar` to implement a producer/consumer channel:

```scala mdoc:compile-only
import zio._
import zio.concurrent.MVar

object MainApp extends ZIOAppDefault {
  def producer(state: MVar[Int]) =
    Random.nextIntBounded(100)
      .flatMap(state.put)
      .forever
 
  def consumer(state: MVar[Int]) =
    state.take
      .flatMap(i => ZIO.debug(s"$i consumed!"))
      .delay(1.second)
      .forever

  def run =
    MVar.empty[Int].flatMap { s =>
      producer(s) <&> consumer(s)
    }
}
```

In such a case we want to model a producer/consumer channel to make sure the producer doesn't produce any value unless the consumer is ready to consume it. So in this example, `MVar` acts as one element size channel that handles backpressure. 

If we add more consumers, the speed of consuming elements will be increased. Note that, by having multiple consumers, the data will not be duplicated through the consumers. If we have three consumers, each piece of data will be consumed only by one of the consumers:

```scala mdoc:compile-only
import zio._
import zio.concurrent.MVar

object MainApp extends ZIOAppDefault {
  def producer(state: MVar[Int]) =
    ZIO.foreachDiscard(1 to Int.MaxValue)(state.put)

  def consumer(state: MVar[Int])(name: String) =
    state.take
      .flatMap(i => ZIO.debug(s"Consumer $name: $i consumed!"))
      .delay(1.second)
      .forever

  def run =
    MVar.empty[Int].flatMap { s =>
      producer(s) <&>
        consumer(s)("A") <&> consumer(s)("B") <&> consumer(s)("C")
    }
}
```