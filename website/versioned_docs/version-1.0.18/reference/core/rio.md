---
id: rio
title: "RIO"
---

`RIO[R, A]` is a type alias for `ZIO[R, Throwable, A]`, which represents an effect that requires an `R`, and may fail with a `Throwable` value, or succeed with an `A`.

> **_Note:_**
>
> In Scala, the _type alias_ is a way to give a name to another type, to avoid having to repeat the original type again and again. It doesn't affect the type-checking process. It just helps us to have an expressive API design.

Let's see how `RIO` is defined:

```scala
type RIO[-R, +A]  = ZIO[R, Throwable, A]
```

So the `RIO` just equal to `ZIO` which its error channel is `Throwable`.


> **Note:** _Principle of The Least Power_
>
> The `ZIO` data type is the most powerful effect in the ZIO library. It helps us to model various types of workflows. On other hand, the type aliases are a way of subtyping and specializing the `ZIO` type, specific for a less powerful workflow. 
>
> Lot of the time, we don't need such a piece of powerful machinery. So as a rule of thumb, whenever we require a less powerful effect, it's better to use the proper specialized type alias.
>
> So there is no need to convert type aliases to the `ZIO` data type, whenever the `ZIO` data type is required, we can use the most precise type alias to fit our workflow requirement.
