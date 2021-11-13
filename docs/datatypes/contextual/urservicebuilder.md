---
id: urservicebuilder
title: "URServiceBuilder"
---

`URServiceBuilder[-RIn, +ROut]` is a type alias for `ZServiceBuilder[RIn, Nothing, ROut]`, which represents a service builder that requires `RIn` as its input, it can't fail, and returns `ROut` as its output.

```scala
type URServiceBuilder[-RIn, +ROut] = ZServiceBuilder[RIn, Nothing, ROut]
```
