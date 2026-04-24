---
id: rmanaged
title: "RManaged"
---

`RManaged[R, A]` is a type alias for `ZManaged[R, Throwable, A]`, which represents a managed resource that requires an `R`, and may fail with a `Throwable` value, or succeed with an `A`.


The `RManaged` type alias is defined as follows:

```scala
type RManaged[-R, +A] = ZManaged[R, Throwable, A]
```
