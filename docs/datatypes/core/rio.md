---
id: rio 
title: "RIO"
---

`RIO[R, A]` is a type alias for `ZIO[R, Throwable, A]`, which represents an effect that requires an `R`, and may fail with a `Throwable` value, or succeed with an `A`.

Let's see how `RIO` is defined:
```scala mdoc:invisible
import zio.ZIO
```

```scala mdoc:silent
type RIO[-R, +A]  = ZIO[R, Throwable, A]
```

So the `RIO` just equal to `ZIO` which its error channel is `Throwable`.