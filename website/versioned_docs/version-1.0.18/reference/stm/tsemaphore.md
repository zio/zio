---
id: tsemaphore
title: "TSemaphore"
---

`TSemaphore` is a semaphore with transactional semantics that can be used to control access to a common resource. It 
holds a certain number of permits, and permits may be acquired or released.

## Create a TSemaphore
Creating a `TSemaphore` with 10 permits:
```scala
import zio._
import zio.stm._

val tSemaphoreCreate: STM[Nothing, TSemaphore] = TSemaphore.make(10L)
```

## Acquire a permit
Acquiring a permit reduces the number of remaining permits that the `TSemaphore` contains. Acquiring a permit is done 
when a user wants to access a common shared resource:
```scala
import zio._
import zio.stm._

val tSemaphoreAcq: STM[Nothing, TSemaphore] = for {
  tSem <- TSemaphore.make(2L)
  _    <- tSem.acquire
} yield tSem

tSemaphoreAcq.commit
```
Note that if you try to acquire a permit when there are no more remaining permits in the semaphore then execution will be blocked semantically until a permit is ready to be acquired. Note that semantic blocking does not block threads and the STM transaction will only be retried when a permit is released.

## Release a permit
Once you have finished accessing the shared resource, you must release your permit so other parties can access the 
shared resource:
```scala
import zio._
import zio.stm._

val tSemaphoreRelease: STM[Nothing, TSemaphore] = for {
  tSem <- TSemaphore.make(1L)
  _    <- tSem.acquire
  _    <- tSem.release
} yield tSem

tSemaphoreRelease.commit
```

## Retrieve available permits
You can query for the remaining amount of permits in the TSemaphore by using `available`:
```scala
import zio._
import zio.stm._

val tSemaphoreAvailable: STM[Nothing, Long] = for {
  tSem <- TSemaphore.make(2L)
  _    <- tSem.acquire
  cap  <- tSem.available
} yield cap

tSemaphoreAvailable.commit
```
The above code creates a TSemaphore with two permits and acquires one permit without releasing it. Here, `available`
will report that there is a single permit left.

## Execute an arbitrary STM action with automatic acquire and release
You can choose to execute any arbitrary STM action that requires acquiring and releasing permit on TSemaphore as part
of the same transaction. Rather than doing:
```scala
import zio._
import zio.stm._

def yourSTMAction: STM[Nothing, Unit] = STM.unit

val tSemaphoreWithoutPermit: STM[Nothing, Unit] = 
  for {
    sem <- TSemaphore.make(1L)
    _   <- sem.acquire
    a   <- yourSTMAction
    _   <- sem.release
  } yield a

tSemaphoreWithoutPermit.commit
```
You can simply use `withPermit` instead:
```scala
import zio._
import zio.stm._

val tSemaphoreWithPermit: STM[Nothing, Unit] = for {
  sem <- TSemaphore.make(1L)
  a   <- sem.withPermit(yourSTMAction)
} yield a

tSemaphoreWithPermit.commit
```

It is considered best practice to use `withPermit` over using an `acquire` and a `release` directly unless dealing with more complicated use cases that involve multiple STM actions where `acquire` is not at the start and `release` is not at the end of the STM transaction.

## Acquire and release multiple permits
It is possible to acquire and release multiple permits at a time using `acquireN` and `releaseN`:
```scala
import zio._
import zio.stm._

val tSemaphoreAcquireNReleaseN: STM[Nothing, Boolean] = for {
  sem <- TSemaphore.make(3L)
  _   <- sem.acquireN(3L)
  cap <- sem.available 
  _   <- sem.releaseN(3L)
} yield cap == 0

tSemaphoreAcquireNReleaseN.commit
```
