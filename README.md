<img src="ZIO.svg" alt="ZIO" align="middle">

The ZIO library provides a general-purpose effect monad and associated abstractions for purely functional Scala applications.

The package strives to deliver on the following design goals:

 - **Principled**. A purely functional interface for effectful code with rigorous, well-defined semantics.
 - **Performant**. A low-level, highly-optimized runtime system that offers performance better than or comparable to other effect monads.
 - **Pragmatic**. The composable, orthogonal primitives necessary to build real world software, including primitives for concurrent and asynchronous programming.

# Why IO?

Effect monads like `IO` are how purely functional programs interact with the real world. Functional programmers use them to build complex, real world software without giving up the equational reasoning, composability, and type safety afforded by purely functional programming.

However, there are many practical reasons to build your programs using `IO`, including all of the following:

 * **Asynchronicity**. Like Scala's own `Future`, `IO` lets you easily write asynchronous code without blocking or callbacks. Compared to `Future`, `IO` has significantly better performance and cleaner, more expressive, and more composable semantics.
 * **Composability**. Purely functional code can't be combined with impure code that has side-effects without the straightforward reasoning properties of functional programming. `IO` lets you wrap up all effects into a purely functional package that lets you build composable real world programs.
 * **Concurrency**. `IO` has all the concurrency features of `Future`, and more, based on a clean fiber concurrency model designed to scale well past the limits of native threads. Unlike other effect monads, `IO`'s concurrency primitives do not leak threads.
 * **Interruptibility**. All concurrent computations can be interrupted, in a way that still guarantees resources are cleaned up safely, allowing you to write aggressively parallel code that doesn't waste valuable resources or bring down production servers.
 * **Resource Safety**. `IO` provides composable resource-safe primitives that ensure resources like threads, sockets, and file handles are not leaked, which allows you to build long-running, robust applications. These applications will not leak resources, even in the presence of errors or interruption.
 * **Immutability**. `IO`, like Scala's immutable collection types, is an immutable data structure. All `IO` methods and functions return new `IO` values. This lets you reason about `IO` values the same way you reason about immutable collections.
 * **Reification**. `IO` reifies programs. In non-functional Scala programming, you cannot pass programs around or store them in data structures, because programs are not values. But `IO` turns your programs into ordinary values, and lets you pass them around and compose them with ease.
 * **Performance**. Although simple, synchronous `IO` programs tend to be slower than the equivalent imperative Scala, `IO` is extremely fast given all the expressive features and strong guarantees it provides. Ordinary imperative Scala could not match this level of expressivity and performance without tedious, error-prone boilerplate that no one would write in real-life.

While functional programmers *must* use `IO` (or something like it) to represent effects, nearly all programmers will find the features of `IO` help them build scalable, performant, concurrent, and leak-free applications faster and with stronger correctness guarantees than legacy techniques allow.

Use `IO` because it's simply not practical to write real-world, correct software without it.

# Getting ZIO

The current snapshot release of ZIO is 0.1, which is cross-built against Scala 2.11.x and 2.12.x.

If you're using SBT, add the following lines to your build file:

```scala
libraryDependencies += "org.scalaz" %% "scalaz-zio" % "0.1-SNAPSHOT"

resolvers += Resolver.sonatypeRepo("snapshots")
```
For Maven and other build tools, you can visit <https://search.maven.org> and search for Scalaz.

# Introduction

A value of type `IO[E, A]` describes an effect that may fail with an `E`, run forever, or produce a single `A`.

`IO` values are immutable, and all `IO` functions produce new `IO` values, enabling `IO` to be reasoned about and used like any ordinary Scala immutable data structure.

`IO` values do not actually _do_ anything. However, they may be interpreted by the `IO` runtime system into effectful interactions with the external world. Ideally, this occurs at a single time, in your application's `main` function (`App` provides this functionality automatically).

# Usage

## Main

Your main function can extend `App`, which provides a complete runtime system and allows your entire program to be purely functional.

```scala
import scalaz.zio.{App, IO}
import scalaz.zio.console._

import java.io.IOException

object MyApp extends App {

  def run(args: List[String]): IO[Nothing, ExitStatus] =
    myAppLogic.attempt.map(_.fold(_ => 1, _ => 0)).map(ExitStatus.ExitNow(_))

  def myAppLogic: IO[IOException, Unit] =
    for {
      _ <- putStrLn("Hello! What is your name?")
      n <- getStrLn
      _ <- putStrLn("Hello, " + n + ", good to meet you!")
    } yield ()
}
```

## Pure Values

You can lift pure values into `IO` with `IO.point`:

```scala
val stringIO: IO[Nothing, String] = IO.point("Hello World")
```

The constructor uses non-strict evaluation, so the parameter will not be evaluated until when and if the `IO` action is executed at runtime, which is useful if the construction is costly and the value may never be needed.

Alternately, you can use the `IO.now` constructor to perform strict evaluation of the value:

```scala
val stringIO: IO[Nothing, String] = IO.now("Hello World")
```

You should never use either constructor for importing impure code into `IO`. The result of doing so is undefined.

## Infallible IO

`IO` values of type `IO[Nothing, A]` (where the error type is `Nothing`) are considered _infallible_,
because the `Nothing` type is _uninhabitable_, i.e. there can be no actual values of type `Nothing`. Values of this type may produce an `A`, but will never fail with an `E`.

## Unproductive IO

`IO` values of type `IO[E, Nothing]` (where the value type is `Nothing`) are considered _unproductive_,
because the `Nothing` type is _uninhabitable_, i.e. there can be no actual values of type `Nothing`. Values of this type may fail with an `E`, but will never produce a value.

## Impure Code

You can use the `sync` method of `IO` to import effectful synchronous code into your purely functional program:

```scala
val nanoTime: IO[Nothing, Long] = IO.sync(System.nanoTime())
```

If you are importing effectful code that may throw exceptions, you can use the `syncException` method of `IO`:

```scala
def readFile(name: String): IO[Exception, ByteArray] =
  IO.syncException(FileUtils.readBytes(name))
```

Alternately, `syncThrowable` will catch all `Throwable`, not just exceptions. Finally, the `syncCatch` method is more general, allowing you to catch and optionally translate any type of `Throwable` into a custom error type:

```scala
def readFile(name: String): IO[String, ByteArray] =
  IO.syncCatch(FileUtils.readBytes(name)) {
    case e : IOException => "Could not read file"
  }
```

You can use the `async` method of `IO` to import effectful asynchronous code into your purely functional program:

```scala
def makeRequest(req: Request): IO[HttpException, Response] =
  IO.async(cb => Http.req(req, cb))
```

In this example, it's assumed the `Http.req` method will invoke the specified callback when the result has been asynchronously computed.

## Mapping

You can change an `IO[E, A]` to an `IO[E, B]` by calling the `map` method with a function `A => B`. This lets you transform values produced by actions into other values.

```scala
val answer = IO.point(21).map(_ * 2)
```

You can transform an `IO[E, A]` into an `IO[E2, A]` by calling the `leftMap` method with a function `E => E2`:

```scala
val response: IO[AppError, Response] =
  makeRequest(r).leftMap(AppError(_))
```

## Chaining

You can execute two actions in sequence with the `flatMap` method. The second action may depend on the value produced by the first action.

```scala
val contacts: IO[IOException, IList[Contact]] =
  readFile("contacts.csv").flatMap((file: ByteArray) =>
    parseCsv(file).map((csv: IList[CsvRow]) =>
      csv.map(rowToContact)
    )
  )
```

You can use Scala's `for` comprehension syntax to make this type of code more compact:

```scala
val contacts: IO[IOException, IList[Contact]] =
  for {
    file <- readFile("contacts.csv")
    csv  <- parseCsv(file)
  } yield csv.map(rowToContact)
```

## Failure

You can create `IO` actions that describe failure with `IO.fail`:

```scala
val failure: IO[String, Unit] = IO.fail("Oh noes!")
```

Like all `IO` values, these are immutable values and do not actually throw any exceptions; they merely describe failure as a first-class value.

You can surface failures with `attempt`, which takes an `IO[E, A]` and produces an `IO[E2, Either[E, A]]`. The choice of `E2` is unconstrained, because the resulting computation cannot fail with any error.

You can use `Nothing` to describe computations that cannot fail:

```scala
val file: IO[Nothing, Data] = readData("data.json").attempt[Nothing].map {
  case Left(_)     => NoData
  case Right(data) => data
}
```

You can submerge failures with `IO.absolve`, which is the opposite of `attempt` and turns an `IO[E, Either[E, A]]` into an `IO[E, A]`:

```scala
def sqrt(io: IO[Nothing, Double]): IO[NonNegError, Double] =
  IO.absolve(
    io[NonNegError].map(value =>
      if (value < 0.0) Left(NonNegError)
      else Right(Math.sqrt(value))
    )
  )
```

If you want to catch and recover from all types of errors and effectfully attempt recovery, you can use the `catchAll` method:

```scala
openFile("primary.json").catchAll(_ => openFile("backup.json"))
```

If you want to catch and recover from only some types of exceptions and effectfully attempt recovery, you can use the `catchSome` method:

```scala
openFile("primary.json").catchSome {
  case FileNotFoundException(_) => openFile("backup.json")
}
```

You can execute one action, or, if it fails, execute another action, with the `orElse` combinator:

```scala
val file = openFile("primary.json").orElse(openFile("backup.json"))
```

If you want more control on the next action and better performance you can use the primitive which all the previous operations are based on, it's called `redeem` and it can be seen as the combination of `flatMap` and `catchAll`. It is useful if you find yourself using combinations of `attempt` or `catchAll` with `flatMap`, using `redeem` you can achieve the same and avoid the intermediate `Either` allocation and the subsequent call to `flatMap`.

```scala
val file: IO[Nothing, Content] =
  readUrls("urls.json").redeem[Nothing](e => IO.point(NoContent(cause = e)))(fetchContent)
```

### Retry

There are a number of useful combinators for repeating actions until failure or success:

 * `IO.forever` &mdash; Repeats the action until the first failure.
 * `IO.retry` &mdash; Repeats the action until the first success.
 * `IO.retryN(n)` &mdash; Repeats the action until the first success, for up to the specified number of times (`n`).
 * `IO.retryFor(d)` &mdash; Repeats the action until the first success, for up to the specified amount of time (`d`).

## Brackets

Brackets are a built-in primitive that let you safely acquire and release resources.

Brackets are used for a similar purpose as try/catch/finally, only brackets work with synchronous and asynchronous actions, work seamlessly with fiber interruption, and are built on a different error model that ensures no errors are ever swallowed.

Brackets consist of an *acquire* action, a *utilize* action (which uses the acquired resource), and a *release* action.

The release action is guaranteed to be executed by the runtime system, even if the utilize action throws an exception or the executing fiber is interrupted.

```scala
openFile("data.json").bracket(closeFile(_)) { file =>
  for {
    data    <- decodeData(file)
    grouped <- groupData(data)
  } yield grouped
}
```

Brackets have compositional semantics, so if a bracket is nested inside another bracket, and the outer bracket acquires a resource, then the outer bracket's release will always be called, even if, for example, the inner bracket's release fails.

A helper method called `ensuring` provides a simpler analogue of `finally`:

```scala
val composite = action1.ensuring(cleanupAction)
```

## Fibers

To perform an action without blocking the current process, you can use fibers, which are a lightweight mechanism for concurrency.

You can `fork` any `IO[E, A]` to immediately yield an `IO[Nothing, Fiber[E, A]]`. The provided `Fiber` can be used to `join` the fiber, which will resume on production of the fiber's value, or to `interrupt` the fiber with some exception.

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

```scala
def fib(n: Int): IO[Nothing, Int] =
  if (n <= 1) IO.point(1)
  else for {
    fiber1 <- fib(n - 2).fork
    fiber2 <- fib(n - 1).fork
    v2     <- fiber2.join
    v1     <- fiber1.join
  } yield v1 + v2
```

Interrupting a fiber returns an action that resumes when the fiber has completed or has been interrupted and all its finalizers have been run. These precise semantics allow construction of programs that do not leak resources.

A more powerful variant of `fork`, called `fork0`, allows specification of supervisor that will be passed any non-recoverable errors from the forked fiber, including all such errors that occur in finalizers. If this supervisor is not specified, then the supervisor of the parent fiber will be used, recursively, up to the root handler, which can be specified in `RTS` (the default supervisor merely prints the stack trace).

## Error Model

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

### Parallelism

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

# Performance

`scalaz.zio` has excellent performance, featuring a hand-optimized, low-level interpreter that achieves zero allocations for right-associated binds, and minimal allocations for left-associated binds.

The `benchmarks` project may be used to compare `IO` with other effect monads, including `Future` (which is not an effect monad but is included for reference), Monix `Task`, and Cats `IO`.

As of the time of this writing, `IO` is significantly faster than or at least comparable to all other purely functional solutions.

# Thread Shifting - JVM

By default, fibers make no guarantees as to which thread they execute on. They may shift between threads, especially as they execute for long periods of time.

Fibers only ever shift onto the thread pool of the runtime system, which means that by default, fibers running for a sufficiently long time will always return to the runtime system's thread pool, even when their (asynchronous) resumptions were initiated from other threads.

For performance reasons, fibers will attempt to execute on the same thread for a (configurable) minimum period, before yielding to other fibers. Fibers that resume from asynchronous callbacks will resume on the initiating thread, and continue for some time before yielding and resuming on the runtime thread pool.

These defaults help guarantee stack safety and cooperative multitasking. They can be changed in `RTS` if automatic thread shifting is not desired.

# Legal

Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
