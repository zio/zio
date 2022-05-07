---
id: umanaged
title: "UManaged"
---

`UManaged[A]` is a type alias for `ZManaged[Any, Nothing, A]`, which represents an **unexceptional** managed resource that doesn't require any specific environment, and cannot fail, but can succeed with an `A`.

The `UMManaged` type alias is defined as follows:

```scala mdoc:compile-only
import zio.ZManaged

type UManaged[+A] = ZManaged[Any, Nothing, A]
```
