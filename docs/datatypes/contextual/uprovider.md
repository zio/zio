---
id: uprovider
title: "UProvider"
---

`UProvider[+ROut]` is a type alias for `ZProvider[Any, Nothing, ROut]`, which represents a provider that doesn't require any services as its input, it can't fail, and returns `ROut` as its output.

```scala
type UProvider[+ROut] = ZProvider[Any, Nothing, ROut]
```
