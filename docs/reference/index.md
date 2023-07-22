---
id: index
title: "Introduction"
---

ZIO contains a few data types that can help you solve complex problems in asynchronous and concurrent programming. ZIO data types categorize into these sections:

1. [Core Data Types](#core-data-types)
2. [Contextual Data Types](#contextual-data-types)
3. [Concurrency](#concurrency)
    - [Fiber Primitives](#fiber-primitives)
    - [Concurrency Primitives](#concurrency-primitives)
    - [Synchronization Aids](#synchronization-aids)
    - [STM](#stm)
3. [Resource Management](#resource-management)
6. [Streaming](#streaming)
7. [Miscellaneous](#miscellaneous)

## Core Data Types
 - **[ZIO](core/zio.md)** — A `ZIO` is a value that models an effectful program, which might fail or succeed.
   + **[UIO](core/uio.md)** — An `UIO[A]` is a type alias for `ZIO[Any, Nothing, A]`.
   + **[URIO](core/urio.md)** — An `URIO[R, A]` is a type alias for `ZIO[R, Nothing, A]`.
   + **[Task](core/task.md)** — A `Task[A]` is a type alias for `ZIO[Any, Throwable, A]`.
   + **[RIO](core/rio.md)** — A `RIO[R, A]` is a type alias for `ZIO[R, Throwable, A]`.
   + **[IO](core/io.md)** — An `IO[E, A]` is a type alias for `ZIO[Any, E, A]`.
 - **[Exit](core/exit.md)** — An `Exit[E, A]` describes the result of executing an `IO` value.
 - **[Cause](core/cause.md)** - `Cause[E]` is a description of a full story of a fiber failure. 
 - **[Runtime](core/runtime.md)** — A `Runtime[R]` is capable of executing tasks within an environment `R`.

## Contextual Data Types
- **[Has](contextual/has.md)** — The trait `Has[A]` is used with the [ZIO environment](contextual/index.md#zio-environment) to express an effect's dependency on a service of type `A`. 
- **[ZLayer](contextual/zlayer.md)** — The `ZIO[-R, +E, +A]` data type describes an effect that requires an input type of `R`, as an environment, may fail with an error of type `E` or succeed and produces a value of type `A`.
    + **[RLayer](contextual/rlayer.md)** — `RLayer[-RIn, +ROut]` is a type alias for `ZLayer[RIn, Throwable, ROut]`, which represents a layer that requires `RIn` as its input, it may fail with `Throwable` value, or returns `ROut` as its output.
    + **[ULayer](contextual/ulayer.md)** — ULayer[+ROut] is a type alias for ZLayer[Any, Nothing, ROut], which represents a layer that doesn't require any services as its input, it can't fail, and returns ROut as its output.
    + **[Layer](contextual/layer.md)** — Layer[+E, +ROut] is a type alias for ZLayer[Any, E, ROut], which represents a layer that doesn't require any services, it may fail with an error type of E, and returns ROut as its output.
    + **[URLayer](contextual/urlayer.md)** — URLayer[-RIn, +ROut] is a type alias for ZLayer[RIn, Nothing, ROut], which represents a layer that requires RIn as its input, it can't fail, and returns ROut as its output.
    + **[TaskLayer](contextual/task-layer.md)** — TaskLayer[+ROut] is a type alias for ZLayer[Any, Throwable, ROut], which represents a layer that doesn't require any services as its input, it may fail with Throwable value, and returns ROut as its output.

## Concurrency

### Fiber Primitives
 - **[Fiber](fiber/fiber.md)** — A fiber value models an `IO` value that has started running, and is the moral equivalent of a green thread.
 - **[FiberRef](fiber/fiberref.md)** — `FiberRef[A]` models a mutable reference to a value of type `A`. As opposed to `Ref[A]`, a value is bound to an executing `Fiber` only.  You can think of it as Java's `ThreadLocal` on steroids.
 - **[Fiber.Status](fiber/fiberstatus.md)** — `Fiber.Status` describe the current status of a Fiber.
 - **[Fiber.Id](fiber/fiberid.md)** — `Fiber.Id` describe the unique identity of a Fiber.
 
### Concurrency Primitives
 - **[Hub](concurrency/hub.md)** - A `Hub` is an asynchronous message hub that allows publishers to efficiently broadcast values to many subscribers.
 - **[Promise](concurrency/promise.md)** — A `Promise` is a model of a variable that may be set a single time, and awaited on by many fibers.
 - **[Semaphore](concurrency/semaphore.md)** — A `Semaphore` is an asynchronous (non-blocking) semaphore that plays well with ZIO's interruption.
- **[ZRef](concurrency/zref.md)** — A `ZRef[EA, EB, A, B]` is a polymorphic, purely functional description of a mutable reference. The fundamental operations of a `ZRef` are `set` and `get`.
  + **[Ref](concurrency/ref.md)** — `Ref[A]` models a mutable reference to a value of type `A`. The two basic operations are `set`, which fills the `Ref` with a new value, and `get`, which retrieves its current content. All operations on a `Ref` are atomic and thread-safe, providing a reliable foundation for synchronizing concurrent programs.
- **[ZRefM](concurrency/zrefm.md)** — A `ZRefM[RA, RB, EA, EB, A, B]` is a polymorphic, purely functional description of a mutable reference. 
  + **[RefM](concurrency/refm.md)** — `RefM[A]` models a **mutable reference** to a value of type `A` in which we can store **immutable** data, and update it atomically **and** effectfully.
 - **[Queue](concurrency/queue.md)** — A `Queue` is an asynchronous queue that never blocks, which is safe for multiple concurrent producers and consumers.

### Synchronization aids

- **[ConcurrentMap](sync/concurrentmap.md)** — A Map wrapper over `java.util.concurrent.ConcurrentHashMap`
- **[ConcurrentSet](sync/concurrentset.md)** — A Set implementation over `java.util.concurrent.ConcurrentHashMap`
- **[CountdownLatch](sync/countdownlatch.md)** — A synchronization aid that allows one or more fibers to wait until a
  set of operations being performed in other fibers completes.
- **[CyclicBarrier](sync/cyclicbarrier.md)** — A synchronization aid that allows a set of fibers to all wait for each
  other to reach a common barrier point.

### STM

- **[STM](stm/stm.md)** - An `STM` represents an effect that can be performed transactionally resulting in a failure or success.
- **[TArray](stm/tarray.md)** - A `TArray` is an array of mutable references that can participate in transactions.
- **[TSet](stm/tset.md)** - A `TSet` is a mutable set that can participate in transactions.
- **[TMap](stm/tmap.md)** - A `TMap` is a mutable map that can participate in transactions.
- **[TRef](stm/tref.md)** - A `TRef` is a mutable reference to an immutable value that can participate in transactions.
- **[TPriorityQueue](stm/tpriorityqueue.md)** - A `TPriorityQueue` is a mutable priority queue that can participate in transactions.
- **[TPromise](stm/tpromise.md)** - A `TPromise` is a mutable reference that can be set exactly once and can participate in transactions.
- **[TQueue](stm/tqueue.md)** - A `TQueue` is a mutable queue that can participate in transactions.
- **[TReentrantLock](stm/treentrantlock.md)** - A `TReentrantLock` is a reentrant read / write lock that can be composed.
- **[TSemaphore](stm/tsemaphore.md)** - A `TSemaphore` is a semaphore that can participate in transactions.

## Resource Management

 - **[Managed](resource/managed.md)** — A `Managed` is a value that describes a perishable resource that may be consumed only once inside a given scope.
 
## Streaming
The following datatypes can be found in ZIO streams library:
 - **[ZStream](stream/zstream.md)** — A `ZStream` is a lazy, concurrent, asynchronous source of values.
 - **[ZSink](stream/zsink.md)** — A `ZSink` is a consumer of values from a `ZStream`, which may produces a value when it has consumed enough.
 
## Miscellaneous
 - **[Chunk](misc/chunk.md)** — ZIO `Chunk`: Fast, Pure Alternative to Arrays
 - **[Schedule](misc/schedule.md)** — A `Schedule` is a model of a recurring schedule, which can be used for repeating successful `IO` values, or retrying failed `IO` values.

To learn more about these data types, please explore the pages above, or check out the Scaladoc documentation.
