---
id: deps
title: "Deps"
---

`Deps[+E, +ROut]` is a type alias for `ZDeps[Any, E, ROut]`, which represents a layer that doesn't require any services, it may fail with an error type of `E`, and returns `ROut` as its output.

```scala
type Deps[+E, +ROut] = ZDeps[Any, E, ROut]
```
