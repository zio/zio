---
id: sequential-and-parallel-errors
title: "Sequential and Parallel Errors"
---

A simple and regular ZIO application usually fails with one error, which is the first error encountered by the ZIO runtime:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val fail = ZIO.fail("Oh uh!")
  val die = ZIO.dieMessage("Boom!")
  val interruption = ZIO.interrupt

  def run = (fail <*> die) *> interruption
}
```

This application will fail with the first error which is "Oh uh!":

```scala
timestamp=2022-03-09T09:50:22.067072131Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.String: Oh uh!
	at <empty>.MainApp.fail(MainApp.scala:4)"
```

In some cases, we may run into multiple errors. When we perform parallel computations, the application may fail due to multiple errors:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = ZIO.fail("Oh!") <&> ZIO.fail("Uh!")
}
```

If we run this application, we can see two exceptions in two different fibers that caused the failure (`zio-fiber-0` and `zio-fiber-14`):

```scala
timestamp=2022-03-09T08:05:48.703035927Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-13" java.lang.String: Oh!
	at <empty>.MainApp.run(MainApp.scala:4)
Exception in thread "zio-fiber-14" java.lang.String: Uh!
	at <empty>.MainApp.run(MainApp.scala:4)"
```

ZIO has a combinator called `ZIO#parallelErrors` that exposes all parallel failure errors in the error channel:

```scala mdoc:compile-only
import zio._

val result: ZIO[Any, ::[String], Nothing] =
  (ZIO.fail("Oh uh!") <&> ZIO.fail("Oh Error!")).parallelErrors
```

Note that this operator is only for failures, not defects or interruptions.

Also, when we work with resource-safety operators like `ZIO#ensuring` we can have multiple sequential errors. Why? because regardless of the original effect has any errors or not, the finalizer is uninterruptible. So the finalizer will be run. Unless the finalizer should be an unexceptional effect (`URIO`), it may die because of a defect. Therefore, it creates multiple sequential errors:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = ZIO.fail("Oh uh!").ensuring(ZIO.dieMessage("Boom!"))
}
```

When we run this application, we can see that the original failure (`Oh uh!`) was suppressed by another defect (`Boom!`):

```scala
timestamp=2022-03-09T08:30:56.563179230Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.String: Oh uh!
	at <empty>.MainApp.run(MainApp.scala:4)
	Suppressed: java.lang.RuntimeException: Boom!
		at <empty>.MainApp.run(MainApp.scala:4)"
```
