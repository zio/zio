---
id: converting-defects-to-failures
title: "Converting Defects to Failures"
---

Both `ZIO#resurrect` and `ZIO#absorb` are symmetrical opposite of the `ZIO#orDie` operator. The `ZIO#orDie` takes failures from the error channel and converts them into defects, whereas the `ZIO#absorb` and `ZIO#resurrect` take defects and convert them into failures:

```scala
trait ZIO[-R, +E, +A] {
  def absorb(implicit ev: E IsSubtypeOfError Throwable): ZIO[R, Throwable, A]
  def absorbWith(f: E => Throwable): ZIO[R, Throwable, A]
  def resurrect(implicit ev1: E IsSubtypeOfError Throwable): ZIO[R, Throwable, A]
}
```

Below are examples of the `ZIO#absorb` and `ZIO#resurrect` operators:

```scala mdoc:compile-only
import zio._

val effect1 =
  ZIO.fail(new IllegalArgumentException("wrong argument"))  // ZIO[Any, IllegalArgumentException, Nothing]
    .orDie                                                  // ZIO[Any, Nothing, Nothing]
    .absorb                                                 // ZIO[Any, Throwable, Nothing]
    .refineToOrDie[IllegalArgumentException]                // ZIO[Any, IllegalArgumentException, Nothing]

val effect2 =
  ZIO.fail(new IllegalArgumentException("wrong argument"))  // ZIO[Any, IllegalArgumentException , Nothing]
    .orDie                                                  // ZIO[Any, Nothing, Nothing]
    .resurrect                                              // ZIO[Any, Throwable, Nothing]
    .refineToOrDie[IllegalArgumentException]                // ZIO[Any, IllegalArgumentException, Nothing]
```

So what is the difference between `ZIO#absorb` and `ZIO#resurrect` operators?

The `ZIO#absorb` can recover from both `Die` and `Interruption` causes. Using this operator we can absorb failures, defects and interruptions using `ZIO#absorb` operation. It attempts to convert all causes into a failure, throwing away all information about the cause of the error:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val effect1 =
    ZIO.dieMessage("Boom!") // ZIO[Any, Nothing, Nothing]
      .absorb               // ZIO[Any, Throwable, Nothing]
      .ignore
  val effect2 =
    ZIO.interrupt           // ZIO[Any, Nothing, Nothing]
      .absorb               // ZIO[Any, Throwable, Nothing]
      .ignore

  def run =
    (effect1 <*> effect2)
      .debug("application exited successfully")
}
```

The output would be as below:

```scala
application exited successfully: ()
```

Whereas, the `ZIO#resurrect` will only recover from `Die` causes:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val effect1 =
    ZIO
      .dieMessage("Boom!") // ZIO[Any, Nothing, Nothing]
      .resurrect           // ZIO[Any, Throwable, Nothing]
      .ignore
  val effect2 =
    ZIO.interrupt          // ZIO[Any, Nothing, Nothing]
      .resurrect           // ZIO[Any, Throwable, Nothing]
      .ignore

  def run =
    (effect1 <*> effect2)
      .debug("couldn't recover from fiber interruption")
}
```

And, here is the output:

```scala
timestamp=2022-02-18T14:21:52.559872464Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.InterruptedException: Interrupted by thread "zio-fiber-"
	at <empty>.MainApp.effect2(MainApp.scala:10)
	at <empty>.MainApp.effect2(MainApp.scala:11)
	at <empty>.MainApp.effect2(MainApp.scala:12)
	at <empty>.MainApp.run(MainApp.scala:15)
	at <empty>.MainApp.run(MainApp.scala:16)"
```
