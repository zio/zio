---
id: urprovider
title: "URProvider"
---

`URProvider[-RIn, +ROut]` is a type alias for `ZProvider[RIn, Nothing, ROut]`, which represents a provider that requires `RIn` as its input, it can't fail, and returns `ROut` as its output.

```scala
type URProvider[-RIn, +ROut] = ZProvider[RIn, Nothing, ROut]
```
