---
layout: docs
section: overview
title:  "Basic Concurrency"
---

# {{page.title}}

ZIO has low-level support for concurrency using _fibers_. While fibers are very powerful, they are low-level. To improve productivity, ZIO provides high-level operations built on fibers.

If you are able to do so, you should always use higher-level operations, rather than working with fibers directly. For the sake of completeness, this section introduces both fibers and some of the higher-level operations built on them.

# Fibers

ZIO's concurrency is built on _fibers_, which are lightweight "green threads" implemented by the ZIO runtime.

Unlike operating system threads, fibers consume almost no memory, have growable and shrinkable stacks, don't waste resources blocking, and will be garbage collected automatically if they are inactive and unreachable.

Fibers are scheduled by the ZIO runtime and will cooperatively yield to each other, which enables multitasking even when operating in a single-threaded environment (like Javascript, or even the JVM when configured with one thread).

All effects in ZIO are executed by _some_ fiber. If you did not create the fiber, then the fiber was created by some operation you are using (if the operation is concurrent or parallel), or by the runtime system.

Even if you only write "single-threaded" code, with no parallel or concurrent operations, then there will be at least one fiber: the "main" fiber that executes your effect.

## The Fiber Data Type

The `Fiber[E, A]` data type in ZIO has two type parameters:

 - **`E` Failure Type**. This is the type of value the effect being executed by the fiber may fail with.
 - **`A` Success Type**. This is the type of value the effect being executed by the fiber may succeed with.

Fibers do not have an `R` type parameter, because they model effects that are already being executed, and which therefore have already had their required environment provided to them.

```tut:invisible

import scalaz.zio._
```

## Forking Effects

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

## Joining Effects

One of the methods on `Fiber` is `join`, which allows another fiber to obtain the result of the fiber being joined. This is similar to awaiting the final result of the fiber, whether that is failure or success.

```tut:silent
for {
  fiber   <- IO.succeed("Hi!").fork
  message <- fiber.join
} yield message
```

## Awaiting Fibers

Another method on `Fiber` is `await`, which allows for inspecting the result of a completed `Fiber`. This provides access to full details on how the fiber completed, represented by the `Exit` data type.

```tut:silent
for {
  fiber <- IO.succeed("Hi!").fork
  exit  <- fiber.await
} yield exit
```

## Interrupting Fibers

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
} yield ()
```

## Composing Fibers

Fibers may be composed in several ways. 

One way fibers may be composed is with `Fiber#zip` or `Fiber#zipWith`. These methods combine two fibers into a single fiber that produces the results of both. If either fiber fails, then the composed fiber will fail.

```tut:silent
for {
  fiber1 <- IO.succeed("Hi!").fork
  fiber2 <- IO.succeed("Bye!").fork
  fiber   = fiber1 zip fiber2
  tuple  <- fiber.join
} yield tuple
```

The second way fibers can be composed is with `orElse`. If the first fiber succeeds, the composed fiber will succeed with that result; otherwise, the composed fiber will complete with the exit of the second fiber.

```tut:silent
for {
  fiber1 <- IO.fail("Uh oh!").fork
  fiber2 <- IO.succeed("Hurray!").fork
  fiber   = fiber1 orElse fiber2
  tuple  <- fiber.join
} yield tuple
```

# Parallelism

ZIO provides many operations for performing effects in parallel. These methods are all named with a `Par` suffix that helps you more easily identify opportunities to parallelize your code.

For example, the ordinary `ZIO#zip` method zips two effects together, sequentially. But there is also a `ZIO#zipPar` method, which zips two effects together in parallel.

The following table summarizes some of the sequential operations and their corresponding parallel versions:

| **Description**              | **Sequential**    | **Parallel**         |
| ---------------------------: | :---------------: | :------------------: |
| Zips two effects into one    | `ZIO#zip`         | `ZIO#zipPar`         |
| Zips two effects into one    | `ZIO#zipWith`     | `ZIO#zipWithPar`     |
| Collects from many effects   | `ZIO.collectAll`  | `ZIO.collectAllPar`  |
| Effectfully loop over values | `ZIO.foreach`     | `ZIO.foreachPar`     |
| Reduces many values          | `ZIO.reduceAll`   | `ZIO.reduceAllPar`   |
| Merges many values           | `ZIO.mergeAll`    | `ZIO.mergeAllPar`    |

For all the parallel operations, if one effect fails, then others will be interrupted, to minimize unnecessary computation. If this behavior is not desired, the potentially failing effects can be converted into infallible effects using the `ZIO#either` or `ZIO#option` methods.

# Racing

ZIO allows you to race multiple effects in parallel, returning the first successful result:

```tut:silent
for {
  winner <- IO.succeed("Hello") race IO.succeed("Goodbye")
} yield winner
```

If you want the first success or failure, rather than the first success, then you can use `left.either race right.either`, for any effects `left` and `right`.

# Timeout

ZIO lets you timeout any effect using the `ZIO#timeout` method, which succeeds with an `Option`, where a value of `None` indicates the effect timed out before producing the result.

```tut:silent
import scalaz.zio.duration._

IO.succeed("Hello").timeout(10.seconds)
```

If an effect is timed out, then instead of continuing to execute in the background, it will be interrupted so no resources will be wasted.

# Next Steps

If you are comfortable with basic concurrency, then the next step is to learn about [testing effects](testing_effects.html).