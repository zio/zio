---
id: flipping-error-and-success-channels
title: "Flipping Error and Success Channels"
---

Sometimes, we would like to apply some methods on the error channel which are specific for the success channel, or we want to apply some methods on the success channel which are specific for the error channel. Therefore, we can flip the error and success channel and before flipping back, we can perform the right operator on flipped channels:

```scala
trait ZIO[-R, +E, +A] {
  def flip: ZIO[R, A, E]
  def flipWith[R1, A1, E1](f: ZIO[R, A, E] => ZIO[R1, A1, E1]): ZIO[R1, E1, A1]
}
```

Assume we have the following example:

```scala mdoc:silent
import zio._

val evens: ZIO[Any, List[String], List[Int]] =
  ZIO.validate(List(1, 2, 3, 4, 5)) { n =>
    if (n % 2 == 0)
      ZIO.succeed(n)
    else
      ZIO.fail(s"$n is not even")
  }
```

We want to reverse the order of errors. In order to do that instead of using `ZIO#mapError`, we can map the error channel by using flip operators:

```scala mdoc:compile-only
import zio._

val r1: ZIO[Any, List[String], List[Int]] = evens.mapError(_.reverse)
val r2: ZIO[Any, List[String], List[Int]] = evens.flip.map(_.reverse).flip
val r3: ZIO[Any, List[String], List[Int]] = evens.flipWith(_.map(_.reverse))
```
