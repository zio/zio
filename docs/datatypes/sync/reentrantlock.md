---
id: reentrantlock 
title: "ReentrantLock"
---

A `ReentrantLock` is a lock which can be acquired multiple times by the same fiber. When a fiber acquires (`lock`) a reentrant lock, it will become the owner of that lock. Other fibers cannot obtain the lock unless the lock owner releases (`unlock`) the lock. As the lock is reentrant, the lock owner can call the `lock` again, multiple times.

## Reentrancy

In reentrancy, only the current working thread can access a shared resource, preventing any other thread from doing so. Reentrant locks allow their owner (the fiber that owns the lock) to re-enter them multiple times.

Therefore, in reentrancy locks are acquired per-fiber instead of per-invocation. In other words, if a fiber is not reentrant, and tries to acquire a lock that it already holds, the request won’t succeed.

## Creating ReentrantLocks

Using `ReentrantLocks.make` we can create a reentrant lock in the _unlocked state_:

```scala
object ReentrantLock {
  def make(fairness: Boolean = false): UIO[ReentrantLock] = ???
}
```

By default, it creates a reentrant lock with an unfair policy, so waiters will be picked randomly. If we set the `fairness` parameter to `true`, the reentrant lock will pick the longest waiting fiber.

## Locking and Unlocking

The two basic operations on reentrant locks are `lock` and `unlock`. They acquire and release the lock, respectively:

```scala
trait ReentrantLock {
  lazy val lock:   UIO[Unit]
  lazy val unlock: UIO[Unit]
}
```

1. **`ReentrantLock#lock`**— When a fiber attempt to acquire the lock one of the following cases will happen:

   - When the state is _unlocked_ and in another word if the lock is not held by another fiber, it will acquire the lock and returns immediately and the _hold count_ increased by one.

   - When the state is _locked_ and the current fiber already holds the lock, then the _hold count_ is incremented by one, and the method returns immediately. 

   - When the state is _locked_ and the lock is held by another fiber, then the current fiber will be put to sleep until the lock has been acquired, at which point the lock hold count will be reset to one.

2. **`ReentrantLock#unlock`**— When a fiber attempt to release the lock, one of the following cases will happen:
    - If the current fiber is the holder of this lock then the hold count is decremented. If the hold count is now zero then the lock is released. So if there are any fibers blocked on acquire, one fiber will be picked using (fairness or unfairness policy) and woken up.
    - If the current fiber is not the holder of this lock then nothing happens.

## Fairness Policy

The ReentrantLock constructor offers two fairness policies:
  - unfair policy (the default)
  - fair policy

When a fiber fails to acquire the lock, it is placed in the waiting queue. So when the owning fiber releases the lock, the next waiting fiber chosen by the fairness policy is allowed to try acquiring the lock:
  - In the case of a fairness policy, fibers always acquire a lock in the order in which they requested it. So the reentrant lock will pick the longest waiting fiber from the waiting queue.
  - In case of unfair policy, the reentrant lock will pick a random fiber from the waiting queue.

## Convenience Operations

1. **`ReentrantLock#tryLock`**— Acquires the lock only if it is not held by another fiber at the time of invocation otherwise it will return immediately, so it is a non-blocking operation.

- When the state is _unlocked_ `tryLock` changes the state to _locked_ (with the current fiber as owner and a hold count of 1) and returns `true`.
- When the state is _locked_ `tryLock` leaves the state _unchanged_ and returns `false`.

```scala
trait ReentrantLock {
  lazy val tryLock: UIO[Boolean]
}
```

2. **`ReentrantLock#withLock`**— Acquires and releases the lock as a scoped effect. By using this method, the unlock method will be called automatically at the end of the scope.

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

### Example of Simple Locking Mechanism

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

### Example of Reentrancy

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

### Example of Producing Deadlock

When two or more threads wait forever for a lock held by another thread, they have reached a deadlock. So when we are working with locks, we should be careful of avoiding deadlocks.

In this example, we are just trying to show a simple possible deadlock example:

```scala mdoc:compile-only
import zio._
import zio.concurrent._

object MainApp extends ZIOAppDefault {
  def workflow1(l1: ReentrantLock, l2: ReentrantLock) =
    for {
      f <- ZIO.fiberId.map(_.threadName)
      _ <- l1.lock *> ZIO.debug(s"$f locked the l1")
      o <- l2.owner.map(_.map(_.threadName))
      _ <- ZIO.debug(s"$f trying to lock the l2 while the $o is its owner") *>
        l2.lock *>
        ZIO.debug(s"$f locked the l2")
      _ <- l2.unlock
      _ <- l1.unlock
    } yield ()

  def workflow2(l1: ReentrantLock, l2: ReentrantLock) =
    for {
      f <- ZIO.fiberId.map(_.threadName)
      _ <- l2.lock *> ZIO.debug(s"$f locked the l2")
      o <- l1.owner.map(_.map(_.threadName))
      _ <- ZIO.debug(s"$f trying to lock the l1 while the $o is its owner") *>
        l1.lock *>
        ZIO.debug(s"$f locked the l1")
      _ <- l1.unlock
      _ <- l2.unlock
    } yield ()

  def run =
    for {
      l1 <- ReentrantLock.make()
      l2 <- ReentrantLock.make()
      _ <- workflow1(l1, l2) <&> workflow2(l1, l2)
    } yield ()
}
```

In we run this program, we have a possible deadlock situation, and it might print the following messages and lock forever:

```
zio-fiber-7 locked the l1
zio-fiber-8 locked the l2
zio-fiber-7 trying to lock the l2 while the Some(zio-fiber-8) is its owner
zio-fiber-8 trying to lock the l1 while the Some(zio-fiber-7) is its owner
```

When we run two workflows concurrently, it can cause a deadlock when the first workflow obtains `l1` and in the meantime, the second workflow obtains `l2`, now:
  - When the first workflow tries to obtain `l2` while `l2` is being obtained by `l1`.
  - When the second workflow tries to obtain the `l1` while the `l2` is being obtained by `l1`.
Eventually, both fibers will enter a waiting state, and there will be a deadlock.
