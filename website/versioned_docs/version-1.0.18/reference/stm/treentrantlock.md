---
id: treentrantlock
title: "TReentrantLock"
---

A TReentrantLock allows safe concurrent access to some mutable state efficiently, allowing multiple fibers to read the 
state (because that is safe to do) but only one fiber to modify the state (to prevent data corruption). Also, even though 
the TReentrantLock is implemented using STM; reads and writes can be committed, allowing this to be used as a building 
block for solutions that expose purely ZIO effects and internally allow locking on more than one piece of state in a 
simple and composable way (thanks to STM).

A `TReentrantLock` is a _reentrant_ read/write lock. A reentrant lock is one where a fiber can claim the lock multiple 
times without blocking on itself. It's useful in situations where it's not easy to keep track of whether you have already 
grabbed a lock. If a lock is non re-entrant you could grab the lock, then block when you go to grab it again, effectively 
causing a deadlock. 

## Semantics 

This lock allows both readers and writers to reacquire read or write locks with reentrancy guarantees. Readers are not 
allowed until all write locks held by the writing fiber have been released. Writers are not allowed unless there are no 
other locks or the fiber wanting to hold a write lock already has a read lock and there are no other fibers holding a 
read lock. 

This lock also allows upgrading from a read lock to a write lock (automatically) and downgrading 
from a write lock to a read lock (automatically provided that you upgraded from a read lock to a write lock).

## Creating a reentrant lock

```scala
import zio.stm._

val reentrantLock = TReentrantLock.make
```

## Acquiring a read lock

```scala
import zio.stm._

val program =
  (for {
    lock <- TReentrantLock.make
    _    <- lock.acquireRead
    rst  <- lock.readLocked  // lock is read-locked once transaction completes
    wst  <- lock.writeLocked // lock is not write-locked
  } yield rst && !wst).commit
```

## Acquiring a write lock

```scala
import zio._
import zio.stm._

val writeLockProgram: UIO[Boolean] =
  (for {
    lock <- TReentrantLock.make
    _    <- lock.acquireWrite
    wst  <- lock.writeLocked // lock is write-locked once transaction completes
    rst  <- lock.readLocked  // lock is not read-locked
  } yield !rst && wst).commit
```

## Multiple fibers can hold read locks

```scala
import zio._
import zio.stm._

val multipleReadLocksProgram: UIO[(Int, Int)] = for {
  lock          <- TReentrantLock.make.commit
  fiber0        <- lock.acquireRead.commit.fork // fiber0 acquires a read-lock
  currentState1 <- fiber0.join                  // 1 read lock held
  fiber1        <- lock.acquireRead.commit.fork // fiber1 acquires a read-lock
  currentState2 <- fiber1.join                  // 2 read locks held 
} yield (currentState1, currentState2)
```

## Upgrading and downgrading locks

If your fiber already has a read lock then it is possible to upgrade the lock to a write lock provided that no other
reader (other than your fiber) holds a lock
```scala
import zio._
import zio.stm._

val upgradeDowngradeProgram: UIO[(Boolean, Boolean, Boolean, Boolean)] = for {
  lock               <- TReentrantLock.make.commit
  _                  <- lock.acquireRead.commit
  _                  <- lock.acquireWrite.commit  // upgrade
  isWriteLocked      <- lock.writeLocked.commit   // now write-locked
  isReadLocked       <- lock.readLocked.commit    // and read-locked
  _                  <- lock.releaseWrite.commit  // downgrade
  isWriteLockedAfter <- lock.writeLocked.commit   // no longer write-locked
  isReadLockedAfter  <- lock.readLocked.commit    // still read-locked
} yield (isWriteLocked, isReadLocked, isWriteLockedAfter, isReadLockedAfter)
```

## Acquiring a write lock in a contentious scenario

A write lock can be acquired immediately only if one of the following conditions are satisfied:
1. There are no other holders of the lock
2. The current fiber is already holding a read lock and there are no other parties holding a read lock

If either of the above scenarios are untrue then attempting to acquire a write lock will semantically block the fiber.
Here is an example which demonstrates that a write lock can only be obtained by the fiber once all other readers (except 
the fiber attempting to acquire the write lock) have released their hold on the (read or write) lock.

```scala
import zio._
import zio.clock._
import zio.console._
import zio.stm._
import zio.duration._

val writeLockDemoProgram: URIO[Console with Clock, Unit] = for {
  l  <- TReentrantLock.make.commit
  _  <- putStrLn("Beginning test").orDie
  f1 <- (l.acquireRead.commit *> ZIO.sleep(5.seconds) *> l.releaseRead.commit).fork
  f2 <- (l.acquireRead.commit *> putStrLn("read-lock").orDie *> l.acquireWrite.commit *> putStrLn("I have upgraded!").orDie).fork
  _  <- (f1 zip f2).join
} yield ()
```

Here fiber `f1` acquires a read lock and sleeps for 5 seconds before releasing it. Fiber `f2` also acquires a read
lock and immediately tries to acquire a write lock. However, `f2` will have to semantically block for approximately 5 
seconds to obtain a write lock because `f1` will release its hold on the lock and only then can `f2` acquire a hold for
the write lock. 

## Safer methods  (`readLock` and `writeLock`)

Using `acquireRead`, `acquireWrite`, `releaseRead` and `releaseWrite` should be avoided for simple use cases relying on
methods like `readLock` and `writeLock` instead. `readLock` and `writeLock` automatically acquire and release the lock
thanks to the `Managed` construct. The program described below is a safer version of the program above and ensures we 
don't hold onto any resources once we are done using the reentrant lock.

```scala
import zio._
import zio.clock._
import zio.console._
import zio.stm._
import zio.duration._

val saferProgram: URIO[Console with Clock, Unit] = for {
  lock <- TReentrantLock.make.commit
  f1   <- lock.readLock.use_(ZIO.sleep(5.seconds) *> putStrLn("Powering down").orDie).fork
  f2   <- lock.readLock.use_(lock.writeLock.use_(putStrLn("Huzzah, writes are mine").orDie)).fork
  _    <- (f1 zip f2).join
} yield ()
```
