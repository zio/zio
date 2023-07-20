---
id: tasklayer
title: "TaskLayer"
---

`TaskLayer[+ROut]` is a type alias for `ZLayer[Any, Throwable, ROut]`, which represents a layer that doesn't require any services as its input, it may fail with `Throwable` value, and returns `ROut` as its output.

```scala
type TaskLayer[+ROut] = ZLayer[Any, Throwable, ROut]
```
