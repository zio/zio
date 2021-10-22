---
id: urdeps
title: "URDeps"
---

`URDeps[-RIn, +ROut]` is a type alias for `ZDeps[RIn, Nothing, ROut]`, which represents a layer that requires `RIn` as its input, it can't fail, and returns `ROut` as its output.

```scala
type URDeps[-RIn, +ROut] = ZDeps[RIn, Nothing, ROut]
```
