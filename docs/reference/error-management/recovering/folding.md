---
id: folding
title: "Folding"
sidebar_label: "3. Folding"
---

Scala's `Option` and `Either` data types have `fold`, which let us handle both failure and success at the same time. In a similar fashion, `ZIO` effects also have several methods that allow us to handle both failure and success.

## `ZIO#fold`/`ZIO#foldZIO`

The first fold method, `ZIO#fold`, lets us non-effectfully handle both failure and success, by supplying a non-effectful handler for each case. The second fold method, `ZIO#foldZIO`, lets us effectfully handle both failure and success, by supplying an effectful (but still pure) handler for each case:

```scala
trait ZIO[-R, +E, +A] {
  def fold[B](
    failure: E => B,
    success: A => B
  ): ZIO[R, Nothing, B]

  def foldZIO[R1 <: R, E2, B](
    failure: E => ZIO[R1, E2, B],
    success: A => ZIO[R1, E2, B]
  ): ZIO[R1, E2, B]
}
```

Let's try an example:

```scala mdoc:invisible
import zio._
import java.io.{ FileNotFoundException, IOException }

def readFile(s: String): ZIO[Any, IOException, Array[Byte]] =
  ZIO.attempt(???).refineToOrDie[IOException]
```

```scala mdoc:silent
import zio._

lazy val DefaultData: Array[Byte] = Array(0, 0)

val primaryOrDefaultData: UIO[Array[Byte]] =
  readFile("primary.data").fold(_ => DefaultData, data => data)
```

We can ignore any failure and success values:

```scala mdoc:compile-only
import zio._

val result: ZIO[Any, Nothing, Unit] =
  ZIO
    .fail("Uh oh!")         // ZIO[Any, String, Int]
    .as(5)                  // ZIO[Any, String, Int]
    .fold(_ => (), _ => ()) // ZIO[Any, Nothing, Unit]
```

It is equivalent to use the `ZIO#ignore` operator instead:

```scala mdoc:compile-only
import zio._

val result: ZIO[Any, Nothing, Unit] = ZIO.fail("Uh oh!").as(5).ignore
```

Now let's try the effectful version of the fold operation. In this example, in case of failure on reading from the primary file, we will fallback to another effectful operation which will read data from the secondary file:

```scala mdoc:compile-only
val primaryOrSecondaryData: IO[IOException, Array[Byte]] =
  readFile("primary.data").foldZIO(
    failure = _    => readFile("secondary.data"),
    success = data => ZIO.succeed(data)
  )
```

Nearly all error handling methods are defined in terms of `foldZIO`, because it is both powerful and fast.

In the following example, `foldZIO` is used to handle both failure and success of the `readUrls` method:

```scala mdoc:invisible
sealed trait Content
case class NoContent(t: Throwable) extends Content
case class OkContent(s: String) extends Content
def readUrls(file: String): Task[List[String]] = ZIO.succeed("Hello" :: Nil)
def fetchContent(urls: List[String]): UIO[Content] = ZIO.succeed(OkContent("Roger"))
```

```scala mdoc:silent
val urls: UIO[Content] =
  readUrls("urls.json").foldZIO(
    error   => ZIO.succeed(NoContent(error)),
    success => fetchContent(success)
  )
```

It's important to note that both `ZIO#fold` and `ZIO#foldZIO` operators cannot catch fiber interruptions. So the following application will crash due to `InterruptedException`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = (ZIO.interrupt *> ZIO.fail("Uh oh!")).fold(_ => (), _ => ())
}
```

And here is the output:

```scala
timestamp=2022-02-24T13:41:01.696273024Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.InterruptedException: Interrupted by thread "zio-fiber-"
   at <empty>.MainApp.run(MainApp.scala:4)"
```

## `ZIO#foldCause`/`ZIO#foldCauseZIO`

This cause version of the `fold` operator is useful to access the full cause of the underlying fiber. So in case of failure, based on the exact cause, we can determine what to do:

```scala
trait ZIO[-R, +E, +A] {
  def foldCause[B](
    failure: Cause[E] => B,
    success: A => B
  ): ZIO[R, Nothing, B]

  def foldCauseZIO[R1 <: R, E2, B](
    failure: Cause[E] => ZIO[R1, E2, B],
    success: A => ZIO[R1, E2, B]
  ): ZIO[R1, E2, B]
}
```

Among the fold operators, these are the most powerful combinators. They can recover from any error, even fiber interruptions.

In the following example, we are printing the proper message according to what cause occurred due to failure:

```scala mdoc:compile-only
import zio._

val exceptionalEffect: ZIO[Any, Throwable, Unit] = ???

val myApp: ZIO[Any, IOException, Unit] =
  exceptionalEffect.foldCauseZIO(
    failure = {
      case Cause.Fail(value, _)        => Console.printLine(s"failure: $value")
      case Cause.Die(value, _)         => Console.printLine(s"cause: $value")
      case Cause.Interrupt(failure, _) => Console.printLine(s"${failure.threadName} interrupted!")
      case _                           => Console.printLine("failed due to other causes")
    },
    success = succeed => Console.printLine(s"succeeded with $succeed value")
  )
```

When catching errors using this operator, if our cases were not exhaustive, we may receive a defect of the type `scala.MatchError` :

```scala mdoc:compile-only
import zio._

import java.io.IOException

object MainApp extends ZIOAppDefault {
  val exceptionalEffect: ZIO[Any, Throwable, Unit] = ZIO.interrupt

  val myApp: ZIO[Any, IOException, Unit] =
    exceptionalEffect.foldCauseZIO(
      failure = {
        case Cause.Fail(value, _) => ZIO.debug(s"failure: $value")
        case Cause.Die(value, _) => ZIO.debug(s"cause: ${value.toString}")
        // case Cause.Interrupt(failure, _) => ZIO.debug(s"${failure.threadName} interrupted!")
      },
      success = succeed => ZIO.debug(s"succeeded with $succeed value")
    )

  def run = myApp
}
```

The output:

```scala
timestamp=2022-02-24T11:05:40.241436257Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" scala.MatchError: Interrupt(Runtime(2,1645700739),Trace(Runtime(2,1645700739),Chunk(<empty>.MainApp.exceptionalEffect(MainApp.scala:6),<empty>.MainApp.myApp(MainApp.scala:9)))) (of class zio.Cause$Interrupt)
	at MainApp$.$anonfun$myApp$1(MainApp.scala:10)
	at zio.ZIO$TracedCont$$anon$33.apply(ZIO.scala:6167)
	at zio.ZIO$TracedCont$$anon$33.apply(ZIO.scala:6165)
	at zio.internal.FiberContext.runUntil(FiberContext.scala:885)
	at zio.internal.FiberContext.run(FiberContext.scala:115)
	at zio.internal.ZScheduler$$anon$1.run(ZScheduler.scala:151)
	at zio.internal.FiberContext.runUntil(FiberContext.scala:538)"
```

## `ZIO#foldTraceZIO`

This version of fold, provide us the facility to access the trace info of the failure:

```scala
trait ZIO[-R, +E, +A] {
  def foldTraceZIO[R1 <: R, E2, B](
    failure: ((E, Trace)) => ZIO[R1, E2, B],
    success: A => ZIO[R1, E2, B]
  )(implicit ev: CanFail[E]): ZIO[R1, E2, B]
}
```

```scala mdoc:invisible
import zio._

sealed trait AgeValidationException extends Exception
case class NegativeAgeException(age: Int) extends AgeValidationException
case class IllegalAgeException(age: Int)  extends AgeValidationException

def validate(age: Int): ZIO[Any, AgeValidationException, Int] = {
  if (age < 0)
    ZIO.fail(NegativeAgeException(age))
  else if (age < 18)
    ZIO.fail(IllegalAgeException(age))
  else ZIO.succeed(age)
}
```

```scala mdoc:compile-only
import zio._

val result: ZIO[Any, Nothing, Int] =
  validate(5).foldTraceZIO(
    failure = {
      case (_: NegativeAgeException, trace) =>
        ZIO.succeed(0).debug(
          "The entered age is negative\n" +
            s"trace info: ${trace.stackTrace.mkString("\n")}"
        )
      case (_: IllegalAgeException, trace) =>
        ZIO.succeed(0).debug(
          "The entered age in not legal\n" +
            s"trace info: ${trace.stackTrace.mkString("\n")}"
        )
    },
    success = s => ZIO.succeed(s)
  )
```

Note that similar to `ZIO#fold` and `ZIO#foldZIO` this operator cannot recover from fiber interruptions.
