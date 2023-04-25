---
id: catching
title: "Catching"
sidebar_label: "1. Catching"
---

## Catching Failures

If we want to catch and recover from all _typed error_ and effectfully attempt recovery, we can use the `ZIO#catchAll` operator:

```scala
trait ZIO[-R, +E, +A] {
  def catchAll[R1 <: R, E2, A1 >: A](h: E => ZIO[R1, E2, A1]): ZIO[R1, E2, A1]
}
```

We can recover from all errors while reading a file and then fallback to another operation:

```scala mdoc:invisible
import zio._
import java.io.{ FileNotFoundException, IOException }

def readFile(s: String): ZIO[Any, IOException, Array[Byte]] =
  ZIO.attempt(???).refineToOrDie[IOException]
```

```scala mdoc:silent
import zio._

val z: ZIO[Any, IOException, Array[Byte]] =
  readFile("primary.json").catchAll(_ =>
    readFile("backup.json"))
```

In the callback passed to `ZIO#catchAll`, we may return an effect with a different error type (or perhaps `Nothing`), which will be reflected in the type of effect returned by `ZIO#catchAll`.

When using `ZIO#catchAll` operator, the match cases should be exhaustive. Remember our `validate` function again:

```scala mdoc:silent
import zio._

sealed trait AgeValidationException extends Exception
case class NegativeAgeException(age: Int) extends AgeValidationException
case class IllegalAgeException(age: Int)  extends AgeValidationException

def validate(age: Int): ZIO[Any, AgeValidationException, Int] =
  if (age < 0)
    ZIO.fail(NegativeAgeException(age))
  else if (age < 18)
    ZIO.fail(IllegalAgeException(age))
  else ZIO.succeed(age)
```

In the following example, we covered all the cases for the `catchAll` operator:

```scala mdoc:compile-only
import zio._

val result: ZIO[Any, Nothing, Int] =
  validate(20)
  .catchAll {
    case NegativeAgeException(age) =>
      ZIO.debug(s"negative age: $age").as(-1)
    case IllegalAgeException(age) =>
      ZIO.debug(s"illegal age: $age").as(-1)
  }
```

If we forget to catch all cases and the match fails, the original **failure** will be lost and replaced by a `MatchError` **defect**:

```scala mdoc:compile-only
object MainApp extends ZIOAppDefault {
  val result: ZIO[Any, Nothing, Int] =
    validate(15)
      .catchAll {
        case NegativeAgeException(age) =>
          ZIO.debug(s"negative age: $age").as(-1)
//        case IllegalAgeException(age) =>
//          ZIO.debug(s"illegal age: $age").as(-1)
      }

  def run = result
}

// Output:
// timestamp=2022-03-01T06:33:13.454651904Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" scala.MatchError: MainApp$IllegalAgeException (of class MainApp$IllegalAgeException)
//	at MainApp$.$anonfun$result$1(MainApp.scala:6)
//	at scala.util.Either.fold(Either.scala:190)
//	at zio.ZIO.$anonfun$foldZIO$1(ZIO.scala:945)
//  ...
//	at zio.internal.FiberContext.runUntil(FiberContext.scala:538)"
```

Another important note about `ZIO#catchAll` is that this operator only can recover from _failures_. So it can't recover from defects or fiber interruptions.

Let's try what happens if we `catchAll` on a dying effect:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val die: ZIO[Any, String, Nothing] =
    ZIO.dieMessage("Boom!") *> ZIO.fail("Oh uh!")

  def run = die.catchAll(_ => ZIO.unit)
}

// Output:
// timestamp=2022-03-03T11:04:41.209169849Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.RuntimeException: Boom!
// 	at <empty>.MainApp.die(MainApp.scala:6)
//	at <empty>.MainApp.run(MainApp.scala:8)"
```

Also, if we have a fiber interruption, we can't catch that using this operator:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val interruptedEffect: ZIO[Any, String, Nothing] =
    ZIO.interrupt *> ZIO.fail("Oh uh!")

  def run = interruptedEffect.catchAll(_ => ZIO.unit)
}

// Output:
// timestamp=2022-03-03T11:10:15.573588420Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.InterruptedException: Interrupted by thread "zio-fiber-"
//	at <empty>.MainApp.die(MainApp.scala:6)
//	at <empty>.MainApp.run(MainApp.scala:8)"
```

If we want to catch and recover from only some types of exceptions and effectfully attempt recovery, we can use the `ZIO#catchSome` method:

```scala mdoc:compile-only
trait ZIO[-R, +E, +A] {
  def catchSome[R1 <: R, E1 >: E, A1 >: A](pf: PartialFunction[E, ZIO[R1, E1, A1]]): ZIO[R1, E1, A1]
}
```

In the following example, we are only catching failure of type `FileNotFoundException`:

```scala mdoc:compile-only
import zio._

val data: ZIO[Any, IOException, Array[Byte]] =
  readFile("primary.data").catchSome {
    case _ : FileNotFoundException =>
      readFile("backup.data")
  }
```

## Catching Defects

Like catching failures, ZIO has two operators to catch _defects_:

```scala
trait ZIO[-R, +E, +A] {
  def catchAllDefect[R1 <: R, E1 >: E, A1 >: A](h: Throwable => ZIO[R1, E1, A1]): ZIO[R1, E1, A1]

  def catchSomeDefect[R1 <: R, E1 >: E, A1 >: A](pf: PartialFunction[Throwable, ZIO[R1, E1, A1]]): ZIO[R1, E1, A1]
}
```

Let's try the `ZIO#catchAllDefect` operator:

```scala mdoc:compile-only
import zio._

ZIO.dieMessage("Boom!")
  .catchAllDefect {
    case e: RuntimeException if e.getMessage == "Boom!" =>
      ZIO.debug("Boom! defect caught.")
    case _: NumberFormatException =>
      ZIO.debug("NumberFormatException defect caught.")
    case _ =>
      ZIO.debug("Unknown defect caught.")
  }
```

We should note that using these operators, we can only recover from a dying effect, and it cannot recover from a failure or fiber interruption.

A defect is an error that cannot be anticipated in advance, and there is no way to respond to it. Our rule of thumb is to not recover defects since we don't know about them. We let them crash the application. Although, in some cases, we might need to reload a part of the application instead of killing the entire application.

Assume we have written an application that can load plugins at runtime. During the runtime of the plugins, if a defect occurs, we don't want to crash the entire application; rather, we log all defects and then reload the plugin.

## Catching Causes

So far, we have only studied how to catch _failures_ and _defects_. But what about _fiber interruptions_ or how about the specific combination of these errors?

There are two ZIO operators useful for catching causes:

```scala
trait ZIO[-R, +E, +A] {
  def catchAllCause[R1 <: R, E2, A1 >: A](h: Cause[E] => ZIO[R1, E2, A1]): ZIO[R1, E2, A1]

  def catchSomeCause[R1 <: R, E1 >: E, A1 >: A](pf: PartialFunction[Cause[E], ZIO[R1, E1, A1]]): ZIO[R1, E1, A1]
}
```

With the help of the `ZIO#catchAllCause` operator we can catch all errors of an effect and recover from them:

```scala mdoc:compile-only
import zio._

val exceptionalEffect = ZIO.attempt(???)

exceptionalEffect.catchAllCause {
  case Cause.Empty =>
    ZIO.debug("no error caught")
  case Cause.Fail(value, _) =>
    ZIO.debug(s"a failure caught: $value")
  case Cause.Die(value, _) =>
    ZIO.debug(s"a defect caught: $value")
  case Cause.Interrupt(fiberId, _) =>
    ZIO.debug(s"a fiber interruption caught with the fiber id: $fiberId")
  case Cause.Stackless(cause: Cause.Die, _) =>
    ZIO.debug(s"a stackless defect caught: ${cause.value}")
  case Cause.Stackless(cause: Cause[_], _) =>
    ZIO.debug(s"an unknown stackless defect caught: ${cause.squashWith(identity)}")
  case Cause.Then(left, right) =>
    ZIO.debug(s"two consequence causes caught")
  case Cause.Both(left, right) =>
    ZIO.debug(s"two parallel causes caught")
}
```

Additionally, there is a partial version of this operator called `ZIO#catchSomeCause`, which can be used when we don't want to catch all causes, but some of them.

## Catching Traces

The two `ZIO#catchAllTrace` and `ZIO#catchSomeTrace` operators are useful to catch the typed error as well as stack traces of exceptional effects:

```scala
trait ZIO[-R, +E, +A] {
  def catchAllTrace[R1 <: R, E2, A1 >: A](
    h: ((E, Trace)) => ZIO[R1, E2, A1]
  ): ZIO[R1, E2, A1]

  def catchSomeTrace[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[(E, Trace), ZIO[R1, E1, A1]]
  ): ZIO[R1, E1, A1]
}
```

In the below example, let's try to catch a failure on the line number 4:

```scala mdoc:compile-only
import zio._

ZIO
  .fail("Oh uh!")
  .catchAllTrace {
    case ("Oh uh!", trace)
      if trace.toJava
        .map(_.getLineNumber)
        .headOption
        .contains(4) =>
      ZIO.debug("caught a failure on the line number 4")
    case _ =>
      ZIO.debug("caught other failures")
  }
```

## Catching Non-Fatal

We can use the `ZIO#catchNonFatalOrDie` to recover from all non-fatal errors:

```scala
trait ZIO[-R, +E, +A] {
  def catchNonFatalOrDie[R1 <: R, E2, A1 >: A](
    h: E => ZIO[R1, E2, A1]
  )(implicit ev1: CanFail[E], ev2: E <:< Throwable): ZIO[R1, E2, A1]
}
```

In case of occurring any [fatal error](#catching-traces), it will die.

```scala
openFile("data.json").catchNonFatalOrDie(_ => openFile("backup.json"))
```
