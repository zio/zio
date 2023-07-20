---
id: urmanaged
title: "URManaged"
---

`URManaged[R, A]` is a type alias for `ZManaged[R, Nothing, A]`, which represents a managed resource that requires an `R`, and cannot fail, but can succeed with an `A`.


The `URManaged` type alias is defined as follows:

```scala
type URManaged[-R, +A] = ZManaged[R, Nothing, A]
```
