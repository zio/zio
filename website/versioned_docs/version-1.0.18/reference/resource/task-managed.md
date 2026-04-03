---
id: task-managed
title: "TaskManaged"
---

`TaskManaged[A]` is a type alias for `ZManaged[Any, Throwable, A]`, which represents a managed resource that has no requirements, and may fail with a `Throwable` value, or succeed with an `A`.


The `TaskManaged` type alias is defined as follows:

```scala
type TaskManaged[+A] = ZManaged[Any, Throwable, A]
```
