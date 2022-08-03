---
id: defects
title: "Defects"
---

By providing a `Throwable` value to the `ZIO.die` constructor, we can describe a dying effect:

```scala
object ZIO {
  def die(t: => Throwable): ZIO[Any, Nothing, Nothing]
}
```

Here is an example of such effect, which will die because of encountering _divide by zero_ defect:

```scala mdoc:compile-only
import zio._

val dyingEffect: ZIO[Any, Nothing, Nothing] =
  ZIO.die(new ArithmeticException("divide by zero"))
```

The result is the creation of a ZIO effect whose error channel and success channel are both `Nothing`. In other words, this effect cannot fail and does not produce anything. Instead, it is an effect describing a _defect_ or an _unexpected error_.

Let's see what happens if we run this effect:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = ZIO.die(new ArithmeticException("divide by zero"))
}
```

If we run this effect, ZIO runtime will print the stack trace that belongs to this defect. So, here is the output:

```scala
timestamp=2022-02-16T13:02:44.057191215Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.ArithmeticException: divide by zero
	at MainApp$.$anonfun$run$1(MainApp.scala:4)
	at zio.ZIO$.$anonfun$die$1(ZIO.scala:3384)
	at zio.internal.FiberContext.runUntil(FiberContext.scala:255)
	at zio.internal.FiberContext.run(FiberContext.scala:115)
	at zio.internal.ZScheduler$$anon$1.run(ZScheduler.scala:151)
	at <empty>.MainApp.run(MainApp.scala:4)"
```

The `ZIO.die` constructor is used to manually describe a dying effect because of a defect inside the code.

For example, assume we want to write a `divide` function that takes two numbers and divides the first number by the second. We know that the `divide` function is not defined for zero dominators. Therefore, we should signal an error if division by zero occurs.

We have two choices to implement this function using the ZIO effect:

1. We can divide the first number by the second, and if the second number was zero, we can fail the effect using `ZIO.fail` with the `ArithmeticException` failure value:

```scala mdoc:compile-only
import zio._

def divide(a: Int, b: Int): ZIO[Any, ArithmeticException, Int] =
  if (b == 0)
    ZIO.fail(new ArithmeticException("divide by zero"))
  else
    ZIO.succeed(a / b)
```

2. We can divide the first number by the second. In the case of zero for the second number, we use `ZIO.die` to kill the effect by sending a signal of `ArithmeticException` as a defect:

```scala mdoc:compile-only
import zio._

def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] =
  if (b == 0)
    ZIO.die(new ArithmeticException("divide by zero")) // Unexpected error
  else
    ZIO.succeed(a / b)
```

So what is the difference between these two approaches? Let's compare the function signature:

```scala
def divide(a: Int, b: Int): ZIO[Any, ArithmeticException, Int]   // using ZIO.fail
def divide(a: Int, b: Int): ZIO[Any, Nothing,             Int]   // using ZIO.die
```

1. The first approach, models the _divide by zero_ error by _failing_ the effect. We call these failures _expected errors_ or _typed error_.
2. While the second approach models the _divide by zero_ error by _dying_ the effect. We call these kinds of errors _unexpected errors_, _defects_ or _untyped errors_.

We use the first method when we are handling errors as we expect them, and thus we know how to handle them. In contrast, the second method is used when we aren't expecting those errors in our domain, and we don't know how to handle them. Therefore, we use the _let it crash_ philosophy.

In the second approach, we can see that the `divide` function indicates that it cannot fail because it's error channel is `Nothing`. However, it doesn't mean that this function hasn't any defects. ZIO defects are not typed, so they cannot be seen in type parameters.

Note that to create an effect that will die, we shouldn't throw an exception inside the `ZIO.die` constructor, although it works. Instead, the idiomatic way of creating a dying effect is to provide a `Throwable` value into the `ZIO.die` constructor:

```scala mdoc:compile-only
import zio._

val defect1 = ZIO.die(new ArithmeticException("divide by zero"))       // recommended
val defect2 = ZIO.die(throw new ArithmeticException("divide by zero")) // not recommended
```

Also, if we import a code that may throw an exception, all the exceptions will be translated to the ZIO defect:

```scala mdoc:compile-only
import zio._

val defect3 = ZIO.succeed(throw new Exception("boom!"))
```

Therefore, in the second approach of the `divide` function, we do not require to manually die the effect in case of the _dividing by zero_, because the JVM itself throws an `ArithmeticException` when the denominator is zero.

When we import any code into the `ZIO` effect, any exception is thrown inside that code will be translated to _ZIO defects_ by default. So the following program is the same as the previous example:

```scala mdoc:compile-only
import zio._

def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] =
  ZIO.succeed(a / b)
```

Another important note is that if we `map`/`flatMap` a ZIO effect and then accidentally throw an exception inside the map operation, that exception will be translated to a ZIO defect:

```scala mdoc:compile-only
import zio._

val defect4 = ZIO.succeed(???).map(_ => throw new Exception("Boom!"))
val defect5 = ZIO.attempt(???).map(_ => throw new Exception("Boom!"))
```
