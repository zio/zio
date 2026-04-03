---
id: io
title: "IO"
---

`IO[E, A]` is a type alias for `ZIO[Any, E, A]`, which represents an effect that has no requirements, and may fail with an `E`, or succeed with an `A`.

:::note
In Scala, the _type alias_ is a way to give a name to another type, to avoid having to repeat the original type again and again. It doesn't affect the type-checking process. It just helps us to have an expressive API design.
:::

Let's see how the `IO` type alias is defined:

```scala mdoc:invisible
import zio.ZIO
```

```scala mdoc:silent
type IO[+E, +A] = ZIO[Any, E, A]
```

So `IO` is equal to a `ZIO` that doesn't need any requirement.

`ZIO` values of type `IO[E, Nothing]` (where the value type is `Nothing`) are considered _unproductive_, because the `Nothing` type is _uninhabitable_, i.e. there can be no actual values of type `Nothing`. Values of this type may fail with an `E`, but will never produce a value.

:::note Principle of Least Power

The `ZIO` data type is the most powerful effect in the ZIO library. It helps us to model various types of workflows. On the other hand, the type aliases are a way of specializing the `ZIO` type for less powerful workflows. 

Often, we don't need such a piece of powerful machinery. So as a rule of thumb, whenever we require a less powerful effect, it's better to use the appropriate specialized type alias.

So there is no need to convert type aliases to the `ZIO` data type, and whenever the `ZIO` data type is required, we can use the most precise type alias to fit our workflow requirement.
:::
