---
id: datatypes_index
title:  "Summary"
---

ZIO contains a small number of data types that can help you solve complex problems in asynchronous and concurrent programming.

 - **[Chunk](chunk.md)** — ZIO `Chunk`: Fast, Pure Alternative to Arrays
 - **[Fiber](fiber.md)** — A fiber value models an `IO` value that has started running, and is the moral equivalent of a green thread.
 - **[FiberRef](fiberref.md)** — `FiberRef[A]` models a mutable reference to a value of type `A`. As opposed to `Ref[A]`, a value is bound to an executing `Fiber` only.  You can think of it as Java's `ThreadLocal` on steroids.
 - **[Has](has.md)** - A `Has` is used to express an effect's dependency on a service of type `A`.
 - **[Hub](hub.md)** - A `Hub` is an asynchronous message hub that allows publishers to efficiently broadcast values to many subscribers.
 - **[Managed](managed.md)** — A `Managed` is a value that describes a perishable resource that may be consumed only once inside a given scope.
 - **[Promise](promise.md)** — A `Promise` is a model of a variable that may be set a single time, and awaited on by many fibers.
 - **[Queue](queue.md)** — A `Queue` is an asynchronous queue that never blocks, which is safe for multiple concurrent producers and consumers.
 - **[Ref](ref.md)** — `Ref[A]` models a mutable reference to a value of type `A`. The two basic operations are `set`, which fills the `Ref` with a new value, and `get`, which retrieves its current content. All operations on a `Ref` are atomic and thread-safe, providing a reliable foundation for synchronizing concurrent programs.
 - **[Schedule](schedule.md)** — A `Schedule` is a model of a recurring schedule, which can be used for repeating successful `IO` values, or retrying failed `IO` values.
 - **[Semaphore](semaphore.md)** — A `Semaphore` is an asynchronous (non-blocking) semaphore that plays well with ZIO's interruption.
 - **[STM](stm.md)** - An `STM` represents an effect that can be performed transactionally resulting in a failure or success.
 - **[TArray](tarray.md)** - A `TArray[A]` is an array of mutable references that can participate in transactions.
 - **[TMap](tmap.md)** - A `TMap[A]` is a mutable map that can participate in transactions.
 - **[TPriorityQueue](tpriorityqueue.md)** - A `TPriorityQueue[A]` is a mutable priority queue that can participate in transactions.
 - **[TPromise](tpromise.md)** - A `TPromise` is a mutable reference that can be set exactly once and can participate in transactions.
 - **[TQueue](tqueue.md)** - A `TQueue` is a mutable queue that can participate in transactions.
 - **[TReentrantLock](treentrantlock.md)** - A `TReentrantLock` is a reentrant read / write lock that can be composed.
 - **[TRef](tref.md)** - A `TRef` is a mutable reference to an immutable value that can participate in transactions.
 - **[TSemaphore](tsemaphore.md)** - A `TSemaphore` is a semaphore that can participate in transactions.
 - **[TSet](tset.md)** - A `TSet` is a mutable set that can participate in transactions.
 - **[ZIO](io.md)** — A `ZIO` is a value that models an effectful program, which might fail or succeed.
 - **[ZLayer](zlayer.md)** - A `ZLayer` describes a layer of an application.

Besides the core datatypes, the following datatypes can be found in ZIO streams library:

 - **[Sink](sink.md)** — A `Sink` is a consumer of values from a `Stream`, which may produces a value when it has consumed enough.
 - **[Stream](stream.md)** — A `Stream` is a lazy, concurrent, asynchronous source of values.

To learn more about these data types, please explore the pages above, or check out the Scaladoc documentation.
