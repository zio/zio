---
id: ulayer
title: "ULayer"
---

`ULayer[+ROut]` is a type alias for `ZLayer[Any, Nothing, ROut]`, which represents a layer that doesn't require any services as its input, it can't fail, and returns `ROut` as its output.

```scala
type ULayer[+ROut] = ZLayer[Any, Nothing, ROut]
```
