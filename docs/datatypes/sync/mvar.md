---
id: mvar 
title: "MVar"
---

An `MVar[A]` is a mutable location that is either empty or contains a value of type `A`. It has two fundamental operations: 
- `put` which fills an `MVar` if it is empty and blocks otherwise.
- `take` which empties an `MVar` if it is full and blocks otherwise. 

They can be used in multiple different ways:
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

In the above example, we created an empty `MVar`, and then we created two `ZIO` workflow that will be executed concurrently. The first one will wait for the second one to finish its work. But the second one in some point of its execution, will need to synchronize with the first one. It need to make sure that the first one has finished its work before it continues its own work.
