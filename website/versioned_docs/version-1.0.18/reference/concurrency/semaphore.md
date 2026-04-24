---
id: semaphore
title: "Semaphore"
---

A `Semaphore` datatype which allows synchronization between fibers with the `withPermit` operation, which safely acquires and releases a permit.
`Semaphore` is based on `Ref[A]` datatype.

## Operations

For example a synchronization of asynchronous tasks can 
be done via acquiring and releasing a semaphore with given number of permits it can spend.
When the acquire operation cannot be performed, due to insufficient `permits` value in the semaphore, such task 
is placed in internal suspended fibers queue and will be awaken when `permits` value is sufficient:

```scala
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
  _ <- sem.withPermit(task)
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

```scala
val semTaskN = (sem: Semaphore) => for {
  _ <- sem.withPermits(5)(task)
} yield ()
```

The guarantee of `withPermit` (and its corresponding counting version `withPermits`) is that acquisition will be followed by equivalent release, regardless of whether the task succeeds, fails, or is interrupted.
