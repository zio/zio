---
id: servicebuilder
title: "ServiceBuilder"
---

`ServiceBuilder[+E, +ROut]` is a type alias for `ZServiceBuilder[Any, E, ROut]`, which represents a service builder that doesn't require any services, it may fail with an error type of `E`, and returns `ROut` as its output.

```scala
type ServiceBuilder[+E, +ROut] = ZServiceBuilder[Any, E, ROut]
```
