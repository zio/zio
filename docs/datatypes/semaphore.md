---
id: datatypes_semaphore
title:  "Semaphore"
---

A `Semaphore` datatype which allows synchronization between fibers with `acquire` and `release` operations.
`Semaphore` is based on `Ref[A]` datatype.

## Operations

For example a synchronization of asynchronous tasks can 
be done via acquiring and releasing a semaphore with given number of permits it can spend.
When the `acquire` operation cannot be performed, due to insufficient `permits` value in the semaphore, such task 
is placed in internal suspended fibers queue and will be awaken when `permits` value is sufficient:

```scala mdoc:silent
import java.util.concurrent.TimeUnit
import zio._
import zio.console._
import zio.duration.Duration

val task = for {
  _ <- putStrLn("start")
  _ <- ZIO.sleep(Duration(2, TimeUnit.SECONDS))
  _ <- putStrLn("end")
} yield ()

val semTask = (sem: Semaphore) => for {
  _ <- sem.acquire
  _ <- task
  _ <- sem.release
} yield ()

val semTaskSeq = (sem: Semaphore) => (1 to 3).map(_ => semTask(sem))

val program = for {

  sem <- Semaphore.make(permits = 1)

  seq <- ZIO.effectTotal(semTaskSeq(sem))

  _ <- ZIO.collectAllPar(seq)

} yield ()
```

As the binary semaphore is a special case of counting semaphore 
we can acquire and release any value, regarding semaphore's permits:

```scala mdoc:silent
val semTaskN = (sem: Semaphore) => for {
  _ <- sem.acquireN(5)
  _ <- task
  _ <- sem.releaseN(5)
} yield ()
```

When acquiring and performing task is followed by equivalent release 
then entire action can be done with `withPermit` 
(or corresponding counting version `withPermits`):

```scala mdoc:silent
val permitTask = (sem: Semaphore) => for {
  _ <- sem.withPermit(task)
} yield ()
```
