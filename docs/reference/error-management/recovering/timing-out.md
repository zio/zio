---
id: timing-out
title: "Timing out"
sidebar_label: "5. Timing out"
---

## `ZIO#timeout`

ZIO lets us timeout any effect using the `ZIO#timeout` method, which returns a new effect that succeeds with an `Option`. A value of `None` indicates the timeout elapsed before the effect completed. If an effect times out, then instead of continuing to execute in the background, it will be interrupted so no resources will be wasted.

Assume we have the following effect:

```scala mdoc:silent
import zio._

val myApp =
  for {
    _ <- ZIO.debug("start doing something.")
    _ <- ZIO.sleep(2.second)
    _ <- ZIO.debug("my job is finished!")
  } yield "result"
```

We should note that when we use the `ZIO#timeout` operator on the `myApp`, it doesn't return until one of the following situations happens:

1. The original effect returns before the timeout elapses so the output will be `Some` of the produced value by the original effect:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    myApp
      .timeout(3.second)
      .debug("output")
      .timed
      .map(_._1.toMillis / 1000)
      .debug("execution time of the whole program in second")
}

// Output:
// start doing something.
// my job is finished!
// output: Some(result)
// execution time of the whole program in second: 2
```

2. The original effect interrupted after the timeout elapses:

    - If the effect is interruptible it will be immediately interrupted, and finally, the timeout operation produces `None` value.

   ```scala mdoc:compile-only
   import zio._

   object MainApp extends ZIOAppDefault {
     def run =
       myApp
         .timeout(1.second)
         .debug("output")
         .timed
         .map(_._1.toMillis / 1000)
         .debug("execution time of the whole program in second")
   }

   // Output:
   // start doing something.
   // output: None
   // execution time of the whole program in second: 1
   ```

    - If the effect is uninterruptible it will be blocked until the original effect safely finished its work, and then the timeout operator produces the `None` value:

   ```scala mdoc:compile-only
   import zio._

   object MainApp extends ZIOAppDefault {
     def run =
       myApp
         .uninterruptible
         .timeout(1.second)
         .debug("output")
         .timed
         .map(_._1.toMillis / 1000)
         .debug("execution time of the whole program in second")
   }

   // Output:
   // start doing something.
   // my job is finished!
   // output: None
   // execution time of the whole program in second: 2
   ```

Instead of waiting for the original effect to be interrupted, we can use `effect.disconnect.timeout` which first disconnects the effect's interruption signal before performing the timeout. By using this technique, we can return early after the timeout has passed and before an underlying effect has been interrupted.

```scala mdoc:compile-only
object MainApp extends ZIOAppDefault {
  def run =
    myApp
      .uninterruptible
      .disconnect
      .timeout(1.second)
      .debug("output")
      .timed
      .map(_._1.toMillis / 1000)
      .debug("execution time of the whole program in second")
}

// Output:
// start doing something.
// output: None
// execution time of the whole program in second: 1
```

By using this technique, the original effect will be interrupted in the background.

## `ZIO#timeoutTo`

This operator is similar to the previous one, but it also allows us to manually create the final result type:

```scala mdoc:silent
import zio._

val delayedNextInt: ZIO[Any, Nothing, Int] =
  Random.nextIntBounded(10).delay(2.second)

val r1: ZIO[Any, Nothing, Option[Int]] =
  delayedNextInt.timeoutTo(None)(Some(_))(1.seconds)

val r2: ZIO[Any, Nothing, Either[String, Int]] =
  delayedNextInt.timeoutTo(Left("timeout"))(Right(_))(1.seconds)

val r3: ZIO[Any, Nothing, Int] =
  delayedNextInt.timeoutTo(-1)(identity)(1.seconds)
```

## `ZIO#timeoutFail`/`ZIO#timeoutFailCause`

In case of elapsing the timeout, we can produce a particular error message:

```scala mdoc:compile-only
import zio._
import scala.concurrent.TimeoutException

val r1: ZIO[Any, TimeoutException, Int] =
  delayedNextInt.timeoutFail(new TimeoutException)(1.second)

val r2: ZIO[Any, Nothing, Int] =
  delayedNextInt.timeoutFailCause(Cause.die(new Error("timeout")))(1.second)
```
