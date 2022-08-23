---
id: urlayer
title: "URLayer"
---

`URLayer[-RIn, +ROut]` is a type alias for `ZLayer[RIn, Nothing, ROut]`, which represents a layer that requires `RIn` as its input, it can't fail, and returns `ROut` as its output.

```scala
type URLayer[-RIn, +ROut] = ZLayer[RIn, Nothing, ROut]
```
