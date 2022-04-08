---
id: reentrantlock 
title: "ReentrantLock"
---

A `ReentrantLock` is a synchronization data type that may be locked multiple times by the same fiber. When a fiber locks a reentrant lock, it will become the owner of that lock. Other threads cannot obtain the lock unless the lock owner unlocks the lock. As the lock is reentrant, the lock owner can call the lock again, multiple times.

When a fiber attempt to acquire the lock using `ReentrantLock#lock` one of the following cases will happen:

1. If the lock is not held by another fiber, it will acquire the lock. The call to the `ReentrantLock#lock` returns immediately, and the _hold count_ increased by one.

2. If the current fiber already holds the lock, then the _hold count_ is incremented by one, and the method returns immediately. Due to the reentrancy feature of the lock, its owner can acquire the lock multiple times.

3. If the lock is held by another fiber, then the current fiber will be put to sleep until the lock has been acquired, at which point the lock hold count will be reset to one.

In the following example, the main fiber acquires the lock, and then we try to acquire the lock from its child fiber. We will see that the child fiber will be blocked when it attempts to acquire the lock until the parent fiber releases it:

```scala mdoc:compile-only
import zio._
import zio.concurrent._

object MainApp extends ZIOAppDefault {

  def run =
    for {
      l  <- ReentrantLock.make()
      fn <- ZIO.fiberId.map(_.threadName)
      _  <- l.lock
      _  <- ZIO.debug(s"$fn acquired the lock.")
      task =
        for {
          fn <- ZIO.fiberId.map(_.threadName)
          _  <- ZIO.debug(s"$fn attempted to acquire the lock.")
          _  <- l.lock
          _  <- ZIO.debug(s"$fn acquired the lock.")
          _  <- ZIO.debug(s"$fn will release the lock after 5 second.")
          _  <- ZIO.sleep(5.second)
          _  <- l.unlock
          _  <- ZIO.debug(s"$fn released the lock.")
        } yield ()
      f <- task.fork
      _ <- ZIO.debug(s"$fn will release the lock after 10 second.")
      _ <- ZIO.sleep(10.second)
      _ <- (l.unlock *> ZIO.debug(s"$fn released the lock.")).uninterruptible
      _ <- f.join
    } yield ()
    
}
// Output:
// zio-fiber-2 acquired the lock.
// zio-fiber-2 will release the lock after 10 second.
// zio-fiber-7 attempted to acquire the lock.
// zio-fiber-2 released the lock.
// zio-fiber-7 acquired the lock.
// zio-fiber-7 will release the lock after 5 second.
// zio-fiber-7 released the lock.
```
