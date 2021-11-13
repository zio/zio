---
id: taskservicebuilder
title: "TaskServiceBuilder"
---

`TaskServiceBuilder[+ROut]` is a type alias for `ZServiceBuilder[Any, Throwable, ROut]`, which represents a service builder that doesn't require any services as its input, it may fail with `Throwable` value, and returns `ROut` as its output.

```scala
type TaskServiceBuilder[+ROut] = ZServiceBuilder[Any, Throwable, ROut]
```
