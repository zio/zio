---
id: tapping-errors
title: "Tapping Errors"
---

Like [tapping for success values](../../core/zio/zio.md#tapping) ZIO has several operators for tapping error values. So we can peek into failures or underlying defects or causes:

```scala
trait ZIO[-R, +E, +A] {
  def tapError[R1 <: R, E1 >: E](f: E => ZIO[R1, E1, Any]): ZIO[R1, E1, A]
  def tapErrorCause[R1 <: R, E1 >: E](f: Cause[E] => ZIO[R1, E1, Any]): ZIO[R1, E1, A]
  def tapErrorTrace[R1 <: R, E1 >: E](f: ((E, Trace)) => ZIO[R1, E1, Any]): ZIO[R1, E1, A]
  def tapDefect[R1 <: R, E1 >: E](f: Cause[Nothing] => ZIO[R1, E1, Any]): ZIO[R1, E1, A]
  def tapBoth[R1 <: R, E1 >: E](f: E => ZIO[R1, E1, Any], g: A => ZIO[R1, E1, Any]): ZIO[R1, E1, A]
  def tapEither[R1 <: R, E1 >: E](f: Either[E, A] => ZIO[R1, E1, Any]): ZIO[R1, E1, A]
}
```

Let's try an example:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[Any, NumberFormatException, Int] =
    Console.readLine
      .mapAttempt(_.toInt)
      .refineToOrDie[NumberFormatException]
      .tapError { e =>
        ZIO.debug(s"user entered an invalid input: ${e}").when(e.isInstanceOf[NumberFormatException])
      }

  def run = myApp
}
```
