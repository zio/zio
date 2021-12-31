---
id: clock
title: MockClock
---

`MockClock` is a built-in mock version of the [`Clock`](../../contextual/services/clock.md) service. 

Here is an example of mocking `Clock.nanoTime` capability:

```scala mdoc:compile-only
import zio._
import zio.mock._
import zio.mock.Expectation._
import zio.test.{test, _}

test("calling mocked nanoTime should return expected time") {
  val app = Clock.nanoTime
  val env = MockClock.NanoTime(value(1000L))
  val out = app.provideLayer(env)
  out.map(r => assertTrue(r == 1000L))
}
```