---
id: rprovider
title: "RProvider"
---

`RProvider[-RIn, +ROut]` is a type alias for `ZProvider[RIn, Throwable, ROut]`, which represents a provider that requires `RIn` as its input, it may fail with `Throwable` value, or returns `ROut` as its output.

```scala
type RProvider[-RIn, +ROut]  = ZProvider[RIn, Throwable, ROut]
```