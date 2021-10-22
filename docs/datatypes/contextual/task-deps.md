---
id: taskdeps
title: "TaskDeps"
---

`TaskDeps[+ROut]` is a type alias for `ZDeps[Any, Throwable, ROut]`, which represents a set of dependencies that doesn't require any services as its input, it may fail with `Throwable` value, and returns `ROut` as its output.

```scala
type TaskDeps[+ROut] = ZDeps[Any, Throwable, ROut]
```
