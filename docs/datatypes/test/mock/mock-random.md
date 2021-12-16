---
id: random
title: MockRandom
---

`MockRandom` is a built-in mock version of the [`Random`](../../contextual/services/random.md) service. It mocks all the random service capabilities.

Here's how we can mock the `MockRandom.nextIntBounded` capability:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

test("expect call with input satisfying assertion and transforming it into output") {
  for {
    out <- Random.nextIntBounded(1).provideLayer(
      MockRandom.NextIntBounded(
        Assertion.equalTo(1),
        Expectation.valueF(_ + 41)
      )
    )
  } yield assertTrue(out == 42)
}
```
