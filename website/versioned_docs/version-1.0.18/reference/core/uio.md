---
id: uio
title: "UIO"
---

`UIO[A]` is a type alias for `ZIO[Any, Nothing, A]`, which represents an **Unexceptional** effect that doesn't require any specific environment, and cannot fail, but can succeed with an `A`.

> **_Note:_**
>
> In Scala, the _type alias_ is a way to give a name to another type, to avoid having to repeat the original type again and again. It doesn't affect the type-checking process. It just helps us to have an expressive API design.

Let's see how the `UIO` type alias is defined:


```scala
type UIO[+A] = ZIO[Any, Nothing, A]
```

So the `UIO` just equal to `ZIO` which doesn't need any requirement and cannot fail because in the Scala the `Nothing` type has no inhabitant, we can't create an instance of type `Nothing`.

`ZIO` values of type `UIO[A]` (where the error type is `Nothing`) are considered _infallible_,
because the `Nothing` type is _uninhabitable_, i.e. there can be no actual values of type `Nothing`. Values of this type may produce an `A`, but will never fail with an `E`.

Let's write a fibonacci function. As we don't expect any failure, it is an unexceptional effect:

In the following example, the `fib`, doesn't have any requirement, as it is an unexceptional effect, we don't except any failure, and it succeeds with value of type `Int`:

```scala
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

> **Note:** _Principle of The Least Power_
>
> The `ZIO` data type is the most powerful effect in the ZIO library. It helps us to model various types of workflows. On other hand, the type aliases are a way of subtyping and specializing the `ZIO` type, specific for a less powerful workflow. 
>
> Lot of the time, we don't need such a piece of powerful machinery. So as a rule of thumb, whenever we require a less powerful effect, it's better to use the proper specialized type alias.
>
> So there is no need to convert type aliases to the `ZIO` data type, whenever the `ZIO` data type is required, we can use the most precise type alias to fit our workflow requirement.
