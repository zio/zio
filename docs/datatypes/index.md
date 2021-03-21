---
id: index
title: "Introduction"
---

ZIO contains a few data types that can help you solve complex problems in asynchronous and concurrent programming. ZIO data types categorize into these sections:

1. [Core Data Types](#core-data-types)
2. [Fiber Primitives](#fiber-primitives)
3. [Concurrency Primitives](#concurrency-primitives)
4. [STM](#stm)
5. [Resource Safety](#resource-safety)
6. [Runtime](#runtime)
7. [Streaming](#streaming)
8. [Miscellaneous](#miscellaneous)

## Core Data Types
 - **[ZIO](core/io.md)** — A `ZIO` is a value that models an effectful program, which might fail or succeed.
 - **[Exit](core/exit.md)** — An `Exit[E, A]` describes the result of executing an `IO` value.
 - **[Cause](core/cause.md)** - `Cause[E]` is a description of a full story of a fiber failure. 
 - **[ZLayer](core/zlayer.md)** - A `ZLayer` describes a layer of an application.
 
## Fiber Primitives
 - **[Fiber](fiber/fiber.md)** — A fiber value models an `IO` value that has started running, and is the moral equivalent of a green thread.
 - **[FiberRef](fiber/fiberref.md)** — `FiberRef[A]` models a mutable reference to a value of type `A`. As opposed to `Ref[A]`, a value is bound to an executing `Fiber` only.  You can think of it as Java's `ThreadLocal` on steroids.
 - **[Fiber.Status](fiber/fiberstatus.md)** — `Fiber.Status` describe the current status of a Fiber.
 - **[Fiber.Id](fiber/fiberid.md)** — `Fiber.Id` describe the unique identity of a Fiber.
 
## Concurrency Primitives
 - **[Promise](concurrency/promise.md)** — A `Promise` is a model of a variable that may be set a single time, and awaited on by many fibers.
 - **[Semaphore](concurrency/semaphore.md)** — A `Semaphore` is an asynchronous (non-blocking) semaphore that plays well with ZIO's interruption.
 - **[Ref](concurrency/ref.md)** — `Ref[A]` models a mutable reference to a value of type `A`. The two basic operations are `set`, which fills the `Ref` with a new value, and `get`, which retrieves its current content. All operations on a `Ref` are atomic and thread-safe, providing a reliable foundation for synchronizing concurrent programs.
 - **[Queue](concurrency/queue.md)** — A `Queue` is an asynchronous queue that never blocks, which is safe for multiple concurrent producers and consumers.

## STM
 - **[STM](stm/stm.md)** - An `STM` represents an effect that can be performed transactionally resulting in a failure or success.
 - **[TArray](stm/tarray.md)** - A `TArray[A]` is an array of mutable references that can participate in transactions.
 - **[TSet](stm/tset.md)** - A `TSet` is a mutable set that can participate in transactions.
 - **[TMap](stm/tmap.md)** - A `TMap[A]` is a mutable map that can participate in transactions.
 - **[TRef](stm/tref.md)** - A `TRef` is a mutable reference to an immutable value that can participate in transactions.
 - **[TPriorityQueue](stm/tpriorityqueue.md)** - A `TPriorityQueue[A]` is a mutable priority queue that can participate in transactions.
 - **[TPromise](stm/tpromise.md)** - A `TPromise` is a mutable reference that can be set exactly once and can participate in transactions.
 - **[TQueue](stm/tqueue.md)** - A `TQueue` is a mutable queue that can participate in transactions.
 - **[TReentrantLock](stm/treentrantlock.md)** - A `TReentrantLock` is a reentrant read / write lock that can be composed.
 - **[TSemaphore](stm/tsemaphore.md)** - A `TSemaphore` is a semaphore that can participate in transactions.
 
 ## Resource Safety
 - **[Managed](resource/managed.md)** — A `Managed` is a value that describes a perishable resource that may be consumed only once inside a given scope.
 
## Runtime
 - **[Runtime](runtime.md)**
 - **[Platform](platform.md)**
 
## Streaming
The following datatypes can be found in ZIO streams library:
 - **[Stream](stream/stream.md)** — A `Stream` is a lazy, concurrent, asynchronous source of values.
 - **[Sink](stream/sink.md)** — A `Sink` is a consumer of values from a `Stream`, which may produces a value when it has consumed enough.
 
## Miscellaneous
 - **[Chunk](misc/chunk.md)** — ZIO `Chunk`: Fast, Pure Alternative to Arrays
 - **[Schedule](misc/schedule.md)** — A `Schedule` is a model of a recurring schedule, which can be used for repeating successful `IO` values, or retrying failed `IO` values.
 - **[Has](misc/has.md)** - A `Has` is used to express an effect's dependency on a service of type `A`.

To learn more about these data types, please explore the pages above, or check out the Scaladoc documentation.
