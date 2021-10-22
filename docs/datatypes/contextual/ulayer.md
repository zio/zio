---
id: undeps
title: "UDeps"
---

`UDeps[+ROut]` is a type alias for `ZDeps[Any, Nothing, ROut]`, which represents a set of dependencies that doesn't require any services as its input, it can't fail, and returns `ROut` as its output.

```scala
type UDeps[+ROut] = ZDeps[Any, Nothing, ROut]
```
