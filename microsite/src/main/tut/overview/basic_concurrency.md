---
layout: docs
section: overview
title:  "Basic Concurrency"
---

# {{page.title}}

ZIO's concurrency is built on _fibers_, which are lightweight "green threads" implemented by the ZIO runtime.

Unlike operating system threads, fibers consume almost no memory, have growable and shrinkable stacks, don't waste resources blocking, and will be garbage collected automatically if they are inactive and unreachable.

Fibers are scheduled by the ZIO runtime and will cooperatively yield to each other, which enables multitasking even when operating in a single-threaded environment (like Javascript, or even the JVM when configured with one thread).

All effects in ZIO are executed by _some_ fiber. If you did not create the fiber, then the fiber was created by some operation you are using (if the operation is concurrent or parallel), or by the runtime system.

Even if you only write "single-threaded" code, with no parallel or concurrent operations, then there will be at least one fiber: the "main" fiber that executes your effect.

# Fiber Type

The `Fiber[E, A]` data type in ZIO has two type parameters:

 - **`E` Failure Type**. This is the type of value the effect being executed by the fiber may fail with.
 - **`A` Success Type**. This is the type of value the effect being executed by the fiber may succeed with.

Fibers do not have an `R` type parameter, because they model effects that are already being executed, and which therefore have already had their required environment provided to them.

```tut:invisible

import scalaz.zio._
```

# Forking Effects

The most primitive way of creating a fiber is to take an effect and _fork_ it. Conceptually, _forking_ an effect immediately begins executing the effect on a new fiber, giving you the new fiber.

The following code creates a single fiber, which executes `fib(100)`:

```scala

def fib(n: Long): UIO[Long] = 
  if (n <= 1) {
    UIO.succeed(n)
  } else {
    fib(n - 1).zipWith(fib(n - 2))(_ + _)
  }

val z: UIO[Fiber[Nothing, Long]] = 
  for {
    fiber <- fib(100).fork
  } yield fiber
```

# Joining Effects

One of the methods on `Fiber` is `join`, which allows another fiber to obtain the result of the fiber being joined. This is similar to awaiting the final result of the fiber, whether that is failure or success.

```tut:silent
for {
  fiber   <- IO.succeed("Hi!").fork
  message <- fiber.join
} yield message
```

# Awaiting Fibers

Another method on `Fiber` is `await`, which allows for inspecting the result of a completed `Fiber`. This provides access to full details on how the fiber completed, represented by the `Exit` data type.

```tut:silent
for {
  fiber <- IO.succeed("Hi!").fork
  exit  <- fiber.await
} yield exit
```

# Interrupting Fibers

A fiber whose result is no longer needed may be _interrupted_, which immediately terminates the fiber, safely releasing all resources and running all finalizers.

Like `await`, `Fiber#interrupt` returns an `Exit` describing how the fiber terminated.

```tut:silent
for {
  fiber <- IO.succeed("Hi!").forever.fork
  exit  <- fiber.interrupt
} yield exit
```

By design, `interrupt` does not resume until the fiber has terminated. If this behavior is not desired, you can `fork` the interruption itself:

```tut:silent
for {
  fiber <- IO.succeed("Hi!").forever.fork
  _     <- fiber.interrupt.fork
} yield exit
```