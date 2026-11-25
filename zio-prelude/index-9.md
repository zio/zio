# ZPure

> `ZPure` is a data type for pure computations that need more than one of the following capabilities: errors, an environment, state, and a log. It lets us describe such a computation in one type, instead of stacking several monad transformers.

The type has six parameters. Its header in ZIO Prelude is:

```scala
sealed abstract class ZPure[+W, -S1, +S2, -R, +E, +A]
```

Reading the parameters from left to right:

- `W` is the type of the log entries the computation can produce.
- `S1` is the type of the state the computation starts with.
- `S2` is the type of the state the computation ends with.
- `R` is the type of the environment the computation requires.
- `E` is the type of error the computation can fail with.
- `A` is the type of value the computation produces on success.

A `ZPure` value is a description of a computation, so nothing runs until we call one of the `run` operators. The guides in this section cover each capability in turn. [Basics](./basics.md) introduces constructing and combining computations, [Errors and Context](./errors-and-context.md) covers failure and the environment, [State and Logging](./state-and-logging.md) covers the state and the log, and [Running and Converting](./running-and-converting.md) covers how to execute a computation and how to turn it into a `ZIO` value.

## Why One Data Type

Each capability could be modeled by its own data type. `Either` models failure, `Reader` models the environment, `State` models state, and `Writer` models logging. This works when we need one capability, but a computation that needs two of them, such as state and failure, does not fit into one of these types.

The traditional answer is a monad transformer stack, such as an `EitherT` on top of a `StateT`. Transformer stacks have three problems. Each layer is a separate data structure, so evaluating one step means unpacking and rebuilding every layer. Their type inference is weak, so the order of the layers is easy to get wrong. And each layer only knows about itself, so an operator that needs two capabilities at once is hard to write.

`ZPure` puts all four capabilities into one data type. It evaluates computations with an explicit stack instead of the call stack, so long chains of steps do not overflow it, and it lets operators see every capability at once.

## A First Example

A small computation shows the capabilities working together. The following computation reads the current counter from the state, writes a log entry that records it, and stores the incremented counter. The state type does not change, so `S1` and `S2` are both `Int`:

```scala
import zio.prelude._
import zio.prelude.fx.ZPure

val tick: ZPure[String, Int, Int, Any, Nothing, Int] =
  for {
    n <- ZPure.get[Int]
    _ <- ZPure.log[Int, String](s"counter was $n")
    _ <- ZPure.set(n + 1)
  } yield n
```

Next, we run the computation with an initial state of `1`. `ZPure#runAll` returns the log and either the error or the final state and value:

```scala
tick.runAll(1)
// res1: (zio.Chunk[String], Either[Nothing, (Int, Int)]) = (
//   IndexedSeq("counter was 1"),
//   Right(value = (2, 1))
// )
```

## Type Aliases

Most computations use only some of the capabilities. The `zio.prelude` package defines aliases that fix the unused parameters, so the type of a computation says what it needs: `State[S, A]` for state, `Reader[R, A]` for an environment, `Writer[W, A]` for a log, and `EState`, `EReader` and `EWriter` for the same with an error type `E`. Each alias also has a value of the same name that is the `ZPure` companion object, so `State.get` and `ZPure.get` are the same operator. The `tick` computation above fits the shape `ZPure[String, Int, Int, Any, Nothing, Int]`, which is a state and a log without an environment or an error.

## When to Use ZPure

Use `ZPure` for logic that is pure: it reads its inputs, updates some state, records a log, and may fail, but it does not perform input or output. `ZPure` has no operators for input or output and no concurrency. Operators such as `attempt` can still run arbitrary code, so keeping a computation pure is up to us.

If a computation needs to perform input or output, use `ZIO` instead. A `ZPure` computation can be converted to `ZIO` when it is time to run it in an effectful program, as described in [Running and Converting](./running-and-converting.md).
