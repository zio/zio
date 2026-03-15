---
id: fiber
title: Fiber
---

## Overview

A `Fiber` is a lightweight thread of execution that forms the basis of concurrency in ZIO. Fibers are the unit of work in ZIO's runtime system, allowing for efficient, cooperative multitasking.

Fibers are immutable, and the operations on them return new fibers or effects that interact with the original fiber. They are safe to share across threads and are designed to be composed.

## Fiber vs. Promise

A common architectural question is whether `Fiber` (specifically `FiberRuntime`) and `Promise` can be merged into a single concept. While both are concurrency primitives that can be `await`ed, they serve fundamentally different purposes and have distinct semantic guarantees:

- **Single Assignment vs. Computation:**
  - A `Promise` is a **single-assignment** variable. It represents a value that may not be available yet. Once completed (either with a success or a failure), its value cannot change. It is primarily a mechanism for synchronization and communication between fibers.
  - A `Fiber` represents an **active computation**. Its result is determined by the execution of the ZIO effect it encapsulates. While you can `join` a fiber to get its result, the fiber itself is the process, not just the result container.

- **Control and Structure:**
  - A `Promise` can be completed manually from the outside using `Promise#succeed` or `Promise#fail`. This external control is its primary use case (e.g., waiting for a callback, signaling completion of a handshake).
  - A `Fiber`'s outcome is determined internally by the logic of the effect it runs. You cannot manually "complete" a `Fiber` with an arbitrary result; you can only observe its exit upon termination.

- **Error Handling and Supervision:**
  - `Fiber` is deeply integrated into ZIO's runtime model, including supervision, interruption, and propagation of defects. It carries a full execution context.
  - `Promise` is a simpler primitive that holds an `Exit` result but does not have its own execution context, children, or supervision strategy.

Therefore, merging them would violate the single-assignment property of `Promise` and conflate the concept of a running process with a synchronization primitive. They are best kept as distinct abstractions that complement each other.

## Basic Operations

### Forking

You can fork an effect to create a new fiber using `ZIO#fork`. This starts the effect in a new fiber and immediately returns a `Fiber` handle.

```scala mdoc:compile-only
import zio._

for {
  fiber <- ZIO.succeed("Hello, World!").debug.fork
  _     <- fiber.join
} yield ()
```

### Joining

`Fiber#join` waits for the fiber to complete and returns its result. If the fiber fails, the join will fail with the same error.

### Awaiting

`Fiber#await` waits for the fiber to complete but returns an `Exit` value, which describes how the fiber terminated (success, failure, or interruption). This allows you to handle the outcome without throwing exceptions.

### Interrupting

`Fiber#interrupt` sends an interruption signal to the fiber and waits for it to terminate. Interruption in ZIO is cooperative and immediate.
