# Basics of ZPure

> This guide covers how to create `ZPure` computations, how to combine them, and how to use the type aliases that ZIO Prelude defines for the common cases. For the overview of the data type and its six parameters, see [ZPure](./index.md).

Throughout this guide we import the package and the `ZPure` companion object:

```scala
import zio.prelude._
import zio.prelude.fx.ZPure
```

## Creating a Computation

The `ZPure` data type exposes a similar API to `ZIO`, without the operators for input, output, and concurrency. If we already know `ZIO`, the operators here will look familiar.

We create a computation that succeeds with an existing value using `ZPure.succeed`:

```scala
val one: ZPure[Nothing, Unit, Unit, Any, Nothing, Int] =
  ZPure.succeed(1)
```

The log type `Nothing` shows that this computation logs nothing. The state types are `Unit` because the computation does not use state. The environment type is `Any` because it does not require an environment, and the error type is `Nothing` because it cannot fail.

## Transforming and Combining Computations

We transform the value a computation produces with `map`:

```scala
val two: ZPure[Nothing, Unit, Unit, Any, Nothing, Int] =
  one.map(_ + 1)
```

We combine the values of two computations with `zipWith`, which passes the values of both to a function:

```scala
val three: ZPure[Nothing, Unit, Unit, Any, Nothing, Int] =
  one.zipWith(two)(_ + _)
```

We chain computations with `flatMap`, and Scala's `for` comprehension is a convenient way to write a sequence of them:

```scala
val six: ZPure[Nothing, Unit, Unit, Any, Nothing, Int] =
  for {
    x <- one
    y <- two
    z <- three
  } yield x + y + z
```

Each step in the `for` comprehension runs after the previous step, passing along any state, log entries, and errors. The comprehension above does not need any of those capabilities, so its type parameters stay trivial.

## Using the Type Aliases

When we only use one or two capabilities, the full six-parameter type is noisy. ZIO Prelude defines type aliases for the common cases in the `zio.prelude` package:

- `State[S, A]` is a computation that updates a state of type `S` and produces an `A`. It has no log, no environment, and no errors.
- `Reader[R, A]` is a computation that requires an environment of type `R` and produces an `A`. It has no log, no state, and no errors.
- `Writer[W, A]` is a computation that produces a log of type `W` and an `A`. It has no state, no environment, and no errors.

Each of these has an `E` variant (`EState`, `EReader`, and `EWriter`) that adds an error type `E`. Using these aliases makes the type signatures shorter, and they often help type inference.

A `State` computation takes a state and returns an updated state together with a value. The `ZPure.modify` operator here returns the old state as its value and increments the state:

```scala
val increment: State[Int, Int] =
  ZPure.modify[Int, Int, Int](s => (s, s + 1))
```

Next, we run `increment` from the state `10`:

```scala
increment.run(10)
// res1: (Int, Int) = (11, 10)
```

The result is `(11, 10)`: the new state is `11`, and the value is the old state, `10`.

A `Reader` computation reads from its environment. Here, `ZPure.serviceWith` reads a `String` from the environment and builds a greeting from it:

```scala
val greeting: Reader[String, String] =
  ZPure.serviceWith[String](name => s"Hello, $name")
```

We provide the environment and run the computation to get the greeting, which is covered in [Errors and Context](./errors-and-context.md) and [Running and Converting](./running-and-converting.md).
