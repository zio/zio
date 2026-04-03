---
id: index
title: "Introduction to ZIO's Synchronization Primitives"
---

When we access shared resources in a concurrent environment, we should choose a proper synchronization mechanism to avoid incorrect results and data inconsistencies. ZIO provides a set of synchronization primitives and concurrent data structures in the `zio-concurrent` module that helps us to achieve the desired synchronization.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-concurrent" % "2.x.x"
```

## Synchronization

ZIO has several synchronization tools:

- **[`ReentrantLock`](reentrantlock.md)**— The `ReentrantLock` is a synchronization tool that is useful for synchronizing blocks of code.
- **[`CountDownLatch`](countdownlatch.md)**— The `CountDownLatch` is a synchronization tool that allows one or more fibers to wait for the finalization of multiple operations.
- **[`CyclicBarrier`](cyclicbarrier.md)**— The `CyclicBarrier` is a synchronization tool that allows a set of fibers to all wait for each other to reach a common barrier point.

## Concurrent Data Structures

It also has some concurrent data structure:

- **[`ConcurrentMap`](concurrentmap.md)**— A `ConcurrentMap` is a Map wrapper over `java.util.concurrent.ConcurrentHashMap`
- **[`ConcurrentSet`](concurrentset.md)**— A `ConcurrentSet` is a Set wrapper over `java.util.concurrent.ConcurrentHashMap`.
