---
layout: docs
section: usage
title:  "Fibers"
---

# Fibers

To perform an action without blocking the current process, you can use fibers, which are a lightweight mechanism for concurrency.

You can `fork` any `IO[E, A]` to immediately yield an `IO[Nothing, Fiber[E, A]]`. The provided `Fiber` can be used to `join` the fiber, which will resume on production of the fiber's value, or to `interrupt` the fiber with some exception.

```tut:silent
import scalaz.zio._
```

```scala
val analyzed =
  for {
    fiber1   <- analyzeData(data).fork  // IO[E, Analysis]
    fiber2   <- validateData(data).fork // IO[E, Boolean]
    ... // Do other stuff
    valid    <- fiber2.join
    _        <- if (!valid) fiber1.interrupt(DataValidationError(data))
                else IO.unit
    analyzed <- fiber1.join
  } yield analyzed
```

On the JVM, fibers will use threads, but will not consume *unlimited* threads. Instead, fibers yield cooperatively during periods of high-contention.

```tut:silent
def fib(n: Int): IO[Nothing, Int] =
  if (n <= 1) {
    IO.point(1)
  } else {
    for {
      fiber1 <- fib(n - 2).fork
      fiber2 <- fib(n - 1).fork
      v2     <- fiber2.join
      v1     <- fiber1.join
    } yield v1 + v2
  }
```

Interrupting a fiber returns an action that resumes when the fiber has completed or has been interrupted and all its finalizers have been run. These precise semantics allow construction of programs that do not leak resources.

A more powerful variant of `fork`, called `fork0`, allows specification of supervisor that will be passed any non-recoverable errors from the forked fiber, including all such errors that occur in finalizers. If this supervisor is not specified, then the supervisor of the parent fiber will be used, recursively, up to the root handler, which can be specified in `RTS` (the default supervisor merely prints the stack trace).

# Error Model

The `IO` error model is simple, consistent, permits both typed errors and termination, and does not violate any laws in the `Functor` hierarchy.

An `IO[E, A]` value may only raise errors of type `E`. These errors are recoverable, and may be caught the `attempt` method. The `attempt` method yields a value that cannot possibly fail with any error `E`. This rigorous guarantee can be reflected at compile-time by choosing a new error type such as `Nothing`, which is possible because `attempt` is polymorphic in the error type of the returned value.

Separately from errors of type `E`, a fiber may be terminated for the following reasons:

 * The fiber self-terminated or was interrupted by another fiber. The "main" fiber cannot be interrupted because it was not forked from any other fiber.
 * The fiber failed to handle some error of type `E`. This can happen only when an `IO.fail` is not handled. For values of type `IO[Nothing, A]`, this type of failure is impossible.
 * The fiber has a defect that leads to a non-recoverable error. There are only two ways this can happen:
     1. A partial function is passed to a higher-order function such as `map` or `flatMap`. For example, `io.map(_ => throw e)`, or `io.flatMap(a => throw e)`. The solution to this problem is to not to pass impure functions to purely functional libraries like Scalaz, because doing so leads to violations of laws and destruction of equational reasoning.
     2. Error-throwing code was embedded into some value via `IO.point`, `IO.sync`, etc. For importing partial effects into `IO`, the proper solution is to use a method such as `syncException`, which safely translates exceptions into values.

When a fiber is terminated, the reason for the termination, expressed as a `Throwable`, is passed to the fiber's supervisor, which may choose to log, print the stack trace, restart the fiber, or perform some other action appropriate to the context.

A fiber cannot stop its own termination. However, all finalizers will be run during termination, even when some finalizers throw non-recoverable errors. Errors thrown by finalizers are passed to the fiber's supervisor.

There are no circumstances in which any errors will be "lost", which makes the `IO` error model more diagnostic-friendly than the `try`/`catch`/`finally` construct that is baked into both Scala and Java, which can easily lose errors.

# Parallelism

To execute actions in parallel, the `par` method can be used:

```scala
def bigCompute(m1: Matrix, m2: Matrix, v: Matrix): IO[Nothing, Matrix] =
  for {
    t <- computeInverse(m1).par(computeInverse(m2))
    val (i1, i2) = t
    r <- applyMatrices(i1, i2, v)
  } yield r
```

The `par` combinator has resource-safe semantics. If one computation fails, the other computation will be interrupted, to prevent wasting resources.

### Racing

Two `IO` actions can be *raced*, which means they will be executed in parallel, and the value of the first action that completes successfully will be returned.

```scala
action1.race(action2)
```

The `race` combinator is resource-safe, which means that if one of the two actions returns a value, the other one will be interrupted, to prevent wasting resources.

The `race` and even `par` combinators are a specialization of a much-more powerful combinator called `raceWith`, which allows executing user-defined logic when the first of two actions succeeds.
