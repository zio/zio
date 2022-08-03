---
id: map-and-flatmap
title: "Map and FlatMap on The Error Channel"
---

Other than `ZIO#map` and `ZIO#flatMap`, ZIO has several other operators to manage errors while mapping:

## `ZIO#mapError`/`ZIO#mapErrorCause`

Let's begin with `ZIO#mapError` and `ZIO#mapErrorCause`. These operators help us to access the error channel as a raw error value or as a type of `Cause` and map their values:

```scala
trait ZIO[-R, +E, +A] {
  def mapError[E2](f: E => E2): ZIO[R, E2, A]
  def mapErrorCause[E2](h: Cause[E] => Cause[E2]): ZIO[R, E2, A]
}
```

Here are two simple examples for these operators:

```scala mdoc:compile-only
import zio._

def parseInt(input: String): ZIO[Any, NumberFormatException, Int] = ???

// mapping the error of the original effect to its message
val r1: ZIO[Any, String, Int] =
  parseInt("five")                // ZIO[Any, NumberFormatException, Int]
    .mapError(e => e.getMessage)  // ZIO[Any, String, Int]

// mapping the cause of the original effect to be untraced
val r2 = parseInt("five")         // ZIO[Any, NumberFormatException, Int]
  .mapErrorCause(_.untraced)      // ZIO[Any, NumberFormatException, Int]
```

> _**Note:**_
>
> Note that mapping over an effect's success or error channel does not change the success or failure of the effect, in the same way that mapping over an `Either` does not change whether the `Either` is `Left` or `Right`.

## `ZIO#mapAttempt`

The `ZIO#mapAttempt` returns an effect whose success is mapped by the specified side-effecting `f` function, translating any thrown exceptions into typed failed effects. So it converts an unchecked exception to a checked one by returning the `RIO` effect.

```scala
  trait ZIO[-R, +E, +A] {
    def map[B](f: A => B): ZIO[R, E, B]
    def mapAttempt[B](f: A => B): ZIO[R, Throwable, B]
  }
```

Using operations that can throw exceptions inside of `ZIO#map` such as `effect.map(_.unsafeOpThatThrows)` will result in a defect (an unexceptional effect that will die). In the following example, when we use the `ZIO#map` operation. So, if the `String#toInt` operation throws `NumberFormatException` it will be converted to a defect:

```scala mdoc:compile-only
import zio._

val result: ZIO[Any, Nothing, Int] =
  Console.readLine.orDie.map(_.toInt)
```

As a result, when the map operation is unsafe, it may lead to buggy programs that may crash, as shown below:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[Any, Nothing, Unit] =
    Console.print("Please enter a number: ").orDie *>
      Console.readLine.orDie
        .map(_.toInt)
        .map(_ % 2 == 0)
        .flatMap {
          case true =>
            Console.printLine("You have entered an even number.").orDie
          case false =>
            Console.printLine("You have entered an odd number.").orDie
        }

  def run = myApp
}
```

Converting literal "five" String to Int by calling `toInt` is a side effecting operation because it will throw `NumberFormatException` exception. So in the previous example, if we enter a non-integer number, e.g. "five", it will die because of a `NumberFormatException` defect:

```scala
Please enter a number: five
timestamp=2022-03-17T14:01:33.323639073Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.NumberFormatException: For input string: "five"
	at java.base/java.lang.NumberFormatException.forInputString(NumberFormatException.java:67)
	at java.base/java.lang.Integer.parseInt(Integer.java:660)
	at java.base/java.lang.Integer.parseInt(Integer.java:778)
	at scala.collection.StringOps$.toInt$extension(StringOps.scala:910)
	at MainApp$.$anonfun$myApp$3(MainApp.scala:7)
	at MainApp$.$anonfun$myApp$3$adapted(MainApp.scala:7)
	at zio.ZIO.$anonfun$map$1(ZIO.scala:1168)
	at zio.ZIO$FlatMap.apply(ZIO.scala:6182)
	at zio.ZIO$FlatMap.apply(ZIO.scala:6171)
	at zio.internal.FiberContext.runUntil(FiberContext.scala:885)
	at zio.internal.FiberContext.run(FiberContext.scala:115)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1130)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:630)
	at java.base/java.lang.Thread.run(Thread.java:831)
	at zio.internal.FiberContext.runUntil(FiberContext.scala:538)
	at <empty>.MainApp.myApp(MainApp.scala:8)
	at <empty>.MainApp.myApp(MainApp.scala:9)"
```

We can see that the error channel of `myApp` is typed as `Nothing`, so it's not an exceptional error. If we want typed effects, this behavior is not intended. So instead of `ZIO#map` we can use the `mapAttempt` combinator which is a safe map operator that translates all thrown exceptions into typed exceptional effect.

To prevent converting exceptions to defects, we can use `ZIO#mapAttempt` which converts any exceptions to exceptional effects:

```scala mdoc:compile-only
import zio._

val result: ZIO[Any, Throwable, Int] =
  Console.readLine.orDie.mapAttempt(_.toInt)
```

Having typed errors helps us to catch errors explicitly and handle them in the right way:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[Any, Nothing, Unit] =
    Console.print("Please enter a number: ").orDie *>
      Console.readLine.orDie
        .mapAttempt(_.toInt)
        .map(_ % 2 == 0)
        .flatMap {
          case true =>
            Console.printLine("You have entered an even number.").orDie
          case false =>
            Console.printLine("You have entered an odd number.").orDie
        }.catchAll(_ => myApp)

  def run = myApp
}

// Please enter a number: five
// Please enter a number: 4
// You have entered an even number.
```

## `ZIO#mapBoth`

It takes two map functions, one for the error channel and the other for the success channel, and maps both sides of a ZIO effect:

```scala
trait ZIO[-R, +E, +A] {
  def mapBoth[E2, B](f: E => E2, g: A => B): ZIO[R, E2, B]
}
```

Here is a simple example:

```scala mdoc:compile-only
import zio._

val result: ZIO[Any, String, Int] =
  Console.readLine.orDie.mapAttempt(_.toInt).mapBoth(
    _ => "non-integer input",
    n => Math.abs(n)
  )
```

## `ZIO#flatMapError`

Unlike `ZIO#flatMap` the `ZIO#flatMapError` combinator chains two effects, where the second effect is dependent on the error channel of the first effect:

```scala
trait ZIO[-R, +E, +A] {
  def flatMapError[R1 <: R, E2](
    f: E => ZIO[R1, Nothing, E2]
  ): ZIO[R1, E2, A]
}
```

In the following example, we are trying to find a random prime number between 1000 and 10000. We will use the `ZIO#flatMapError` to collect all errors inside a `Ref` of type `List[String]`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def isPrime(n: Int): Boolean =
    if (n <= 1) false else (2 until n).forall(i => n % i != 0)

  def findPrimeBetween(
      minInclusive: Int,
      maxExclusive: Int
  ): ZIO[Any, List[String], Int] =
    for {
      errors <- Ref.make(List.empty[String])
      number <- Random
        .nextIntBetween(minInclusive, maxExclusive)
        .reject {
          case n if !isPrime(n) =>
            s"non-prime number rejected: $n"
        }
        .flatMapError(error => errors.updateAndGet(_ :+ error))
        .retryUntil(_.length >= 5)
    } yield number

  val myApp: ZIO[Any, Nothing, Unit] =
    findPrimeBetween(1000, 10000)
      .flatMap(prime => Console.printLine(s"found a prime number: $prime").orDie)
      .catchAll { (errors: List[String]) =>
        Console.printLine(
          s"failed to find a prime number after 5 attempts:\n  ${errors.mkString("\n  ")}"
        )
      }
      .orDie

  def run = myApp
}
```
