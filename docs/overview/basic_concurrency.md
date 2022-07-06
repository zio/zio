---
id: overview_basic_concurrency
title:  "Basic Concurrency"
---

ZIO is a highly concurrent framework, powered by _fibers_, which are lightweight virtual threads that achieve massive scalability compared to threads.

ZIO augments the modern virtual threading model with pervasive, automatic, resource-safe interruption, which allows ZIO to instantly and deeply cancel running computations when their results are no longer needed, making ZIO applications automatically globally-efficient.

In this section, you will learn the basics of fibers, and become acquainted with some of the powerful high-level operators that are powered by fibers.

## Fibers

All effects in ZIO are executed by _some_ fiber. If you did not create the fiber, then the fiber was created by some operation you are using (if the operation is concurrent or parallel), or by the ZIO runtime system.

Even if you only write "single-threaded" code, with no parallel or concurrent operations, there will be at least one fiber: the "main" fiber that executes your effect.

Like operating system-level threads, ZIO fibers have a well-defined lifecycle, defined by the effect they are executing.

Every fiber exits with failure or success, depending on whether the effect it is executing fails or succeeds.

Also like operating system threads, ZIO fibers have unique identities, stacks (including stack traces), local state, and a status (such as _done_, _running_, or _suspended_).

Unlike operating system threads, ZIO fibers consume almost no memory, they have dynamic stacks that grow and shrink, they don't waste operating system threads with blocking operations, they can be safely interrupted at any point in time, they are strongly typed, they have enumerable child fibers, and they will be garbage collected automatically if they are suspended and cannot be reactivated.

Fibers are scheduled onto operating system threads by the ZIO runtime. Because fibers cooperatively yield to each other, ZIO fibers always execute concurrently, even when running in a single-threaded environment like JavaScript (or the JVM, when ZIO is configured with one work thread).

### The Fiber Data Type

The `Fiber` data type in ZIO represents a "handle" on the execution of an effect. The `Fiber` data type is most similar to Scala's `Future` data type, which represents a "handle" on a running asynchronous operation.

The `Fiber[E, A]` data type in ZIO has two type parameters:

 - **`E` Failure Type**. The fiber may fail with a value of this type.
 - **`A` Success Type**. The fiber may succeed with a value of this type.

Fibers do not have an `R` type parameter, because fibers only execute effects that have already had their requirements provided to them.

```scala mdoc:invisible

import zio._
```

### Forking Effects

The most fundamental way of creating a fiber is to take an existing effect and _fork_ it. Conceptually, _forking_ an effect begins executing the effect on a new fiber, giving you a reference to the newly-created fiber.

The following code creates a single fiber using `fork`, which executes `fib(100)` independently of the main fiber:

```scala mdoc:silent
def fib(n: Long): UIO[Long] = 
  ZIO.suspendSucceed {
    if (n <= 1) ZIO.succeed(n)
    else fib(n - 1).zipWith(fib(n - 2))(_ + _)
  }

val fib100Fiber: UIO[Fiber[Nothing, Long]] = 
  for {
    fiber <- fib(100).fork
  } yield fiber
```

### Joining Fibers

One of the methods on `Fiber` is `Fiber#join`, which returns an effect. The effect returned by `Fiber#join` will succeed or fail as per the fiber:

```scala mdoc:silent
for {
  fiber   <- ZIO.succeed("Hi!").fork
  message <- fiber.join
} yield message
```

When a parent fiber joins a child fiber, it will succeed or fail in the same way as the child fiber, and the local states of the fibers will be merged.

### Awaiting Fibers

Another method on `Fiber` is `Fiber#await`, which returns an effect containing an `Exit` value, which provides full information on how the fiber completed.

```scala mdoc:silent
for {
  fiber <- ZIO.succeed("Hi!").fork
  exit  <- fiber.await
} yield exit
```

Awaiting the exit values of fibers is different than joining them, because awaiting will not tie the fate of the parent fiber to that of the child fiber, and nor will it attempt to merge the local states of the fibers.

### Interrupting Fibers

A fiber whose result is no longer needed may be _interrupted_, which immediately terminates the fiber, safely releasing all resources by running all finalizers.

Like `await`, `Fiber#interrupt` returns an `Exit` describing how the fiber completed.

```scala mdoc:silent
for {
  fiber <- ZIO.succeed("Hi!").forever.fork
  exit  <- fiber.interrupt
} yield exit
```

By design, the effect returned by `Fiber#interrupt` does not resume until the fiber has completed, which helps ensure your code does not spin up new fibers until the old one has terminated.

If this behavior (often called "back-pressuring") is not desired, you can `ZIO#fork` the interruption itself into a new fiber:

```scala mdoc:silent
for {
  fiber <- ZIO.succeed("Hi!").forever.fork
  _     <- fiber.interrupt.fork // I don't care!
} yield ()
```

There is a shorthand for background interruption, which is the method `Fiber#interruptFork`.

### Composing Fibers

ZIO lets you compose fibers with `Fiber#zip` or `Fiber#zipWith`. 

These methods combine two fibers into a single fiber that produces the results of both. If either fiber fails, then the composed fiber will fail.

```scala mdoc:silent
for {
  fiber1 <- ZIO.succeed("Hi!").fork
  fiber2 <- ZIO.succeed("Bye!").fork
  fiber   = fiber1.zip(fiber2)
  tuple  <- fiber.join
} yield tuple
```

Another way fibers compose is with `Fiber#orElse`. If the first fiber succeeds, the composed fiber will succeed with its result; otherwise, the composed fiber will complete with the exit value of the second fiber (whether success or failure).

```scala mdoc:silent
for {
  fiber1 <- ZIO.fail("Uh oh!").fork
  fiber2 <- ZIO.succeed("Hurray!").fork
  fiber   = fiber1.orElse(fiber2)
  message  <- fiber.join
} yield message
```

## Parallelism

ZIO provides parallel versions of many methods, which are named with a `Par` suffix that helps you identify opportunities to parallelize your code.

For example, the ordinary `ZIO#zip` method zips two effects together sequentially. But there is also a `ZIO#zipPar` method, which zips two effects together in parallel.

The following table summarizes some of the sequential operations and their corresponding parallel versions:

| **Description**                | **Sequential**    | **Parallel**         |
| -----------------------------: | :---------------: | :------------------: |
| Zips two effects into one      | `ZIO#zip`         | `ZIO#zipPar`         |
| Zips two effects into one      | `ZIO#zipWith`     | `ZIO#zipWithPar`     |
| Zips multiple effects into one | `ZIO#tupled`      | `ZIO#tupledPar`      |
| Collects from many effects     | `ZIO.collectAll`  | `ZIO.collectAllPar`  |
| Effectfully loop over values   | `ZIO.foreach`     | `ZIO.foreachPar`     |
| Reduces many values            | `ZIO.reduceAll`   | `ZIO.reduceAllPar`   |
| Merges many values             | `ZIO.mergeAll`    | `ZIO.mergeAllPar`    |

Because all these parallel operators return all the results, if any effect being parallelized fails, ZIO will automatically cancel the other running effects, because their results will not be used.

If the fail-fast behavior is not desired, potentially failing effects can be first converted into infallible effects using the `ZIO#either` or `ZIO#option` methods.

## Racing

ZIO lets you race multiple effects concurrently, returning the first successful result:

```scala mdoc:silent
for {
  winner <- ZIO.succeed("Hello").race(ZIO.succeed("Goodbye"))
} yield winner
```

If you want the first success **or** failure, rather than the first success, then you can use `left.either.race(right.either)`, for any two effects `left` and `right`.

## Timeout

ZIO has resource-safe, compositional timeouts that work on "small" effects, such as querying a database or calling a cloud API, or even "large" effects, such as running a streaming pipeline or fully handling a web request.

ZIO lets you timeout effects using the `ZIO#timeout` method, which returns a new effect that succeeds with an `Option` value. 

A value of `None` indicates the timeout elapsed before the effect completed.

```scala mdoc:silent
ZIO.succeed("Hello").timeout(10.seconds)
```

If an effect times out, then instead of continuing to execute in the background, it will be interrupted, for automatic efficiency.

## Next Steps

If you are comfortable with basic concurrency, the next step is to learn about [running effects](running_effects.md).
