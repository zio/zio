---
id: test-aspect
title: "TestAspect"
---

A `TestAspect` is an aspect that can be weaved into specs. We can think of an aspect as a polymorphic function, capable of transforming one test into another, possibly enlarging the environment or error type.

## Timing Out with Safe Interruption

We can easily time out a long-running test:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

test("effects can be safely interrupted") {
  for {
    r <- ZIO.attempt(println("Still going ...")).forever
  } yield assert(r)(Assertion.isSuccess)
} @@ timeout(1.second)
```

By applying a `timeout(1.second)` test aspect, this will work with ZIO's interruption mechanism. So when we run this test, you can see a tone of print lines, and after a second, the `timeout` aspect will interrupt that.

## Non Flaky

Whenever we deal with concurrency issues or race conditions, we should ensure that our tests pass consistently. The `nonFlaky` is a test aspect to do that.

It will run a test several times, by default 100 times, and if all those times pass, it will pass, otherwise, it will fail:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

test("random value is always greater than zero") {
  for {
    random <- Random.nextIntBounded(100)
  } yield assert(random)(Assertion.isGreaterThan(0))
} @@ nonFlaky
```

## Platform-specific Tests

Sometimes we have platform-specific tests. Instead of creating separate sources for each platform to test those tests, we can use a proper aspect to run those tests on a specific platform.

To do that we can use `jvmOnly`, `jsOnly` or `nativeOnly` aspects:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._
import zio.test.environment.live

test("Java virtual machine name can be accessed") {
  for {
    vm <- live(System.property("java.vm.name"))
  } yield
    assert(vm)(Assertion.isSome(Assertion.containsString("VM")))
} @@ jvmOnly
```

## Tagging

ZIO Test allows us to define some arbitrary tags. By labeling tests with one or more tags, we can categorize them, and then, when running tests, we can filter tests according to their tags.

Let's tag all slow tests and run them separately:

```scala mdoc:invisible
import zio.test.{test, _}

val longRunningAssertion        = assertTrue(true)
val anotherLongRunningAssertion = assertTrue(true)
```

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

object TaggedSpecsExample extends DefaultRunnableSpec {
  def spec =
    suite("a suite containing tagged tests")(
      test("a slow test") {
        longRunningAssertion
      } @@ tag("slow", "math"),
      test("a simple test") {
        assertTrue(1 + 1 == 2)
      } @@ tag("math"),
      test("another slow test") {
        anotherLongRunningAssertion
      } @@ tag("slow")
    )
}
```

By adding the `-tags slow` argument to the command line, we will only run the slow tests:

```
sbt> test:runMain TaggedSpecsExample -tags slow
```

The output would be:

```
[info] running (fork) TaggedSpecsExample -tags slow
[info] + a suite containing tagged tests - tagged: "slow", "math"
[info]   + a slow test - tagged: "slow", "math"
[info]   + another slow test - tagged: "slow"
[info] Ran 2 tests in 162 ms: 2 succeeded, 0 ignored, 0 failed
[success] Total time: 1 s, completed Nov 2, 2021, 12:36:36 PM
```

## Conditional Aspects

When we apply a conditional aspect, it will run the spec only if the specified predicate is satisfied:

- **`ifEnv`** — An aspect that only runs a test if the specified environment variable satisfies the specified assertion.
- **`ifEnvSet`** — An aspect that only runs a test if the specified environment variable is set.
- **`ifProp`** An aspect that only runs a test if the specified Java property satisfies the specified assertion.
- **`ifPropSet`** — An aspect that only runs a test if the specified Java property is set.

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

test("a test that will run if the product is deployed in the testing environment") {
  ???
} @@ ifEnv("ENV")(_ == "testing")

test("a test that will run if the java.io.tmpdir property is available") {
  ???
} @@ ifEnvSet("java.io.tmpdir")
```
