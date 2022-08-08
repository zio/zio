---
id: failures
title: "Failures"
---

When writing ZIO application, we can model a failure, using the `ZIO.fail` constructor:

```scala
trait ZIO {
  def fail[E](error: => E): ZIO[Any, E, Nothing]
}
```

Let's try to model some failures using this constructor:

```scala mdoc:silent
import zio._

val f1: ZIO[Any, String, Nothing] = ZIO.fail("Oh uh!")
val f2: ZIO[Any, String, Int]     = ZIO.succeed(5) *> ZIO.fail("Oh uh!")
```

Now, let's try to run a failing effect and see what happens:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = ZIO.succeed(5) *> ZIO.fail("Oh uh!")
}
```

This will crash the application and print the following stack trace:

```scala
timestamp=2022-03-08T17:55:50.002161369Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.String: Oh uh!
	at <empty>.MainApp.run(MainApp.scala:4)"
```

We can also model a failure using `Exception`:

```
val f2: ZIO[Any, Exception, Nothing] =
  ZIO.fail(new Exception("Oh uh!"))
```

Or using user-defined failure types (domain errors):

```
case class NegativeNumberException(msg: String) extends Exception(msg)

val validateNonNegaive(input: Int): ZIO[Any, NegativeNumberException, Int] =
  if (input < 0)
    ZIO.fail(NegativeNumberException(s"entered negative number: $input"))
  else
    ZIO.succeed(input)
```

In the above examples, we can see that the type of the `validateNonNegaive` function is `ZIO[Any, NegativeNumberException, Int]`. It means this is an exceptional effect, which may fail with the type of `NegativeNumberException`.

The `ZIO.fail` constructor is somehow the moral equivalent of `throw` for pure codes. We will discuss this [further](../declarative.md).
