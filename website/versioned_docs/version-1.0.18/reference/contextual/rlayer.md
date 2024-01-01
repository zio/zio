---
id: rlayer
title: "RLayer"
---

`RLayer[-RIn, +ROut]` is a type alias for `ZLayer[RIn, Throwable, ROut]`, which represents a layer that requires `RIn` as its input, it may fail with `Throwable` value, or returns `ROut` as its output.

```scala
type RLayer[-RIn, +ROut]  = ZLayer[RIn, Throwable, ROut]
```