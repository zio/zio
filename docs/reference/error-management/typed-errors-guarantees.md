---
id: typed-errors-guarantees
title: "Typed Errors Guarantees"
sidebar_label: "Typed Errors Guarantees"
---

**Typed errors don't guarantee the absence of defects and interruptions.** Having an effect of type `ZIO[R, E, A]`, means it can fail because of some failure of type `E`, but it doesn't mean it can't die or be interrupted. So the error channel is only for `failure` errors.

In the following example, the type of the `validateNonNegativeNumber` function is `ZIO[Any, String, Int]` which denotes it is a typed exceptional effect. It can fail of type `String` but it still can die with the type of `NumberFormatException` defect:

```scala mdoc:silent
import zio._

def validateNonNegativeNumber(input: String): ZIO[Any, String, Int] =
  input.toIntOption match {
    case Some(value) if value >= 0 =>
      ZIO.succeed(value)
    case Some(other) =>
      ZIO.fail(s"the entered number is negative: $other")
    case None =>
      ZIO.die(
        new NumberFormatException(
          s"the entered input is not in the correct number format: $input"
        )
      )
  }
```

Also, its underlying fiber can be interrupted without affecting the type of the error channel:

```scala mdoc:compile-only
import zio._

val myApp: ZIO[Any, String, Int] =
  for {
    f <- validateNonNegativeNumber("5").fork
    _ <- f.interrupt
    r <- f.join
  } yield r
```

Therefore, if we run the `myApp` effect, it will be interrupted before it gets the chance to finish.
