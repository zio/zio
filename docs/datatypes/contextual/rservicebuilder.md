---
id: rservicebuilder
title: "RServiceBuilder"
---

`RServiceBuilder[-RIn, +ROut]` is a type alias for `ZServiceBuilder[RIn, Throwable, ROut]`, which represents a service builder that requires `RIn` as its input, it may fail with `Throwable` value, or returns `ROut` as its output.

```scala
type RServiceBuilder[-RIn, +ROut]  = ZServiceBuilder[RIn, Throwable, ROut]
```