---
id: urio
title: "URIO"
---

`URIO[R, A]` is a type alias for `ZIO[R, Nothing, A]`, which represents an effect that requires an `R`, and cannot fail, but can succeed with an `A`.

> **_Note:_**
>
> In Scala, the _type alias_ is a way to give a name to another type, to avoid having to repeat the original type again and again. It doesn't affect the type-checking process. It just helps us to have an expressive API design.

Let's see how the `URIO` type alias is defined:


```scala
type URIO[-R, +A] = ZIO[R, Nothing, A]
```

So the `URIO` just equal to `ZIO` which requires `R` and cannot fail because in the Scala the `Nothing` type has no inhabitant, we can't create an instance of type `Nothing`. It succeeds with `A`.

In following example, the type of `putStrLn` is `URIO[Console, Unit]` which means, it requires `Console` service as an environment, and it succeeds with `Unit` value:


```scala
def putStrLn(line: => String): ZIO[Console, IOException, Unit] =
  ZIO.accessM(_.get putStrLn line)
```

> **Note:** _Principle of The Least Power_
>
> The `ZIO` data type is the most powerful effect in the ZIO library. It helps us to model various types of workflows. On other hand, the type aliases are a way of subtyping and specializing the `ZIO` type, specific for a less powerful workflow. 
>
> Lot of the time, we don't need such a piece of powerful machinery. So as a rule of thumb, whenever we require a less powerful effect, it's better to use the proper specialized type alias.
>
> So there is no need to convert type aliases to the `ZIO` data type, whenever the `ZIO` data type is required, we can use the most precise type alias to fit our workflow requirement.
