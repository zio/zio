---
id: spec
title: "Spec"
---

## Constructors

## Dependencies on Other Services

Just like the `ZIO` data type, the `Spec` requires an environment of type `R`. When we write tests, we might need to access a service through the environment. It can be a combination of the standard services such a `Clock`, `Console`, `Random` and `System` or test services like `TestClock`, `TestConsole`, `TestRandom`, and `TestSystem`, or any user-defined services.

## Using Standard Test Services

All standard test services are located at the `zio.test` package. They are test implementation of standard ZIO services. The use of these test services enables us to test functionality that depends on printing to or reading from a console, randomness, timings, and, also the system properties.

Let's see how we can test the `sayHello` function, which uses the `Console` service:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.Assertion._

import java.io.IOException

def sayHello: ZIO[Any, IOException, Unit] =
  Console.printLine("Hello, World!")

suite("HelloWorldSpec")(
  test("sayHello correctly displays output") {
    for {
      _      <- sayHello
      output <- TestConsole.output
    } yield assertTrue(output == Vector("Hello, World!\n"))
  }
)
```

There is a separate section in the documentation pages that covers [all built-in test services](services/index.md).

## Providing Layers

By using `Spec#provideXYZLayer`, a test or suite of tests can be provided with any dependencies in a similar way to how a ZIO data type can.

## Sharing Layers Between Multiple Specs

ZIO Test has the ability to share layers between multiple specs. This is useful when we want to have some common services available for all tests. We have two ways to do this:

1. Using `Spec#provideXYZShared` methods, which is useful to share layers between multiple specs that are residing in the same file.
2. Using the `bootstrap` layer, which is useful to share layers between multiple specs that are residing in different files.

### Sharing Layers Within The Same File

The `Spec` data type has a very nice mechanism to share layers within all tests in a suite. So instead of acquiring and releasing dependencies for each test, we can share the layer within all tests. The test framework acquires that layer for once and shares that between all tests. When the execution of all tests is finished, that layer will be released. To share layers between multiple specs we can use `Spec#provideXYZShared` methods:

```scala
{
  test("test1")(???) +
    test("test2")(???)
}.provideZYZShared(sharedLayer)

suite("suite1")(
  test("test1")(???),
  test("test2")(???)
).provideZYZShared(sharedLayer)

suite("all suites")(
  suite("suite1")(
    test("test1")(???),
  ),
  suite("suite2")(
    test("test1")(???),
    test("test2")(???),
    test("test3")(???)
  )
).provideXYZShared(sharedLayer)
```

To demonstrate this, let's try an example. In this example, instead of using the built-in features of the ZIO Test, we want to write our own basic solution to count the number of times tests are executed.

First, we need a counter service like the below:

```scala mdoc:silent
import zio._

case class Counter(value: Ref[Int]) {
  def inc: UIO[Unit] = value.update(_ + 1)
  def get: UIO[Int] = value.get
}

object Counter {
  val layer =
    ZLayer.scoped(
      ZIO.acquireRelease(
        Ref.make(0).map(Counter(_)) <* ZIO.debug("Counter initialized!")
      )(c => c.get.debug("Number of tests executed"))
    )
  def inc = ZIO.service[Counter].flatMap(_.inc)
}
```

We use this service to count the number of times the tests are executed, by calling the `Counter.inc` operator after each test:

```scala mdoc:compile-only
import zio._
import zio.test._

object MySpecs extends ZIOSpecDefault {
  def spec = {
    suite("Spec1")(
      test("test1") {
        assertTrue(true)
      } @@ TestAspect.after(Counter.inc),
      test("test2") { assertTrue(true)
      } @@ TestAspect.after(Counter.inc)
    ) +
      suite("Spec2") {
        test("test1") {
          assertTrue(true)
        } @@ TestAspect.after(Counter.inc)
      }
  }.provideShared(Counter.layer)
}
```

If we execute all tests, we will see an output like this:

```
Counter initialized!
+ Spec1
  + test2
  + test1
+ Spec2
  + test1
Number of tests executed: 3
3 tests passed. 0 tests failed. 0 tests ignored.
```

In the above example, the `Counter.layer` is shared between all tests, and only acquired and released once.

### Sharing Layers Within Multiple Files

In the previous example, we used the `Spec#provideXYZShared` methods to share layers between multiple specs in one file. In most cases, when the number of tests and specs grows, this is not a good idea. We want a way to share layers between multiple specs in different files.

So in such situations, we can't use the previous pattern here, because specs are in entirely different files, and we don't want the boilerplate of creating a _master spec_ that references the other specs. ZIO has a solution to this problem. We can define the resource we want to share as part of the `bootstrap` layer of `ZIOApp`.

The `bootstrap` layer is responsible for creating and managing any services that our ZIO tests need. Using `boostrap` we can have one shared layer across multiple test specs.

Let's assume we have two specs in different files, and we want to share the `Counter` service between them. First, we need to create a base class that contains the shared bootstrap layer:

```scala mdoc:silent
import zio._
import zio.test._

abstract class SharedCounterSpec extends ZIOSpec[Counter] {
  override val bootstrap: ZLayer[Any, Nothing, Counter] = Counter.layer
}
```

Now it's time to create the specs. Each spec is extending the `SharedCounterSpec` class.

Spec1.scala:

```scala mdoc:compile-only
import zio._
import zio.test._

object Spec1 extends SharedCounterSpec {
  override def spec =
    test("test1") {
      assertTrue(true)
    } @@ TestAspect.after(Counter.inc)
}
```

Spec2.scala:

```scala mdoc:compile-only
import zio._
import zio.test._

object Spec2 extends SharedCounterSpec {
  override def spec: Spec[Scope with Counter, Any] =
    test("test2") {
      assertTrue(true)
    } @@ TestAspect.after(Counter.inc)
}
```

Now, when we run all specs (`sbt testOnly org.example.*`), we will see an output like this:

```
Counter initialized!
+ test1
+ test2
Number of tests executed: 2
```

The ZIO test runner will execute all specs with the shared bootstrap layer. This means that the `Counter` service will be created and managed only once, and will be shared between all specs.

```scala mdoc:invisible:reset

```

## Operations

In ZIO Test, specs are just values like other data types in ZIO. So we can filter, map or manipulate these data types. In this section, we are going to learn some of the most important operations on the `Spec` data type:

### Test Aspects

We can think of a test aspect as a polymorphic function from one test to another test. We use them to change existing tests or even entire suites or specs that we have already created.

Test aspects are applied to a test or suite using the `@@` operator:

```scala mdoc:invisible
val testAspect = zio.test.TestAspect.identity
```

```scala mdoc:compile-only
import zio.test.{test, _}

test("a single test") {
  ???
} @@ testAspect

suite("suite of multiple tests") {
  ???
} @@ testAspect
```

The great thing about test aspects is that they are very composable. So we chain them one after another. We can even have test aspects that modify other test aspects.

So let's say we have a challenge that we need to run a test, and we want to make sure there is no flaky on the JVM, and then we want to make sure it doesn't take more than 60 seconds:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

test("a test with two aspects composed together") {
  ???
} @@ jvm(nonFlaky) @@ timeout(60.seconds)
```

This is an example of a test suite showing the use of aspects to modify test behavior:

```scala mdoc:compile-only
import zio.test._
import zio.{test => _, _}
import zio.test.TestAspect._

object MySpec extends ZIOSpecDefault {
  def spec = suite("A Suite")(
    test("A passing test") {
      assertTrue(true)
    },
    test("A passing test run for JVM only") {
      assertTrue(true)
    } @@ jvmOnly, // @@ jvmOnly only runs tests on the JVM
    test("A passing test run for JS only") {
      assertTrue(true)
    } @@ jsOnly, // @@ jsOnly only runs tests on Scala.js
    test("A passing test with a timeout") {
      assertTrue(true)
    } @@ timeout(10.nanos), // @@ timeout will fail a test that doesn't pass within the specified time
    test("A failing test... that passes") {
      assertTrue(true)
    } @@ failing, //@@ failing turns a failing test into a passing test
    test("A ignored test") {
      assertTrue(false)
    } @@ ignore, //@@ ignore marks test as ignored
    test("A flaky test that only works on the JVM and sometimes fails; let's compose some aspects!") {
      assertTrue(false)
    } @@ jvmOnly           // only run on the JVM
      @@ eventually        // @@ eventually retries a test indefinitely until it succeeds
      @@ timeout(20.nanos) // it's a good idea to compose `eventually` with `timeout`, or the test may never end
  ) @@ timeout(60.seconds) // apply a timeout to the whole suite
}
```

## Smart Specs

The `suite` method creates a spec from a collection of specs. So what we can do is to provide it with a collection of specs:

```scala mdoc:compile-only
import zio.test._

object ExampleSpec extends ZIOSpecDefault {

  def spec =
    suite("some suite")(
      test("test 1") {
        val stuff = 1
        assertTrue(stuff == 1)
      },
      test("test 2") {
        val stuff = Some(1)
        assertTrue(stuff == Some(1))
      }
    )

}
```

But what if we wanted to have a suite of tests that work on a common value, e.g. the same `stuff`? ZIO provides the `suiteAll` method that helps us to share the same `stuff` between all tests:

```scala mdoc:compile-only
import zio.test._

object ExampleSpec extends ZIOSpecDefault {

  def spec =
    suiteAll("some suite") {

      val stuff = "hello"

      test("test 1") {
        assertTrue(stuff.startsWith("h"))
      }

      val stuff2 = 5

      test("test 2") {
        assertTrue(stuff.length == stuff2)
      }
    }

}
```
