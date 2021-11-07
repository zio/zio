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

```scala mcoc:compile-only
import scala.Predef.assert

val random = Runtime.default.unsafeRun(
  Random.nextIntBounded(10).provideLayer(Random.live)
)

assert(random >= 0)
```

Testing effectful programs is difficult since we should use many `unsafeRun` methods. Also, we might need to make sure that the test is non-flaky. In these cases, running `unsafeRun` multiple times is not straightforward. We need a testing framework that treats effects as _first-class values_. So this is the primary motivation for creating the ZIO Test library.

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

The fastest way to start writing tests is to extend `DefaultRunnableSpec`, which creates a Spec that is also an executable program we can run from within SBT using `test:run` or by using `test` with the SBT test runner.

```scala mdoc:silent
import zio._
import zio.test._
import zio.test.Assertion._

import java.io.IOException

import HelloWorld._

object HelloWorld {
  def sayHello: ZIO[Console, IOException, Unit] =
    Console.printLine("Hello, World!")
}

object HelloWorldSpec extends DefaultRunnableSpec {
  def spec = suite("HelloWorldSpec")(
    test("sayHello correctly displays output") {
      for {
        _      <- sayHello
        output <- TestConsole.output
      } yield assert(output)(equalTo(Vector("Hello, World!\n")))
    }
  )
}
```

In the example above, our test involved the effect of printing to the console, but we didn't have to do anything differently in our test.

When we tested our program above the `helloWorld` method didn't actually print a string to the console but instead wrote the string to a buffer that could access for testing purposes.

## Why ZIO Test?

### Test Environment

The library includes built-in _test versions_ of all the standard ZIO services (`Clock`, `Console`, `System`, and `Random`). Note that If we ever do need to access the "live" environment we can just use the `live` method in the `mock` package or specify the live environment in our type signature like `Live[Console]`.

### Resource Management

We may need to set up and tear down some fixtures in our test code before and after running tests. ZIO Test manages this seamlessly for us. So, instead of providing `before`/`after`, `beforeAll`/`afterAll` hooks which are not composable, we can provide a `ZLayer` to each test or a test suite. The ZIO test takes care of acquiring, utilizing, and releasing that layer.

For example, if we have a Kafka layer, we can provide it to one test, or we can provide it to an entire suite of tests, just like the example below:

```scala mdoc:invisible
import zio.test.{test, _}
import zio.test.Assertion._
val kafkaLayer: ZLayer[Any, Nothing, Has[Int]] = ZLayer.succeed(1)
val test1 = test("kafkatest")(assertTrue(true))
val test2 = test("kafkatest")(assertTrue(true))
val test3 = test("kafkatest")(assertTrue(true))
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
    assert((x + y) + z)(equalTo(x + (y + z)))
  }
```

If a property fails, the failure will be automatically shrunk to the smallest failing cases to make it easier for us to diagnose the problem. And shrinking is integrated with the generation of random variables, so we are guaranteed that any shrunk counter example will meet the conditions of our original generator.

ZIO Test also supports automatic derivation of generators using the ZIO Test Magnolia module:

```scala mdoc:silent:nest
import zio.Random
import zio.test.magnolia._

final case class Point(x: Double, y: Double)

val genPoint: Gen[Random with Sized, Point] = DeriveGen[Point]
 
sealed trait Color
case object Red   extends Color
case object Green extends Color
case object Blue  extends Color
 
val genColor: Gen[Random with Sized, Color] = DeriveGen[Color]
```

### Results Reporting

When tests do fail, it is easy to see what went wrong because the test reporter will show us the entire assertion that failed and the specific part of the assertion that failed. To facilitate this, a variety of assertion combinators are included in the `Assertion` class.

### Test Aspects

Test aspects are powerful tools for modifying behavior of individual tests or even entire suites that we have already written. Convenient syntax `@@` is provided for applying test aspects. For example, we could apply a timeout to a test by using `test @@ timeout(60.seconds)` or only run a test on JavaScript by using `test @@ jsOnly`. Test aspects are highly composable, so we can combine multiple test aspects together or apply them only to certain tests that match a predicate we specify.

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
