---
id: live
title: "Live"
---

The `Live` trait provides access to the _live_ environment from within the test environment for effects such as printing test results to the console or timing out tests where it is necessary to access the real environment.

## Providing Live Environment 

### To the Entire Effect

The easiest way to access the _live_ environment is to use the `live` method with an effect that would otherwise access the test environment.

For example, within the test environment, when we use the `Clock.currentTime` function, it will not execute the live version of the `Clock` service, rather, it will run the test version of the `Clock`, which is instantiated with zero-state by default. Thus, the `Clock.currentTime` will return a `0L` value:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.Assertion._
import java.util.concurrent.TimeUnit

test("running clock methods in a test environment") {
  assertZIO(Clock.currentTime(TimeUnit.MILLISECONDS))(equalTo(0L)) 
}
```

To run the `Clock.currentTime` within the live environment, we should use the `Live.live` method:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.Assertion._

import java.util.concurrent.TimeUnit

test("live can access real environment") {
  for {
    live <- Live.live(Clock.currentTime(TimeUnit.MILLISECONDS))
  } yield assertTrue(live != 0L)
}
```

### To the Part of an Effect

The `withLive` method can be used to apply a transformation to an effect with the live environment while ensuring that the effect itself still runs with the test environment.

For example, assume we have a long-running task that is required to run within the test environment, and we want to timeout it before the assertion. To do this, we should run the timeout operation within the live environment:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.Assertion._

val longRunningSUT =
  ZIO.attemptBlockingInterrupt {
    // ... 
    Thread.sleep(10000) // simulating a long-running blocking operation
    // ...
  }
  
test("withLive provides real environment to a single part of an effect") {
  assertZIO(Live.withLive(longRunningSUT)(_.timeout(3.seconds)))(anything)
}
```
