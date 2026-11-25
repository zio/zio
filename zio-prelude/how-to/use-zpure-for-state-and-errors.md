# How to Use ZPure for State and Errors

> Write a stack calculator as a pure ZPure computation with state, errors and a log, and run it with different inputs.

In this guide, we build a calculator for expressions in postfix notation, such as `2 3 + 4 *`, as a pure computation. By the end, you will have an `evaluate` function whose type shows that it uses a stack as state, can fail with a typed error, and writes a log, and that you can run any number of times from any starting stack.

We build it incrementally: stack operations first, then failure handling, then a log, and finally running and recovering.

## The Problem

A postfix calculator needs a stack that changes with every token, and it can fail: an operator can find too few operands, and a token can be unknown. Written with mutable state and exceptions, the stack and the failure modes are invisible in the function signature, and the function cannot be run twice without resetting the state:

```scala
import scala.collection.mutable

def evaluate(expression: String): Int = {
  val stack = mutable.Stack.empty[Int]

  expression.split(" ").foreach {
    case "+"   => stack.push(stack.pop() + stack.pop())
    case "*"   => stack.push(stack.pop() * stack.pop())
    case token => stack.push(token.toInt)
  }

  stack.pop()
}
```

An expression with a missing operand throws an unspecific exception, an unknown token throws `NumberFormatException`, and a test must create fresh state for every case. Nothing in the signature warns the caller.

In this guide, we make the state, the errors and the log part of the type with `ZPure`.

## Prerequisites

We need the `zio-prelude` dependency:

```scala
libraryDependencies += "dev.zio" %% "zio-prelude" % "1.0.0-RC49"
```

A `ZPure[W, S1, S2, R, E, A]` describes a computation that starts with a state `S1`, ends with a state `S2`, logs entries of type `W`, reads an environment `R`, and either fails with an `E` or succeeds with an `A`. Nothing runs until we call a run operator. We import the package and `ZPure`, and define the stack, the error type and a type alias, so signatures stay short:

```scala
import zio.prelude._
import zio.prelude.fx.ZPure

type Stack = List[Int]

sealed trait CalcError

object CalcError {
  case object StackUnderflow                   extends CalcError
  case object DivisionByZero                   extends CalcError
  final case class UnknownToken(token: String) extends CalcError
}

import CalcError._

type Calc[+A] = ZPure[String, Stack, Stack, Any, CalcError, A]
```

The alias `Calc[A]` fixes a `String` log, a `Stack` that is both the input and output state, no environment, and `CalcError` as the error. Using one alias keeps the rest of the guide readable.

## Changing the State

`ZPure.update` replaces the state with a function of the old state, and `ZPure.log` appends an entry to the log. Pushing a number does both, and the `*>` operator runs them one after the other:

```scala
def push(n: Int): Calc[Unit] =
  ZPure.update[Stack, Stack](n :: _) *> ZPure.log[Stack, String](s"push $n")
```

Both `ZPure.update` and `ZPure.log` take the state type as a type parameter, because they are generic in the state. That explains the explicit type arguments.

## Failing with a Typed Error

Popping reads the state with `ZPure.get`, and decides what to do from its shape. An empty stack fails with `ZPure.fail`, and a non-empty stack stores the rest with `ZPure.set` and returns the top with `ZPure#as`:

```scala
val pop: Calc[Int] =
  ZPure.get[Stack].flatMap {
    case top :: rest => ZPure.set(rest).as(top)
    case Nil         => ZPure.fail(StackUnderflow)
  }
```

The failure is a value of type `CalcError`, so the caller can pattern match on it instead of catching an exception. Computations compose in a `for` comprehension. A binary operator pops the right operand and then the left one, computes a result, and pushes it. The `compute` function can itself fail, which is how division reports a zero divisor:

```scala
def binary(name: String)(compute: (Int, Int) => Calc[Int]): Calc[Unit] =
  for {
    right  <- pop
    left   <- pop
    result <- compute(left, right)
    _      <- push(result)
    _      <- ZPure.log[Stack, String](s"applied $name")
  } yield ()
```

## Evaluating an Expression

A token is an operator or a number. We match on it, build the matching computation, and fail with `UnknownToken` when the text is neither. To evaluate a whole expression, `ZPure.foreachDiscard` runs a computation for each token in order, threading the state from one to the next, and a final `pop` returns the answer:

```scala
def step(token: String): Calc[Unit] =
  token match {
    case "+" => binary("+")((left, right) => ZPure.succeed(left + right))
    case "*" => binary("*")((left, right) => ZPure.succeed(left * right))
    case "/" =>
      binary("/")((left, right) =>
        if (right == 0) ZPure.fail(DivisionByZero) else ZPure.succeed(left / right)
      )
    case other =>
      other.toIntOption match {
        case Some(n) => push(n)
        case None    => ZPure.fail(UnknownToken(other))
      }
  }

def evaluate(expression: String): Calc[Int] =
  ZPure.foreachDiscard(expression.split(" ").toList)(step) *> pop
```

The `evaluate` function has not computed anything yet. It returns a description whose type shows the state, the log and the error.

## Running with a Starting State

`ZPure#runAll` takes the initial state and returns the log together with either the error, or the final state and the value. We start from an empty stack:

```scala
evaluate("2 3 + 4 *").runAll(Nil)
// res2: (zio.Chunk[String], Either[CalcError, (Stack, Int)]) = (
//   IndexedSeq(
//     "push 2",
//     "push 3",
//     "push 5",
//     "applied +",
//     "push 4",
//     "push 20",
//     "applied *"
//   ),
//   Right(value = (List(), 20))
// )
```

The log has an entry for every push and every operator, the final stack is empty, and the value is `20`. A failure keeps the log entries written before it, which shows how far the computation got:

```scala
evaluate("2 +").runAll(Nil)
// res3: (zio.Chunk[String], Either[CalcError, (Stack, Int)]) = (
//   IndexedSeq("push 2"),
//   Left(value = StackUnderflow)
// )

evaluate("2 0 /").runAll(Nil)
// res4: (zio.Chunk[String], Either[CalcError, (Stack, Int)]) = (
//   IndexedSeq("push 2", "push 0"),
//   Left(value = DivisionByZero)
// )

evaluate("2 x").runAll(Nil)
// res5: (zio.Chunk[String], Either[CalcError, (Stack, Int)]) = (
//   IndexedSeq("push 2"),
//   Left(value = UnknownToken(token = "x"))
// )
```

Because `evaluate` is a value, running it again from another starting state needs no cleanup. We start from a stack that already holds `10`, and the leftover value stays in the final stack:

```scala
evaluate("2 3 +").runAll(List(10))
// res6: (zio.Chunk[String], Either[CalcError, (Stack, Int)]) = (
//   IndexedSeq("push 2", "push 3", "push 5", "applied +"),
//   Right(value = (List(10), 5))
// )
```

## Recovering from Errors

`ZPure#catchAll` handles any error by running another computation, and the error type becomes `Nothing` when every error is handled. We fall back to `0` for any failing expression:

```scala
evaluate("2 +").catchAll(_ => ZPure.succeed[Stack, Int](0)).runResult(Nil)
// res7: Int = 0
```

Other run operators return less. `ZPure#runResult` returns only the value, which needs an error type of `Nothing` as above. After `ZPure#provideState` supplies the starting state, `ZPure#runEither` returns an `Either` and `ZPure#runValidation` returns the result with the log, which keeps the log of a failed run:

```scala
evaluate("2 3 +").provideState(Nil).runEither
// res8: Either[CalcError, Int] = Right(value = 5)

evaluate("2 +").provideState(Nil).runValidation
// res9: ZValidation[String, CalcError, Int] = Failure(
//   log = IndexedSeq("push 2"),
//   errors = NonEmptyChunk(StackUnderflow)
// )
```

## Going Further

- Read [Basics of ZPure](../zpure/basics.md), [Errors and Context in ZPure](../zpure/errors-and-context.md) and [State and Logging in ZPure](../zpure/state-and-logging.md) for the individual capabilities.
- See [Running and Converting](../zpure/running-and-converting.md) for converting a computation to a `ZIO` effect.
- To follow the same idea inside a larger program, see the stock reservation step of [Tutorial: Validate an Order](../tutorial/validate-an-order.md).
