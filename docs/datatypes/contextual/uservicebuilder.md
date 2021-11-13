---
id: uservicebuilder
title: "UServiceBuilder"
---

`UServiceBuilder[+ROut]` is a type alias for `ZServiceBuilder[Any, Nothing, ROut]`, which represents a service builder that doesn't require any services as its input, it can't fail, and returns `ROut` as its output.

```scala
type UServiceBuilder[+ROut] = ZServiceBuilder[Any, Nothing, ROut]
```
