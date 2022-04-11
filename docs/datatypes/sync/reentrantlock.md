---
id: reentrantlock 
title: "ReentrantLock"
---

A `ReentrantLock` is a lock which can be acquired multiple times by the same fiber. When a fiber acquires (`lock`) a reentrant lock, it will become the owner of that lock. Other threads cannot obtain the lock unless the lock owner releases (`unlock`) the lock. As the lock is reentrant, the lock owner can call the `lock` again, multiple times.

## Creating ReentrantLocks

Using `ReentrantLocks.make` we can create a reentrant lock in the _unlocked state_:

```scala
object ReentrantLock {
  def make(fairness: Boolean = false): UIO[ReentrantLock] = ???
}
```

By default, it creates a reentrant lock with an unfair policy, so waiters will be picked randomly. If we set the `fairness` parameter to true, the reentrant lock will pick the longest waiting thread.

## Locking and Unlocking

When a fiber attempt to acquire the lock using `ReentrantLock#lock` one of the following cases will happen:

1. If the lock is not held by another fiber, it will acquire the lock. The call to the `ReentrantLock#lock` returns immediately, and the _hold count_ increased by one.

2. If the current fiber already holds the lock, then the _hold count_ is incremented by one, and the method returns immediately. Due to the reentrancy feature of the lock, its owner can acquire the lock multiple times.

3. If the lock is held by another fiber, then the current fiber will be put to sleep until the lock has been acquired, at which point the lock hold count will be reset to one.

## Convenience Operations

1. **`tryLock`**— Acquires the lock only if it is not held by another fiber at the time of invocation otherwise it will return immediately, so it is a non-blocking operation.

- When the state is _unlocked_ `tryLock` changes the state to _locked_ (with the current fiber as owner and a hold count of 1) and returns `True`.
- When the state is _locked_ `tryLock` leaves the state _unchanged_ and returns `False`.

```scala
trait ReentrantLock {
  lazy val tryLock: UIO[Boolean]
}
```

2. **`withLock`**— Acquires and releases the lock as a scoped effect. By using this method, the unlock method will be called automatically at the end of the scope.

```scala
trait ReentrantLock {
  lazy val withLock: URIO[Scope, Int]
}
```

## Querying ReentrantLocks

A reentrant lock has two states: _locked_ or _unlocked_. When the reentrant lock is in _locked_ state it has these properties:
- **Owner** indicates which fiber has acquired the lock. This can be queried by calling the `ReentrantLock#owner` method.
- **Hold Count** indicates how many times its owner acquired the lock. This can be queried using by calling the `ReentrantLock#holdCount` method.
- **Waiters** is a collection of fibers that are waiting to acquire this lock. We can query all of them using the `ReentrantLock#queuedFibers` method.

## Examples

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

Parent fiber (`zio-fiber-2`) acquires the lock and then releases it after 10 seconds. Meanwhile, the child fiber (`zio-fiber-7`) tries to acquire the lock, but it cannot. The attempt to acquire the lock in the child fiber causes the fiber to go into sleep mode. Following the release of the lock by the parent fiber, the child fiber will awaken and acquire the lock.

In the previous example, we used the simplest use-case of a locking mechanism that doesn't involve reentrancy. To illustrate how reentrancy works, let's look at another example:

```scala mdoc:compile-only
import zio._
import zio.concurrent._

object MainApp extends ZIOAppDefault {

  def task(l: ReentrantLock, i: Int): ZIO[Any, Nothing, Unit] = for {
    fn <- ZIO.fiberId.map(_.threadName)
    _  <- l.lock
    hc <- l.holdCount
    _  <- ZIO.debug(s"$fn (re)entered the critical section and now the hold count is $hc")
    _  <- ZIO.when(i > 0)(task(l, i - 1))
    _  <- l.unlock
    hc <- l.holdCount
    _  <- ZIO.debug(s"$fn exited the critical section and now the hold count is $hc")
  } yield ()

  def run =
    for {
      l <- ReentrantLock.make()
      _ <- task(l, 2) zipPar task(l, 3)
    } yield ()
}
// One possible output:
// zio-fiber-8 (re)entered the critical section and now the hold count is 1
// zio-fiber-8 (re)entered the critical section and now the hold count is 2
// zio-fiber-8 (re)entered the critical section and now the hold count is 3
// zio-fiber-8 (re)entered the critical section and now the hold count is 4
// zio-fiber-8 exited the critical section and now the hold count is 3
// zio-fiber-8 exited the critical section and now the hold count is 2
// zio-fiber-8 exited the critical section and now the hold count is 1
// zio-fiber-8 exited the critical section and now the hold count is 0
// zio-fiber-7 (re)entered the critical section and now the hold count is 1
// zio-fiber-7 (re)entered the critical section and now the hold count is 2
// zio-fiber-7 (re)entered the critical section and now the hold count is 3
// zio-fiber-7 exited the critical section and now the hold count is 2
// zio-fiber-7 exited the critical section and now the hold count is 1
// zio-fiber-7 exited the critical section and now the hold count is 0
```

In this example, inside the `task` function, we have a critical section. Also, the `task` itself is recursive and inside the critical section, it will call itself. When a fiber tries to enter the critical section and that fiber is the owner of that critical section, the `ReentrantLock` allows that fiber to reenter, and it will increment the `holdCount` by one.


