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
  
## Operations

In ZIO Test, specs are just values like other data types in ZIO. So we can filter, map or manipulate these data types. In this section, we are going to learn some of the most important operations on the `Spec` data type:

### Test Aspects

We can think of a test aspect as a polymorphic function from one test to another test. They are used to change existing tests or even entire suites that you have already created.

Test aspects are applied to a test or suite using the `@@` operator:

```scala
test("a single test") {
  ...
} @@ testAspect

suite("suite of multiple tests") {
  ...
} @@ testAspect
```

#### Timing Out

We can easily time out a long-running test:

```scala mdoc:silent:nest
import zio._
import zio.test._
import zio.test.test
import zio.test.TestAspect._

test("effects can be safely interrupted") {
  for {
    r <- ZIO.attempt(println("Still going ...")).forever
  } yield assert(r)(Assertion.isSuccess)
} @@ timeout(1.second)
```

By applying a `timeout(1.second)` test aspect, this will work with ZIO's interruption mechanism. So when we run this test, you can see a tone of print lines, and after a second, the `timeout` aspect will interrupt that.

### Non Flaky

Whenever we deal with concurrency issues or race conditions, we should ensure that our tests pass consistently. The `nonFlaky` is a test aspect to do that. 

It will run a test several times, by default 100 times, and if all those times pass, it will pass, otherwise, it will fail:

```scala mdoc:silent:nest
test("random value is always greater than zero") {
  for {
    random <- Random.nextIntBounded(100)
  } yield assert(random)(Assertion.isGreaterThan(0))
} @@ nonFlaky
```
