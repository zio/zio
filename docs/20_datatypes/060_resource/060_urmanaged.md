---
id: urmanaged
title: "URManaged"
---

`URManaged[R, A]` is a type alias for `ZManaged[R, Nothing, A]`, which represents a managed resource that requires an `R`, and cannot fail, but can succeed with an `A`.

```scala mdoc:invisible
import zio.ZManaged
```

The `URManaged` type alias is defined as follows:

```scala mdoc:silent:nest
type URManaged[-R, +A] = ZManaged[R, Nothing, A]
```
