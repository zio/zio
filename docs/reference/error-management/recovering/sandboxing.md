---
id: sandboxing
title: "Sandboxing"
sidebar_label: "6. Sandboxing"
---

We know that a ZIO effect may fail due to a failure, a defect, a fiber interruption, or a combination of these causes. So a ZIO effect may contain more than one cause. Using the `ZIO#sandbox` operator, we can sandbox all errors of a ZIO application, whether the cause is a failure, defect, or a fiber interruption or combination of these. This operator exposes the full cause of a ZIO effect into the error channel:

```scala
trait ZIO[-R, +E, +A] {
  def sandbox: ZIO[R, Cause[E], A]
}
```

We can use the `ZIO#sandbox` operator to uncover the full causes of an _exceptional effect_. So we can see all the errors that occurred as a type of `Cause[E]` at the error channel of the `ZIO` data type. So then we can use normal error-handling operators such as `ZIO#catchSome` and `ZIO#catchAll` operators:

```scala mdoc:silent
import zio._

object MainApp extends ZIOAppDefault {
  val effect: ZIO[Any, String, String] =
    ZIO.succeed("primary result") *> ZIO.fail("Oh uh!")

  val myApp: ZIO[Any, Cause[String], String] =
    effect.sandbox.catchSome {
      case Cause.Interrupt(fiberId, _) =>
        ZIO.debug(s"Caught interruption of a fiber with id: $fiberId") *>
          ZIO.succeed("fallback result on fiber interruption")
      case Cause.Die(value, _) =>
        ZIO.debug(s"Caught a defect: $value") *>
          ZIO.succeed("fallback result on defect")
      case Cause.Fail(value, _) =>
        ZIO.debug(s"Caught a failure: $value") *>
          ZIO.succeed("fallback result on failure")
    }

  val finalApp: ZIO[Any, String, String] = myApp.unsandbox.debug("final result")

  def run = finalApp
}

// Output:
// Caught a failure: Oh uh!
// final result: fallback result on failure
```

Using the `sandbox` operation we are exposing the full cause of an effect. So then we have access to the underlying cause in more detail. After handling exposed causes using `ZIO#catch*` operators, we can undo the `sandbox` operation using the `unsandbox` operation. It will submerge the full cause (`Cause[E]`) again:

```scala mdoc:compile-only
import zio._

val effect: ZIO[Any, String, String] =
  ZIO.succeed("primary result") *> ZIO.fail("Oh uh!")

effect            // ZIO[Any, String, String]
  .sandbox        // ZIO[Any, Cause[String], String]
  .catchSome(???) // ZIO[Any, Cause[String], String]
  .unsandbox      // ZIO[Any, String, String]
```

There is another version of sandbox called `ZIO#sandboxWith`. This operator helps us to sandbox, then catch all causes, and then unsandbox back:

```scala
trait ZIO[-R, +E, +A] {
  def sandboxWith[R1 <: R, E2, B](f: ZIO[R1, Cause[E], A] => ZIO[R1, Cause[E2], B])
}
```

Let's try the previous example using this operator:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val effect: ZIO[Any, String, String] =
    ZIO.succeed("primary result") *> ZIO.fail("Oh uh!")

  val myApp =
    effect.sandboxWith[Any, String, String] { e =>
      e.catchSome {
        case Cause.Interrupt(fiberId, _) =>
          ZIO.debug(s"Caught interruption of a fiber with id: $fiberId") *>
            ZIO.succeed("fallback result on fiber interruption")
        case Cause.Die(value, _) =>
          ZIO.debug(s"Caught a defect: $value") *>
            ZIO.succeed("fallback result on defect")
        case Cause.Fail(value, _) =>
          ZIO.debug(s"Caught a failure: $value") *>
            ZIO.succeed("fallback result on failure")
      }
    }
  def run = myApp.debug
}

// Output:
// Caught a failure: Oh uh!
// fallback result on failure
```
