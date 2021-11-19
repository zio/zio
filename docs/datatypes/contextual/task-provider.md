---
id: taskprovider
title: "TaskProvider"
---

`TaskProvider[+ROut]` is a type alias for `ZProvider[Any, Throwable, ROut]`, which represents a provider that doesn't require any services as its input, it may fail with `Throwable` value, and returns `ROut` as its output.

```scala
type TaskProvider[+ROut] = ZProvider[Any, Throwable, ROut]
```
