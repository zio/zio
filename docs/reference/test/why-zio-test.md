---
id: why-zio-test
title: "Why ZIO Test?"
---

In this section, we will discuss important features of the ZIO Test which help us to test our effectual code easily.

## Test Environment

The library includes built-in _testable versions_ of all the standard ZIO services (`Clock`, `Console`, `System`, and `Random`). For example, the `TestClock` has some timing actions that enables us to control the passage of time. So instead of waiting for timeouts and passage of time, we can adjust the time in our test:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.Assertion._

test("timeout") {
  for {
    fiber  <- ZIO.sleep(5.minutes).timeout(1.minute).fork
    _      <- TestClock.adjust(1.minute)
    result <- fiber.join
  } yield assertTrue(result.isEmpty)
}
```

In this example, to test the timeout function without waiting for one minute, we passed the time for one minute using the `adjust` operation. Sometimes, we may want to run these kinds of tests with the `nonFlaky` operator, which runs the test one hundred different times.

The `TestRandom` service has some extra functionality that enables us to test those functionalities with randomness. We can provide seed number to the `TestRandom`, and then we can have an exact expectation of the random function results.

Each of these services, comes with a bunch of functionality that makes it very easy to test effects.

Whenever we need to access the _live_ environment, we can use the `live` method in the `test` package or test annotations like `withLiveConsole`.

## Resource Management

We may need to set up and tear down some fixtures in our test code before and after running tests. ZIO Test manages this seamlessly for us. So, instead of providing `before`/`after`, `beforeAll`/`afterAll` hooks which are not composable, we can provide a `ZLayer` to each test or a test suite. The ZIO test takes care of acquiring, utilizing, and releasing that layer.

For example, if we have a Kafka layer, we can provide it to one test, or we can provide it to an entire suite of tests, just like the example below:

```scala mdoc:invisible
import zio._
import zio.test.{test, _}
import zio.test.Assertion._

val kafkaLayer: ZLayer[Any, Nothing, Int] = ZLayer.succeed(1)
val test1: Spec[Int, Nothing] = test("kafkatest")(assertTrue(true))
val test2: Spec[Int, Nothing] = test("kafkatest")(assertTrue(true))
val test3: Spec[Int, Nothing] = test("kafkatest")(assertTrue(true))
```

```scala mdoc:compile-only
suite("a test suite with shared kafka layer")(test1, test2, test3)
  .provideCustomLayerShared(kafkaLayer)
```

This layer going to get acquired once, then we have access to that service within all these three tests within the suite and then it is guaranteed to be released at the end of our tests.

So in ZIO Test, we have nice resource management which enables us to have tests where:
- They are resource safe
- Resources can be acquired and released per test or across a suite of tests
- Fully composable

## Property Based Testing

Support for property based testing is included out-of-the-box through the `check` method and its variants and the `Gen` and `Sample` classes. For example, here is how we could write a property to test that integer addition is associative.

```scala mdoc:compile-only
import zio.test._

val associativity =
  check(Gen.int, Gen.int, Gen.int) { (x, y, z) =>
    assertTrue(((x + y) + z) == (x + (y + z)))
  }
```

If a property fails, the failure will be automatically shrunk to the smallest failing cases to make it easier for us to diagnose the problem. And shrinking is integrated with the generation of random variables, so we are guaranteed that any shrunk counter example will meet the conditions of our original generator.

ZIO Test also supports automatic derivation of generators using the ZIO Test Magnolia module:

```scala mdoc:compile-only
import zio._
import zio.test._
import zio.test.magnolia._

case class Point(x: Double, y: Double)

val genPoint: Gen[Any, Point] = DeriveGen[Point]
 
sealed trait Color
case object Red   extends Color
case object Green extends Color
case object Blue  extends Color
 
val genColor: Gen[Any, Color] = DeriveGen[Color]
```

## Test Reporting

When tests do fail, it is easy to see what went wrong because the test reporter will show us the entire assertion that failed and the specific part of the assertion that failed. To facilitate this, a variety of assertion combinators are included in the `Assertion` class.

## Test Aspects

Test aspects are powerful tools for modifying behavior of individual tests or even entire suites that we have already written. Convenient syntax `@@` is provided for applying test aspects.

For example, we can apply a timeout to a test by using `test @@ timeout(60.seconds)` or only run a test on JavaScript by using `test @@ jsOnly`.

Test aspects are _highly composable_, so we can combine multiple test aspects together:

```scala mdoc:compile-only
import zio.test._
import zio.test.TestAspect._

test("another zio test")(???) @@ timeout(60.seconds) @@ jvmOnly
```

## Zero Dependencies

As a library with zero third party dependencies, this project is available on the JVM, ScalaJS, Dotty, and will be available on Scala Native in the near future. So we can write our tests once and make sure that our code works correctly across all platforms that we support.
