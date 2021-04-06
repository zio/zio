---
id: io
title: "IO"
---

`IO[E, A]` is a type alias for `ZIO[Any, E, A]`, which represents an effect that has no requirements, and may fail with an `E`, or succeed with an `A`.

Let's see how the `IO` type alias is defined:

```scala mdoc:invisible
import zio.ZIO
```

```scala mdoc:silent
type IO[+E, +A] = ZIO[Any, E, A]
```

So the `IO` just equal to `ZIO` which doesn't need any requirement.

## Unproductive IO

`ZIO` values of type `IO[E, Nothing]` (where the value type is `Nothing`) are considered _unproductive_,
because the `Nothing` type is _uninhabitable_, i.e. there can be no actual values of type `Nothing`. Values of this type may fail with an `E`, but will never produce a value.
