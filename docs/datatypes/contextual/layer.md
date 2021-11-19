---
id: layer
title: "Layer"
---

`Layer[+E, +ROut]` is a type alias for `ZLayer[Any, E, ROut]`, which represents a layer that doesn't require any services, it may fail with an error type of `E`, and returns `ROut` as its output.

```scala
type Layer[+E, +ROut] = ZLayer[Any, E, ROut]
```
