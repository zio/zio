---
id: test-hierarchies-and-organization
title: "Test Hierarchies and Organization"
---

A `Spec[R, E]` is the backbone of ZIO Test. All specs require an environment of type `R` and may potentially fail with an error of type `E`.

We can think of a spec as just a collection of tests. It is essentially a recursive data structure where every spec is just one individual test or a suite that itself can have multiple specs inside that each could be tests or sub suites. We can go down as far as we want in a recursive tree-like data structure.

## Test: A Single Spec

The `test` constructor creates one single spec:

```scala mdoc:silent:nest
import zio.test._

val mySpec = test("true is true") {
  assertTrue(true)
}
```

Real tests that run some logic and return testing result are created mostly with `test` function. It expects two arguments, first one will be the label of test which will be used for visual reporting back to the user, and an assertion which contains some testable logic specified about a target under the test.

## Suite: Collection of Multiple Specs

The `suite` creates a suite which contains other specs (tests or suites):

```scala mdoc:compile-only
import zio.test._

val mySuite =
  suite("A suite containing multiple tests")(
    test("the first test") {
      assertTrue(1 + 1 == 2)
    },
    test("the second test") {
      assertTrue(2 * 2 == 4)
    }
  )
```

  Suites can contain other suites. We can have multiple suites and one big suite that will aggregate them all:

```scala mdoc:compile-only
import zio.test._

suite("int and string")(
  suite("int suite")(
    test("minus")(assertTrue(2 - 1 == 1)),
    test("plus")(assertTrue(1 + 1 == 2))
  ),
  suite("string suite")(
    test("concat")(assertTrue("a" + "b" == "ab")),
    test("length")(assertTrue("abc".length == 3))
  )
)
```
