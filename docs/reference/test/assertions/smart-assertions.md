---
id: smart-assertions
title: "Smart Assertions"
---

The smart assertion is a simple way to assert both _ordinary values_ and _ZIO effects_. It uses the `assertTrue` function, which uses macro under the hood.

## Asserting Ordinary Values

In the following example, we assert simple ordinary values using the `assertTrue` method:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("sum"){
  assertTrue(1 + 1 == 2)
}
```

We can assert multiple assertions inside a single `assertTrue`:

```scala
test("multiple assertions"){
  assertTrue(
    true,
    1 + 1 == 2,
    Some(1 + 1) == Some(2)
  )
}
```

## Asserting ZIO effects

The `assertTrue` method can also be used to assert ZIO effects:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("updating ref") {
  for {
    r <- Ref.make(0)
    _ <- r.update(_ + 1)
    v <- r.get
  } yield assertTrue(v == 1)
}
```

Using `assertTrue` with for-comprehension style, we can think of testing as these three steps:

1. **Set up the test** — In this section we should setup the system under test (e.g. `Ref.make(0)`).
2. **Running the test** — Then we run the test scenario according to the test specification. (e.g `ref.update(_ + 1)`)
3. **Making assertions about the test** - Finally, we should assert the result with the right expectations (e.g. `assertTrue(v == 1)`)
