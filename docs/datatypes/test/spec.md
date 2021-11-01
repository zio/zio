---
id: spec
title: "Spec"
---

A `Spec[R, E, T]` is the backbone of _ZIO Test_. All specs require an environment of type `R` and may potentially fail with an error of type `E`.

## Constructors

We can think of a spec as just a collection of tests. It is essentially a recursive data structure where every spec is just one individual test or a suite that itself can have multiple specs inside that each could be tests or sub suites. We can go down as far as we want in a recursive tree-like data structure:

- **A Single Test** — The `test` constructor creates one single spec (test):

  ```scala mdoc:silent:nest
  import zio.test._
  
  val mySpec = test("true is true") {
    assert(true)(Assertion.isTrue)
  }
  ```

- **Collection of Multiple Tests** — The `suite` creates a suite which contains other specs (tests):

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
  