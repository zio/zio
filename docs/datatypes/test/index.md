---
id: index
title: "Introduction"
---

**ZIO Test** is a zero dependency testing library that makes it easy to test effectual programs. In **ZIO Test**, all tests are immutable values and tests are tightly integrated with ZIO, so testing effectual programs is as natural as testing pure ones. 

## Motivation

We can easily assert ordinary values and data types to test them:

```scala mdoc:compile-only
import scala.Predef.assert

assert(1 + 2 == 2 + 1)
assert("Hi" == "H" + "i")

case class Point(x: Long, y: Long)
assert(Point(5L, 10L) == Point.apply(5L, 10L))
```

What about functional effects? Can we assert two effects using ordinary scala assertion to test whether they have the same functionality? As we know, a functional effect, like `ZIO`, describes a series of computations. Unfortunately, we can't assert functional effects without executing them. If we assert two `ZIO` effects, e.g. `assert(expectedEffect == actualEffect)`, the result says nothing about whether these two effects behave similarly and produce the same result or not. Instead, we should `unsafeRun` each one and assert their results.

Let's say we have a random generator effect, and we want to ensure that the output is bigger than zero, so we should `unsafeRun` the effect and assert the result:

```scala mdoc:invisible
import zio._
```

```scala mdoc:compile-only
import scala.Predef.assert

val random = Runtime.default.unsafeRun(
  Random.nextIntBounded(10).provideLayer(Random.live)
)

assert(random >= 0)
```

Testing effectful programs is difficult since we should use many `unsafeRun` methods. Also, we might need to make sure that the test is non-flaky. In these cases, running `unsafeRun` multiple times is not straightforward. We need a testing framework that treats effects as _first-class values_. So this is the primary motivation for creating the ZIO Test library.

## How ZIO Test was designed

We designed ZIO Test around the idea of _making tests first-class objects_. This means that tests (and other concepts, like assertions) become ordinary values that can be passed around, transformed, and composed.

This approach allows for greater flexibility compared to some other testing frameworks, where tests and additional logic around tests had to be put into callbacks so that framework could make use of them.

As a result, this approach is also better suited to other `ZIO` concepts like `Scope`, which can only be used within a scoped block of code. This also created a mismatch between `BeforeAll`, `AfterAll` callback-like methods when there were resources that should be opened and closed during test suite execution.

Another thing worth pointing out is that tests being values are also effects. Implications of this design are far-reaching:

1. First, the well-known problem of testing asynchronous value is gone. Whereas in other frameworks we have to somehow "run" our effects and at best wrap them in `scala.util.Future` because blocking would eliminate running on ScalaJS, ZIO Test expects us to create `ZIO` objects. There is no need for indirect transformations from one wrapping object to another.

2. Second, because our tests are ordinary `ZIO` values, we don't need to turn to a testing framework for things like retries, timeouts, and resource management. We can solve all those problems with the full richness of functions that `ZIO` exposes.

## Installation

In order to use ZIO Test, we need to add the required configuration in our SBT settings:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-test"          % zioVersion % "test",
  "dev.zio" %% "zio-test-sbt"      % zioVersion % "test",
  "dev.zio" %% "zio-test-magnolia" % zioVersion % "test" // optional
)
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
```

## Our First Lines of ZIO Test

The fastest way to start writing tests is to extend `ZIOSpecDefault`, which requires a `Spec`. `ZIOSpecDefault` is very similar in its logic of operations to `ZIOAppDefault`. Instead of providing one `ZIO` application at the end of the world, we provide a suite that can be a tree of other suites and tests. 

```scala mdoc:compile-only
import zio._
import zio.test._
import zio.test.Assertion._

import java.io.IOException

import HelloWorld._

object HelloWorld {
  def sayHello: ZIO[Any, IOException, Unit] =
    Console.printLine("Hello, World!")
}

object HelloWorldSpec extends ZIOSpecDefault {
  def spec = suite("HelloWorldSpec")(
    test("sayHello correctly displays output") {
      for {
        _      <- sayHello
        output <- TestConsole.output
      } yield assertTrue(output == Vector("Hello, World!\n"))
    }
  )
}
```

In the example above, our test involved the effect of printing to the console, but we didn't have to do anything differently in our test. Also note that the `helloWorld` method in the above program does not actually print a string to the console instead writes it to a buffer for testing.

## Running Tests

We can run ZIO Tests in two ways:

1. If we [added](#installation) `zio.test.sbt.ZTestFramework` to SBT's `testFrameworks`, our tests should be automatically picked up by SBT on invocation of `test`:

  ```bash
  sbt Test/test                      // run all tests
  sbt Test/testOnly HelloWorldSpec   // run a specific test
  ```

2. However, if we're not using SBT or have some other special needs, the `ZIOSpecDefault` has a `main` method which can be invoked directly or with SBTs `Test/run` or `Test/runMain` commands:

  ```bash
  sbt Test/run                       // prompt to choose which test to run
  sbt Test/runMain HelloWorldSpec    // run a specific test
  ```

## Why ZIO Test?

### Test Environment

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

Whenever we need to access the _live_ environment, we can use the `live` method in the `test` package or specify the live environment in the type signature like `Live[Console]`.

### Resource Management

We may need to set up and tear down some fixtures in our test code before and after running tests. ZIO Test manages this seamlessly for us. So, instead of providing `before`/`after`, `beforeAll`/`afterAll` hooks which are not composable, we can provide a `ZLayer` to each test or a test suite. The ZIO test takes care of acquiring, utilizing, and releasing that layer.

For example, if we have a Kafka layer, we can provide it to one test, or we can provide it to an entire suite of tests, just like the example below:

```scala mdoc:invisible
import zio._
import zio.test.{test, _}
import zio.test.Assertion._

val kafkaLayer: ZLayer[Any, Nothing, Int] = ZLayer.succeed(1)
val test1: ZSpec[Int, Nothing] = test("kafkatest")(assertTrue(true))
val test2: ZSpec[Int, Nothing] = test("kafkatest")(assertTrue(true))
val test3: ZSpec[Int, Nothing] = test("kafkatest")(assertTrue(true))
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

### Property Based Testing

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

val genPoint: Gen[Sized, Point] = DeriveGen[Point]
 
sealed trait Color
case object Red   extends Color
case object Green extends Color
case object Blue  extends Color
 
val genColor: Gen[Sized, Color] = DeriveGen[Color]
```

### Results Reporting

When tests do fail, it is easy to see what went wrong because the test reporter will show us the entire assertion that failed and the specific part of the assertion that failed. To facilitate this, a variety of assertion combinators are included in the `Assertion` class.

### Test Aspects

Test aspects are powerful tools for modifying behavior of individual tests or even entire suites that we have already written. Convenient syntax `@@` is provided for applying test aspects. 

For example, we can apply a timeout to a test by using `test @@ timeout(60.seconds)` or only run a test on JavaScript by using `test @@ jsOnly`. 

Test aspects are _highly composable_, so we can combine multiple test aspects together: 

```scala mdoc:compile-only
import zio.test._
import zio.test.TestAspect._

test("another zio test")(???) @@ timeout(60.seconds) @@ jvmOnly
```

### Zero Dependencies

As a library with zero third party dependencies, this project is available on the JVM, ScalaJS, Dotty, and will be available on Scala Native in the near future. So we can write our tests once and make sure that our code works correctly across all platforms that we support.

### JUnit integration

A custom JUnit runner is provided for running ZIO Test specs under other build tools (like Maven, Gradle, Bazel, etc.) and under IDEs.

To get the runner, we need to add the equivalent of following dependency definition under our build tool:

```scala
"dev.zio" %% "zio-test-junit" % zioVersion % "test"
```

To make our spec appear as a JUnit test to build tools and IDEs, we should convert it to a `class` (JUnit won't run scala objects) and annotate it with `@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])` or simply extend `zio.test.junit.JUnitRunnableSpec`.

See [ExampleSpecWithJUnit](https://github.com/zio/zio/blob/master/examples/jvm/src/test/scala/zio/examples/test/ExampleSpecWithJUnit.scala)

SBT (and thus Scala.JS) is not supported, as the JUnit Test Framework for SBT doesn't seem to support custom runners.
