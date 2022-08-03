---
id: filtering-the-success-channel
title: "Filtering the Success Channel Values"
---

ZIO has a variety of operators that can filter values on the success channel based on a given predicate, and if the predicate fails, we can use different strategies:

- Failing the original effect (`ZIO#filterOrFail`)
- Dying the original effect (`ZIO#filterOrDie` and `ZIO#filterOrDieMessage`)
- Running an alternative ZIO effect (`ZIO#filterOrElse` and `ZIO#filterOrElseWith`)

```scala mdoc:compile-only
import zio._

def getNumber: ZIO[Any, Nothing, Int] =
  (Console.print("Please enter a non-negative number: ") *>
    Console.readLine.mapAttempt(_.toInt))
    .retryUntil(!_.isInstanceOf[NumberFormatException]).orDie

val r1: ZIO[Any, String, Int] =
  Random.nextInt.filterOrFail(_ >= 0)("random number is negative")

val r2: ZIO[Any, Nothing, Int] =
  Random.nextInt.filterOrDie(_ >= 0)(
    new IllegalArgumentException("random number is negative")
  )

val r3: ZIO[Any, Nothing, Int] =
  Random.nextInt.filterOrDieMessage(_ >= 0)("random number is negative")

val r4: ZIO[Any, Nothing, Int] =
  Random.nextInt.filterOrElse(_ >= 0)(getNumber)

val r5: ZIO[Any, Nothing, Int] =
  Random.nextInt.filterOrElseWith(_ >= 0)(x => ZIO.succeed(-x))
```
