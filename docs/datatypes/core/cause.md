---
id: cause
title: "Cause"
---

`Cause[E]` is a description of a full story of failure, which is included in an [Exit.Failure](exit.md). Many times in ZIO something can fail for a value of type `E`, but there are other ways things can fail too.

The `ZIO[R, E, A]` effect is polymorphic in values of type `E` and we can work with any error type that we want, but there is a lot of information that is not inside an arbitrary `E` value. So as a result ZIO needs somewhere to store things like **unexpected error or defects**, **stack and execution traces**, **cause of fiber interruptions**, and so forth.

ZIO uses a data structure from functional programming called a _semiring_. The `Cause` is a semiring. It allows us to take a base type `E` that represents the error type and then capture the sequential and parallel composition of errors in a fully lossless fashion.

It is the underlying data type for the ZIO data type, and we do not usually work directly with it.

Even though it is not a data type that we deal with very often, anytime we want, we can access the `Cause` data structure, which gives us total access to all parallel and sequential errors in our codebase. 

The following snippet shows how the `Cause` is designed as a semiring data structure:

```scala
sealed abstract class Cause[+E] extends Product with Serializable { self =>
  import Cause._
  def trace: ZTrace = ???

  final def ++[E1 >: E](that: Cause[E1]): Cause[E1] = Then(self, that)
  final def &&[E1 >: E](that: Cause[E1]): Cause[E1] = Both(self, that)
}

object Cause extends Serializable {
  case object Empty extends Cause[Nothing]
  final case class Fail[+E](value: E, override val trace: ZTrace) extends Cause[E]
  final case class Die(value: Throwable, override val trace: ZTrace) extends Cause[Nothing]
  final case class Interrupt(fiberId: FiberId, override val trace: ZTrace) extends Cause[Nothing]
  final case class Stackless[+E](cause: Cause[E], stackless: Boolean) extends Cause[E]
  final case class Then[+E](left: Cause[E], right: Cause[E]) extends Cause[E]
  final case class Both[+E](left: Cause[E], right: Cause[E]) extends Cause[E]
}
```

Using the `Cause` data structure described above, ZIO can capture all errors inside the application.

## Cause Variations

The `Cause` has the following constructors in its companion object: 

```scala
object Cause extends Serializable {
  val empty: Cause[Nothing] = Empty
  def die(defect: Throwable, trace: ZTrace = ZTrace.none): Cause[Nothing] = Die(defect, trace)
  def fail[E](error: E, trace: ZTrace = ZTrace.none): Cause[E] = Fail(error, trace)
  def interrupt(fiberId: FiberId, trace: ZTrace = ZTrace.none): Cause[Nothing] = Interrupt(fiberId, trace)
  def stack[E](cause: Cause[E]): Cause[E] = Stackless(cause, false)
  def stackless[E](cause: Cause[E]): Cause[E] = Stackless(cause, true)
}
```

In this section, we will describe each of these causes. We will see how they can be created manually or how they will be automatically generated as the underlying error management data type of a ZIO application.

1. `Cause.empty`— It creates an empty cause which indicates the lack of errors. Using `ZIO.failCause` we can create a ZIO effect that has an empty cause:

```scala mdoc:compile-only
import zio._

ZIO.failCause(Cause.empty).cause.debug
// Empty
```

Also, we can use the `ZIO#cause` to uncover the underlying cause of an effect. For example, we know that the `ZIO.succeed(5)` has no errors. So, let's check that:

```
ZIO.succeed(5).cause.debug
// Empty
```

2. `Fail[+E](value: E)`— contains the cause of expected failure of type `E`.

3. `Die(value: Throwable)` contains the cause of a defect or in other words, an unexpected failure of type `Throwable`. If we have a bug in our code and something throws an unexpected exception, that information would be described inside a `Die`.

4. `Interrupt(fiberId)` contains information of the fiber id that causes fiber interruption.

5. `Traced(cause, trace)` stores stack traces and execution traces.

6. `Meta(cause, data)`

7. `Both(left, right)` & `Then(left, right)` store a composition of two parallel and sequential causes, respectively. Sometimes fibers can fail for more than one reason. If we are doing two things at once and both of them fail then we actually have two errors. Examples:
    + If we perform ZIO's analog of try-finally (e.g. ZIO#ensuring), and both of `try` and `finally` blocks fail, then their causes are encoded with `Then`.
    + If we run two parallel fibers with `zipPar` and both of them fail, then their causes are encoded with `Both`.

Let's try to create some of these causes:

```scala mdoc:silent
import zio._
for {
  failExit <- ZIO.fail("Oh! Error!").exit
  dieExit  <- ZIO.succeed(5 / 0).exit
  thenExit <- ZIO.fail("first").ensuring(ZIO.die(throw new Exception("second"))).exit
  bothExit <- ZIO.fail("first").zipPar(ZIO.die(throw new Exception("second"))).exit
  fiber    <- ZIO.sleep(1.second).fork
  _        <- fiber.interrupt
  interruptionExit <- fiber.join.exit
} yield ()
```

## Lossless Error Model
ZIO is very strict about preserving the full information related to a failure. ZIO captures all types of errors into the `Cause` data type. So its error model is lossless. It doesn't throw away any information related to the failure result. So we can figure out exactly what happened during the operation of our effects.

