---
id: live
title: "Live"
---

The `Live` trait provides access to the _live_ environment from within the test environment for effects such as printing test results to the console or timing out tests where it is necessary to access the real environment.

The easiest way to access the _live_ environment is to use the `live` method with an effect that would otherwise access the test environment: 

```scala mdoc:compile-only
import zio.Clock
import zio.test.environment._

val realTime = live(Clock.nanoTime)
```

The `withLive` method can be used to apply a transformation to an effect with the live environment while ensuring that the effect itself still runs with the test environment, for example to time out a test. Both of these methods are re-exported in the `environment` package for easy availability. 