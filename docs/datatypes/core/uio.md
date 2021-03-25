---
id: uio
title: "UIO"
---

`UIO[A]` is a type alias for `ZIO[Any, Nothing, A]`, which represents an **Unexceptional** effect that doesn't require any specific environment, and cannot fail, but can succeed with an `A`.

Let's write a fibonacci function. As we don't expect any failure, it is an unexceptional effect:

In the following example, the `fib`, doesn't have any requirement, as it is an unexceptional effect, we don't except any failure, and it succeeds with value of type `Int`:

```scala mdoc:silent
import zio.UIO
def fib(n: Int): UIO[Int] =
  if (n <= 1) {
    UIO.succeed(1)
  } else {
    for {
      fiber1 <- fib(n - 2).fork
      fiber2 <- fib(n - 1).fork
      v2     <- fiber2.join
      v1     <- fiber1.join
    } yield v1 + v2
  }
```
