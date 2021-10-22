---
id: ulayer
title: "ULayer"
---

`ULayer[+ROut]` is a type alias for `ZDeps[Any, Nothing, ROut]`, which represents a layer that doesn't require any services as its input, it can't fail, and returns `ROut` as its output.

```scala
type ULayer[+ROut] = ZDeps[Any, Nothing, ROut]
```
