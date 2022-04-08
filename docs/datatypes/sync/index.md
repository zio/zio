---
id: index
title: "Introduction"
---

When we access shared resources in a concurrent environment, we should choose a proper synchronization mechanism to avoid incorrect results and data inconsistencies.

ZIO has several synchronization tools:

- **[`ReentrantLock`](reentrantlock.md)**— The `ReentrantLock` is a synchronization tool that is useful for synchronizing blocks of code.
- **[`CountDownLatch`](countdownlatch.md)**— The `CountDownLatch` is a synchronization tool that allows one or more fibers to wait for the finalization of multiple operations.
- **[`CyclicBarrier`](cyclicbarrier.md)**— The `CyclicBarrier` is a synchronization tool that allows a set of fibers to all wait for each other to reach a common barrier point.

It also has some concurrent data structure:

- **[`ConcurrentMap`](concurrentmap.md)**— A `ConcurrentMap` is a Map wrapper over `java.util.concurrent.ConcurrentHashMap`
- **[`ConcurrentSet`](concurrentset.md)**— A `ConcurrentSet` is a Set wrapper over `java.util.concurrent.ConcurrentHashMap`.
