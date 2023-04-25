---
id: index
title: "Introduction"
---

ZIO contains a few data types that can help you solve complex problems in asynchronous and concurrent programming. ZIO data types categorize into these sections:

1. [Core Data Types](#core-data-types)
2. [Contextual Data Types](#contextual-data-types)
3. [State Management](#state-management)
4. [Concurrency](#concurrency)
    - [Fiber Primitives](#fiber-primitives)
    - [Concurrency Primitives](#concurrency-primitives)
    - [Synchronization Aids](#synchronization-aids)
    - [STM](#stm)
5. [Resource Management](#resource-management)
6. [Streaming](#streaming)
7. [Metrics](#metrics)
8. [Testing](#testing)
9. [Miscellaneous](#miscellaneous)

## Core Data Types
- **[ZIO](core/zio/zio.md)** — `ZIO` is a value that models an effectful program, which might fail or succeed.
    + **[UIO](core/zio/uio.md)** — `UIO[A]` is a type alias for `ZIO[Any, Nothing, A]`.
    + **[URIO](core/zio/urio.md)** — `URIO[R, A]` is a type alias for `ZIO[R, Nothing, A]`.
    + **[Task](core/zio/task.md)** — `Task[A]` is a type alias for `ZIO[Any, Throwable, A]`.
    + **[RIO](core/zio/rio.md)** — `RIO[R, A]` is a type alias for `ZIO[R, Throwable, A]`.
    + **[IO](core/zio/io.md)** — `IO[E, A]` is a type alias for `ZIO[Any, E, A]`.
- **[ZIOApp](core/zioapp.md)** — `ZIOApp` and the `ZIOAppDefault` are entry points for ZIO applications.
- **[Runtime](core/runtime.md)** — `Runtime[R]` is capable of executing tasks within an environment `R`.
- **[Exit](core/exit.md)** — `Exit[E, A]` describes the result of executing an `IO` value.
- **[Cause](core/cause.md)** — `Cause[E]` is a description of a full story of a fiber failure.

## Contextual Data Types

- **[ZEnvironment](contextual/zenvironment.md)** — `ZEnvironment[R]` is a built-in type-level map for the `ZIO` data type which is responsible for maintaining the environment of a `ZIO` effect.
- **[ZLayer](contextual/zlayer.md)** — `ZLayer[-RIn, +E, +ROut]` is a recipe to build an environment of type `ROut`, starting from a value `RIn`, and possibly producing an error `E` during creation.
    + **[RLayer](contextual/rlayer.md)** — `RLayer[-RIn, +ROut]` is a type alias for `ZLayer[RIn, Throwable, ROut]`, which represents a layer that requires `RIn` as its input, it may fail with `Throwable` value, or returns `ROut` as its output.
    + **[ULayer](contextual/ulayer.md)** — `ULayer[+ROut]` is a type alias for `ZLayer[Any, Nothing, ROut]`, which represents a layer that doesn't require any services as its input, it can't fail, and returns `ROut` as its output.
    + **[Layer](contextual/layer.md)** — `Layer[+E, +ROut]` is a type alias for `ZLayer[Any, E, ROut]`, which represents a layer that doesn't require any services, it may fail with an error type of `E`, and returns `ROut` as its output.
    + **[URLayer](contextual/urlayer.md)** — `URLayer[-RIn, +ROut]` is a type alias for `ZLayer[RIn, Nothing, ROut]`, which represents a layer that requires `RIn` as its input, it can't fail, and returns `ROut` as its output.
    + **[TaskLayer](contextual/task-layer.md)** — `TaskLayer[+ROut]` is a type alias for `ZLayer[Any, Throwable, ROut]`, which represents a layer that doesn't require any services as its input, it may fail with `Throwable` value, and returns `ROut` as its output.

## State Management

- **[ZState](state-management/zstate.md)**— It models a state that can be read from and written to during the execution of an effect.
- **[Ref](state-management/global-shared-state.md)**— `Ref[A]` models a mutable reference to a value of type `A`.
- **[FiberRef](state-management/fiberref.md)**— `FiberRef[A]` models a mutable reference to a value of type `A`. As opposed to `Ref[A]`, a value is bound to an executing `Fiber` only.  You can think of it as Java's `ThreadLocal` on steroids.

## Concurrency

### Fiber Primitives

- **[Fiber](fiber/fiber.md)** — A fiber value models an `IO` value that has started running, and is the moral equivalent of a green thread.
- **[Fiber.Status](fiber/fiberstatus.md)** — `Fiber.Status` describe the current status of a Fiber.
- **[FiberId](fiber/fiberid.md)** — `FiberId` describe the unique identity of a Fiber.

### Concurrency Primitives

- **[Hub](concurrency/hub.md)** — A `Hub` is an asynchronous message hub that allows publishers to efficiently broadcast values to many subscribers.
- **[Promise](concurrency/promise.md)** — A `Promise` is a model of a variable that may be set a single time, and awaited on by many fibers.
- **[Semaphore](concurrency/semaphore.md)** — A `Semaphore` is an asynchronous (non-blocking) semaphore that plays well with ZIO's interruption.
- **[Ref](concurrency/ref.md)** — `Ref[A]` models a mutable reference to a value of type `A`. The two basic operations are `set`, which fills the `Ref` with a new value, and `get`, which retrieves its current content. All operations on a `Ref` are atomic and thread-safe, providing a reliable foundation for synchronizing concurrent programs.
- **[Ref.Synchronized](concurrency/refsynchronized.md)** — `Ref.Synchronized[A]` models a **mutable reference** to a value of type `A` in which we can store **immutable** data, and update it atomically **and** effectfully.
- **[Queue](concurrency/queue.md)** — A `Queue` is an asynchronous queue that never blocks, which is safe for multiple concurrent producers and consumers.

### Synchronization Aids

- **[ReentrantLock](sync/reentrantlock.md)**— The `ReentrantLock` is a synchronization tool that is useful for synchronizing blocks of code.
- **[CountdownLatch](sync/countdownlatch.md)** — A synchronization aid that allows one or more fibers to wait until a set of operations being performed in other fibers completes.
- **[CyclicBarrier](sync/cyclicbarrier.md)** — A synchronization aid that allows a set of fibers to all wait for each other to reach a common barrier point.
- **[ConcurrentMap](sync/concurrentmap.md)** — A Map wrapper over `java.util.concurrent.ConcurrentHashMap`
- **[ConcurrentSet](sync/concurrentset.md)** — A Set implementation over `java.util.concurrent.ConcurrentHashMap`

### STM

- **[STM](stm/stm.md)** — An `STM` represents an effect that can be performed transactionally resulting in a failure or success.
- **[TArray](stm/tarray.md)** — A `TArray` is an array of mutable references that can participate in transactions.
- **[TSet](stm/tset.md)** — A `TSet` is a mutable set that can participate in transactions.
- **[TMap](stm/tmap.md)** — A `TMap` is a mutable map that can participate in transactions.
- **[TRef](stm/tref.md)** — A `TRef` is a mutable reference to an immutable value that can participate in transactions.
- **[TPriorityQueue](stm/tpriorityqueue.md)** — A `TPriorityQueue` is a mutable priority queue that can participate in transactions.
- **[TPromise](stm/tpromise.md)** — A `TPromise` is a mutable reference that can be set exactly once and can participate in transactions.
- **[TQueue](stm/tqueue.md)** — A `TQueue` is a mutable queue that can participate in transactions.
- **[TReentrantLock](stm/treentrantlock.md)** — A `TReentrantLock` is a reentrant read / write lock that can be composed.
- **[TSemaphore](stm/tsemaphore.md)** — A `TSemaphore` is a semaphore that can participate in transactions.

## Resource Management

- **[Scope](resource/scope.md)** — A scope in which resources can safely be used.
- **[ZPool](resource/zpool.md)** — An asynchronous and concurrent generalized pool of reusable resources.

## Streaming

- **[ZStream](stream/zstream/index.md)** — `ZStream` is a lazy, concurrent, asynchronous source of values.
    + **Stream** — `Stream[E, A]` is a type alias for `ZStream[Any, E, A]`, which represents a ZIO stream that does not require any services, and may fail with an `E`, or produce elements with an `A`.
- **[ZSink](stream/zsink/index.md)** — `ZSink` is a consumer of values from a `ZStream`, which may produce a value when it has consumed enough.
    + **[Sink](stream/zsink/index.md)** — `Sink[InErr, A, OutErr, L, B]` is a type alias for `ZSink[Any, InErr, A, OutErr, L, B]`.
- **[ZPipeline](stream/zpipeline.md)** — `ZPipeline` is a polymorphic stream transformer.
- **[SubscriptionRef](stream/subscriptionref.md)** — `SubscriptionRef[A]` contains a current value of type `A` and a stream that can be consumed to observe all changes to that value.

## Metrics

IO supports 5 types of Metrics:

- **[Counter](observability/metrics/counter.md)** — The Counter is used for any value that increases over time like _request counts_.
- **[Gauge](observability/metrics/gauge.md)** — The gauge is a single numerical value that can arbitrary goes up or down over time like _memory usage_.
- **[Histogram](observability/metrics/histogram.md)** — The Histogram is used to track the distribution of a set of observed values across a set of buckets like _request latencies_.
- **[Summary](observability/metrics/summary.md)** — The Summary represents a sliding window of a time series along with metrics for certain percentiles of the time series, referred to as quantiles like _request latencies_.
- **[Frequency](observability/metrics/frequency.md)** — The Frequency is a metric that counts the number of occurrences of distinct string values.

## Testing

- **[Spec](test/spec.md)**— A `Spec[R, E]` is the backbone of ZIO Test. All specs require an environment of type `R` and may potentially fail with an error of type `E`.
- **[Assertion](test/assertions/index.md)**— An `Assertion[A]` is a test assertion that can be used to assert the predicate of type `A => Boolean`.
- **[TestAspect](test/aspects/index.md)**— A `TestAspect` is an aspect that can be weaved into specs. We can think of an aspect as a polymorphic function, capable of transforming one test into another.
- **[Gen](test/property-testing/built-in-generators.md)**— A `Gen[R, A]` represents a generator of values of type `A`, which requires an environment `R`.
- **Test Service**— ZIO Test has the following out-of-the-box test services:
    - **[TestConsole](test/services/console.md)**— It allows testing of applications that interact with the console.
    - **[TestClock](test/services/clock.md)**— We can deterministically and efficiently test effects involving the passage of time without actually having to wait for the full amount of time to pass.
    - **[TestRandom](test/services/random.md)**— This service allows us having fully deterministic testing of code that deals with Randomness.
    - **[TestSystem](test/services/system.md)**— It supports deterministic testing of effects involving system properties.
    - **[Live](test/services/live.md)**— It provides access to the live environment from within the test environment for effects.
    - **[TestConfig](test/services/test-config.md)**— It provides access to default configuration settings used by ZIO Test.
    - **[Sized](test/services/sized.md)**— It enables _Sized Generators_ to access the size from the ZIO Test environment.

## Miscellaneous

- **[Chunk](stream/chunk.md)**— `Chunk` is a fast, pure alternative to Arrays.
- **[Supervisor](observability/supervisor.md)**— `Supervisor[A]` is allowed to supervise the launching and termination of fibers, producing some visible value of type `A` from the supervision.

To learn more about these data types, please explore the pages above, or check out the Scaladoc documentation.
