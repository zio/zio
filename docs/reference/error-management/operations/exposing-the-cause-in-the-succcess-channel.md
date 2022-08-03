---
id: exposing-the-cause-in-the-success-channel
title: "Exposing the Cause in The Success Channel"
---

Using the `ZIO#cause` operation we can expose the cause, and then by using `ZIO#uncause` we can reverse this operation:

```scala
trait ZIO[-R, +E, +A] {
  def cause: URIO[R, Cause[E]]
  def uncause[E1 >: E](implicit ev: A IsSubtypeOfOutput Cause[E1]): ZIO[R, E1, Unit]
}
```

In the following example, we expose and then untrace the underlying cause:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val f1: ZIO[Any, String, Int] =
    ZIO.fail("Oh uh!").as(1)

  val f2: ZIO[Any, String, Int] =
    ZIO.fail("Oh error!").as(2)

  val myApp: ZIO[Any, String, (Int, Int)] = f1 zipPar f2

  def run = myApp.cause.map(_.untraced).debug
}
```

Sometimes the [`ZIO#mapErrorCause`](map-and-flatmap.md) operator is a better choice when we just want to map the underlying cause without exposing the cause.
