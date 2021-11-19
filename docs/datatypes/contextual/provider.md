---
id: provider
title: "Provider"
---

`Provider[+E, +ROut]` is a type alias for `ZProvider[Any, E, ROut]`, which represents a provider that doesn't require any services, it may fail with an error type of `E`, and returns `ROut` as its output.

```scala
type Provider[+E, +ROut] = ZProvider[Any, E, ROut]
```
