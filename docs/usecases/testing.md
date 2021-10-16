---
id: usecases_testing
title:  "Testing"
---

**Test Environment**

The library also includes built-in test versions of all the standard ZIO environmental effects (`Clock`, `Console`, `System`, and `Random`), so when we tested our program above the `helloWorld` method didn't actually print a String to the console but instead wrote the String to a buffer that could access for testing purposes. If you ever do need to access the "live" environment just use the `live` method in the `mock` package or specify the live environment in your type signature like `Live[Console]`.

**Property Based Testing**

Support for property based testing is included out of the box through the `check` method and its variants and the `Gen` and `Sample` classes. For example, here is how we could write a property to test that integer addition is associative.

```scala mdoc:silent
val associativity =
  check(Gen.int, Gen.int, Gen.int) { (x, y, z) =>
    assert((x + y) + z)(equalTo(x + (y + z)))
  }
```

If a property fails, the failure will be automatically shrunk to the smallest failing cases to make it easier for you to diagnose the problem. And shrinking is integrated with the generation of random variables, so you are guaranteed that any shrunk counterexample will meet the conditions of your original generator.

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

**Results Reporting**

When tests do fail, it is easy to see what went wrong because the test reporter will show you the entire assertion that failed and the specific part of the assertion that failed. To facilitate this, a variety of assertion combinators are included in the `Assertion` class.

**Test Aspects**

Test aspects are powerful tools for modifying behavior of individual tests or even entire suites that you have already written. Convenient syntax `@@` is provided for applying test aspects. So for example you could apply a timeout to a test by using `test @@ timeout(60.seconds)` or only run a test on javascript by using `test @@ jsOnly`. Test aspects are highly composable so you can combine multiple test aspects together or apply them only to certain tests that match a predicate you specify.

**Zero Dependencies**

As a library with zero third party dependencies, this project is available on the JVM, ScalaJS, Dotty, and will be available on Scala Native in the near future. So you can write your tests once and make sure that your code works correctly across all platforms that you support.

**JUnit integration**

A custom JUnit runner is provided for running ZIO Test specs under other build tools (like Maven, Gradle, Bazel, etc.) and under IDEs.
To get the runner, add the equivalent of following dependency definition under your build tool:
  ```scala
      "dev.zio" %% "zio-test-junit"   % zioVersion % "test"
  ```

To make your spec appear as a JUnit test to build tools and IDEs, convert it to a `class` (JUnit won't run scala objects) and 
annotate it with `@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])` or simply extend `zio.test.junit.JUnitRunnableSpec`.
See [ExampleSpecWithJUnit](https://github.com/zio/zio/blob/master/examples/jvm/src/test/scala/zio/examples/test/ExampleSpecWithJUnit.scala)

SBT (and thus Scala.JS) is not supported, as the JUnit Test Framework for SBT doesn't seem to support custom runners.
