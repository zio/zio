---
id: chaining-effects-based-on-errors
title: "Chaining Effects Based on Errors"
---


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
