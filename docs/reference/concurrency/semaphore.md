---
id: semaphore
title: "Semaphore"
---

A `Semaphore` datatype which allows synchronization between fibers with the `withPermit` operation, which safely acquires and releases a permit.
`Semaphore` is based on `Ref[A]` datatype.

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
