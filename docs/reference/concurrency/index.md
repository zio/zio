---
id: index
title: "Introduction"
---

## Overview

Most of the time in concurrent programming we have a single state that we need to read and update concurrently. When we have multiple fibers reading or writing to the same memory location we encounter the race condition. The main goal in every concurrent program is to have a consistent view of states among all threads.

There are two major concurrency models which try to solve this problem:

1. **Shared State** — In this model, all threads communicate with each other by sharing the same memory location.

2. **Message Passing (Distributed State)** — This model provides primitives for sending and receiving messages, and the state is distributed. Each thread of execution has its own state. 

The _Shared State_ model has two main solutions:

1. **Lock-based** — In the locking model, the general primitives for synchronization are _locks_ that control access to critical sections. When a thread wants to modify the critical section, it acquires the lock and says _I'm the only thread that is allowed to modify the state right now_, and after its work finished it unlocks the critical section and says _I'm done, any other thread can modify this memory section_.

2. **Non-blocking** — Non-blocking algorithms usually use hardware-intrinsic atomic operations like `compare-and-swap` (CAS), without using any locks. This method follows an optimistic design with a transactional memory mechanism to roll back in conflict situations.

## Implications of Locking Mechanism

There are several drawbacks with lock-based concurrency:

1. Incorrect use of locks can lead to deadlocks. We need to care about the locking orders. If we don't place the locks in the right order, we may encounter a deadlock situation.

2. Identifying the critical section of code that is vulnerable to race conditions is overwhelming. We should always care about them and remember to lock everywhere it's required.

3. It makes our software design very sophisticated to become scalable and reliable. It doesn't scale with program size and complexity.

4. To prevent missing the releasing of the acquired locks, we should always care about exceptions and error handling inside locking sections. 

5. The locking mechanism violates the encapsulation property of the pieces of our programs. So systems that are built on a locking mechanism are difficult to compose without knowing about their internals.

## Lock-free Concurrency Model

As the lock-oriented programming does not compose and has lots of drawbacks, ZIO uses a _lock-free concurrency model_ which is a variation of non-blocking algorithms. The magic behind all of ZIO concurrency primitives is that they use the CAS (_compare-and-set_) operation. 

Let's see how the `modify` function of `Ref` is implemented without any locking mechanism:

```scala mdoc:invisible
import java.util.concurrent.atomic.AtomicReference
import zio.{UIO, ZIO}
```

```scala mdoc:silent
case class Ref[A](value: AtomicReference[A]) { self =>
  def modify[B](f: A => (B, A)): UIO[B] = ZIO.succeed {
    var loop = true
    var b: B = null.asInstanceOf[B]
    while (loop) {
      val current = value.get
      val tuple   = f(current)
      b = tuple._1
      loop = !value.compareAndSet(current, tuple._2)
    }
    b
  }
}
```

The idea behind the `modify` is that a variable is only updated if it still has the same value as the time we had read the value from the original memory location. If the value has changed, it retries in the while loop until it succeeds. 

## Advantage of Using ZIO Concurrency

Let's point out the key properties of the ZIO concurrency model:

1. **Composable** — Due to the use of the lock-free concurrency model, ZIO brings us composable concurrency primitives and lots of great combinators in a declarative style.

> **Note:** `Ref` and `Promise` and subsequently all other ZIO concurrency primitives that are on top of these two basic primitives **are not _transactionally_ composable**.
>
> We cannot do transactional changes across two or more of such concurrency primitives. They are susceptible to race conditions and deadlocks. **So don't use them if you need to perform an atomic operation on top of a composed sequence of multiple state-changing operations. In such a case use [`STM`](../stm/index.md) instead**. 

2. **Non-blocking** — All of the ZIO primitives are a hundred percent asynchronous and nonblocking.

3. **Resource Safety** — ZIO concurrency model comes with strong guarantees of resource safety. If any interruption occurs in between concurrent operations, it won't leak any resource. So it allows us to write compositional operators like timeout and racing without worrying about any leaks.

## Concurrency Primitives

Let's take a quick look at ZIO concurrency primitives, what they are and why they exist.

### Basic Operations

`Ref` and `Promise` are the two simple concurrency primitives which provide an orthogonal basis for building concurrency structures. They are assembly language of other concurrent data structures:

- **[Ref](ref.md)** — `Ref` and all its variant like [`Ref.Synchronized`](refsynchronized.md) are building blocks for writing concurrent stateful applications. Anytime we need to share information between multiple fibers, and those fibers have to update the same information, they need to communicate through something that provides the guarantee of atomicity. So all of these `Ref` primitives are atomic and thread-safe. They provide us a reliable foundation for synchronizing concurrent programs.

- **[Promise](promise.md)** — A `Promise` is a model of a variable that may be set a single time, and awaited on by many fibers. This primitive is very useful when we need some point of synchronization between two or multiple fibers.

By using these two simple primitives, we can build lots of other asynchronous concurrent data structures like `Semaphore`, `Queue` and `Hub`.

### Others

- **[Semaphore](semaphore.md)** — A `Semaphore` is an asynchronous (non-blocking) semaphore that plays well with ZIO's interruption. `Semaphore` is a generalization of a mutex. It has a certain number of permits, which can be held and released concurrently by different parties. Attempts to acquire more permits than available result in the acquiring fiber being suspended until the specified number of permits become available.

- **[Queue](queue.md)** — A `Queue` is an asynchronous queue that never blocks, which is safe for multiple concurrent producers and consumers.

- **[Hub](hub.md)** - A `Hub` is an asynchronous message hub that allows publishers to efficiently broadcast values to many subscribers.
  