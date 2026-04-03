---
id: rejecting-some-success-values
title: "Rejecting Some Success Values"
---

We can reject some success values using the `ZIO#reject` operator:

```scala
trait ZIO[-R, +E, +A] {
  def reject[E1 >: E](pf: PartialFunction[A, E1]): ZIO[R, E1, A]

  def rejectZIO[R1 <: R, E1 >: E](
    pf: PartialFunction[A, ZIO[R1, E1, E1]]
  ): ZIO[R1, E1, A]
}
```

If the `PartialFunction` matches, it will reject that success value and convert that to a failure, otherwise it will continue with the original success value:

```scala mdoc:compile-only
import zio._

val myApp: ZIO[Any, String, Int] =
  Random
    .nextIntBounded(20)
    .reject {
      case n if n % 2 == 0 => s"even number rejected: $n"
      case 5               => "number 5 was rejected"
    }
    .debug
```
