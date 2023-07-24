---
id: rio 
title: "RIO"
---

`RIO[R, A]` is a type alias for `ZIO[R, Throwable, A]`, which represents an effect that requires an `R`, and may fail with a `Throwable` value, or succeed with an `A`.

:::note

In Scala, the _type alias_ is a way to give a name to another type, to avoid having to repeat the original type again and again. It doesn't affect the type-checking process. It just helps us to have an expressive API design.
:::

Let's see how `RIO` is defined:
```scala mdoc:invisible
import zio.ZIO
```

```scala mdoc:silent
type RIO[-R, +A]  = ZIO[R, Throwable, A]
```

So `RIO` is equal to a `ZIO` that requires `R`, and whose error channel is `Throwable`. It succeeds with `A`.


:::note _Principle of Least Power_

The `ZIO` data type is the most powerful effect in the ZIO library. It helps us to model various types of workflows. On the other hand, the type aliases are a way of specializing the `ZIO` type for less powerful workflows. 

Often, we don't need such a piece of powerful machinery. So as a rule of thumb, whenever we require a less powerful effect, it's better to use the appropriate specialized type alias.

So there is no need to convert type aliases to the `ZIO` data type, and whenever the `ZIO` data type is required, we can use the most precise type alias to fit our workflow requirement.
:::
