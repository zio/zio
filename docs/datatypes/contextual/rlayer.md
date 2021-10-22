---
id: rdeps
title: "RDeps"
---

`RDeps[-RIn, +ROut]` is a type alias for `ZDeps[RIn, Throwable, ROut]`, which represents a set of dependencies that requires `RIn` as its input, it may fail with `Throwable` value, or returns `ROut` as its output.

```scala
type RDeps[-RIn, +ROut]  = ZDeps[RIn, Throwable, ROut]
```