---
id: spec
title: "Spec"
---

A `Spec[R, E, T]` is the backbone of _ZIO Test_. All specs require an environment of type `R` and may potentially fail with an error of type `E`.

Every spec is either a test:

```scala mdoc:silent:nest
import zio.test._

val mySuite = test("true is true") {
  assert(true)(Assertion.isTrue)
}
```

Or it's a suite, which contains other specs:

```scala mdoc:silent:nest
val mySuite =
  suite("A suite containing multiple tests")(
    test("the first test") {
      assert(true)(Assertion.isTrue)
    },
    test("the second test") {
      assert(false)(Assertion.isFalse)
    }
  )
```

