---
id: semaphore
title: Semaphore
description: "A synchronization primitive that safely manages permit-based fiber coordination with automatic release guarantees."
keywords:
  - "Permit Management"
  - "Fiber Synchronization"
  - "Counting Semaphore"
  - "Concurrent Access Control"
  - "Binary Semaphore"
  - "Resource Blocking"
---

A `Semaphore` datatype which allows synchronization between fibers with the `withPermit` operation, which safely acquires and releases a permit.

## Operations

For example, a synchronization of asynchronous tasks can 
be done via acquiring and releasing a semaphore with a given number of permits it can spend.
When the acquire operation cannot be performed due to no more available `permits` in the semaphore, such task 
is semantically blocked, until the `permits` value is large enough again:

```scala mdoc:silent
import java.util.concurrent.TimeUnit
import zio._
import zio.Console._

val task = for {
  _ <- printLine("start")
  _ <- ZIO.sleep(Duration(2, TimeUnit.SECONDS))
  _ <- printLine("end")
} yield ()

val semTask = (sem: Semaphore) => for {
  _ <- sem.withPermit(task)
} yield ()

val semTaskSeq = (sem: Semaphore) => (1 to 3).map(_ => semTask(sem))

val program = for {

  sem <- Semaphore.make(permits = 1)

  seq <- ZIO.succeed(semTaskSeq(sem))

  _ <- ZIO.collectAllPar(seq)

} yield ()
```

As the binary semaphore is a special case of a counting semaphore, 
we can acquire and release any number of `permits`:

```scala mdoc:silent
val semTaskN = (sem: Semaphore) => for {
  _ <- sem.withPermits(5)(task)
} yield ()
```

The guarantee of `withPermit` (and its corresponding counting version `withPermits`) is that each acquisition will be followed by the equivalent number of releases, regardless of whether the task succeeds, fails, or is interrupted.

## Fairness

A semaphore created with `Semaphore.make` is fair: fibers that have to wait for permits are served in the order they asked, and once any fiber is waiting, every fiber that asks after it queues behind it, even if enough permits happen to be free at that instant. This means a fiber asking for many permits cannot be starved by fibers asking for few.

`Semaphore.makeUnfair` creates a semaphore where a fiber that finds free permits takes them, whether or not other fibers are queued for them. A fiber that releases its permits and immediately asks for them again therefore usually keeps them without suspending, which gives higher throughput when many fibers compete for few permits. A queued fiber may wait indefinitely. Use it when throughput matters more than how long any one fiber may wait, for example a mutex around a short critical section that is entered in a tight loop.

```scala mdoc:silent
val unfairProgram = for {
  sem <- Semaphore.makeUnfair(permits = 1)
  _   <- ZIO.collectAllParDiscard(semTaskSeq(sem))
} yield ()
```

## See Also

- [Fiber](../fiber/fiber.md) — lightweight concurrency mechanism that Semaphore synchronizes between
- [Migrate from Cats Effect to ZIO](../../guides/migrate/from-cats-effect.md) — shows how `cats.effect.std.Semaphore` maps to `zio.Semaphore`, and how a binary semaphore replaces `cats.effect.std.Mutex`.
