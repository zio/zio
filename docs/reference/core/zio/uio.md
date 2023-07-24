---
id: uio
title: "UIO"
---

`UIO[A]` is a type alias for `ZIO[Any, Nothing, A]`, which represents an **Unexceptional** effect that doesn't require any specific environment, and cannot fail, but can succeed with an `A`.

:::note

In Scala, the _type alias_ is a way to give a name to another type, to avoid having to repeat the original type again and again. It doesn't affect the type-checking process. It just helps us to have an expressive API design.
:::

Let's see how the `UIO` type alias is defined:

```scala mdoc:invisible
import zio.ZIO
```

```scala
type UIO[+A] = ZIO[Any, Nothing, A]
```

So `UIO` is equal to a `ZIO` that doesn't need any requirement (because it accepts `Any` environment) and that cannot fail (because in Scala the `Nothing` type is _uninhabitable_, i.e. there can be no actual value of type `Nothing`). It succeeds with `A`.

`ZIO` values of type `UIO[A]` are considered _infallible_. Values of this type may produce an `A`, but will never fail.

Let's write a Fibonacci function. In the following example, the `fib` function is an unexceptional effect, since it has no requirements, we don't expect any failure, and it succeeds with a value of type `Int`:

```scala mdoc:reset:silent
import zio.{UIO, ZIO}

def fib(n: Int): UIO[Int] =
  if (n <= 1) {
    ZIO.succeed(1)
  } else {
    for {
      fiber1 <- fib(n - 2).fork
      fiber2 <- fib(n - 1).fork
      v2     <- fiber2.join
      v1     <- fiber1.join
    } yield v1 + v2
  }
```

:::note Principle of Least Power

The `ZIO` data type is the most powerful effect in the ZIO library. It helps us to model various types of workflows. On the other hand, the type aliases are a way of specializing the `ZIO` type for less powerful workflows. 

Often, we don't need such a piece of powerful machinery. So as a rule of thumb, whenever we require a less powerful effect, it's better to use the appropriate specialized type alias.

So there is no need to convert type aliases to the `ZIO` data type, and whenever the `ZIO` data type is required, we can use the most precise type alias to fit our workflow requirement.
:::
