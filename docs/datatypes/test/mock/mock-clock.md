---
id: clock
title: MockClock
---

`MockClock` is a built-in mock version of the [`Clock`](../../contextual/services/clock.md) service. 

Here is an example of mocking `Clock.nanoTime` capability:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._
import zio.test.mock.Expectation._

test("calling mocked nanoTime should return expected time") {
  val app = Clock.nanoTime
  val env = MockClock.NanoTime(value(1000L))
  val out = app.provideLayer(env)
  out.map(r => assertTrue(r == 1000L))
}
```