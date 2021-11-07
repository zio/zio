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
  
## Dependencies on Other Services

Just like the `ZIO` data type, the `Spec` requires an environment of type `R`. When we write tests, we might need to access a service through the environment. It can be a combination of the standard services such a `Clock`, `Console`, `Random` and `System` or test services like `TestClock`, `TestConsole`, `TestRandom`, and `TestSystem`, or any user-defined services.

### Using Standard Test Services

All standard test services are located at the `zio.test.environment` package. They are test implementation of standard ZIO services. The use of these test services enables us to test functionality that depends on printing to or reading from a console, randomness, timings, and, also the system properties.

Let's see how we can test the `sayHello` function, which uses the `Console` service:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.Assertion._
import zio.test.environment._

import java.io.IOException

def sayHello: ZIO[Has[Console], IOException, Unit] =
  Console.printLine("Hello, World!")

suite("HelloWorldSpec")(
  test("sayHello correctly displays output") {
    for {
      _      <- sayHello
      output <- TestConsole.output
    } yield assert(output)(equalTo(Vector("Hello, World!\n")))
  }
)
```

There is a separate section in the documentation pages that covers [all built-in test services](./environment/index.md).

### Providing Layers

By using `Spec#provideLayer`, `Spec#provideSomeLayer`, or `Spec#provideCustomLayer`, a test or suite of tests can be provided with any dependencies in a similar way to how a ZIO data type can. 

### Sharing Layers Within a Suite

The `Spec` has a very nice mechanism to share layers within all tests in a suite. So instead of acquiring and releasing dependencies for each test, we can share the layer within all tests. The test framework acquires that layer for once and shares that between all tests. When the execution of all tests is finished, that layer will be released.

In the following example, we share the Kafka layer within all tests in the suite:

```scala mdoc:invisible
import zio.test.{test, _}
import zio.{Chunk, _}

case class Row(key: String, value: String)

trait Kafka {
  def consume(topic: String): Task[Chunk[Row]]

  def produce(topic: String, key: String, value: String): Task[Unit]
}

object Kafka {
  def consume(topic: String) =
    ZIO.serviceWith[Kafka](_.consume(topic))

  def produce(topic: String, key: String, value: String) =
    ZIO.serviceWith[Kafka](_.produce(topic, key, value))
}

case class EmbeddedKafka() extends Kafka {
  override def consume(topic: String): Task[Chunk[Row]] =
    ZIO.succeed(Chunk.empty)

  override def produce(topic: String, key: String, value: String): Task[Unit] =
    Task.unit
}

object EmbeddedKafka {
  val layer = (EmbeddedKafka.apply _).toLayer[Kafka]
}
```

```scala mdoc:compile-only
suite("a test suite with shared kafka layer")(
  test("producing an element to the kafka service") {
    assertM(Kafka.produce(
      topic = "testTopic",
      key = "key1",
      value = "value1")
    )(Assertion.anything)
  },
  test("consuming elements from the kafka service") {
    assertM(Kafka.consume(topic = "testTopic"))(Assertion.anything)
  }
).provideCustomLayerShared(EmbeddedKafka.layer)
```

## Operations

In ZIO Test, specs are just values like other data types in ZIO. So we can filter, map or manipulate these data types. In this section, we are going to learn some of the most important operations on the `Spec` data type:

### Test Aspects

We can think of a test aspect as a polymorphic function from one test to another test. We use them to change existing tests or even entire suites or specs that we have already created.

Test aspects are applied to a test or suite using the `@@` operator:

```scala
test("a single test") {
  ...
} @@ testAspect

suite("suite of multiple tests") {
  ...
} @@ testAspect
```

The great thing about test aspects is that they are very composable. So we chain them one after another. We can even have test aspects that modify other test aspects.

So let's say we have a challenge that we need to run a test, and we want to make sure there is no flaky on the JVM, and then we want to make sure it doesn't take more than 60 seconds:

```scala
test @@ jvm(nonFlaky) @@ timeout(60.seconds)
```

This is an example of a test suite showing the use of aspects to modify test behavior:

```scala mdoc:compile-only
import zio.{test => _, _}
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object MySpec extends DefaultRunnableSpec {
  def spec = suite("A Suite")(
    test("A passing test") {
      assert(true)(isTrue)
    },
    test("A passing test run for JVM only") {
      assert(true)(isTrue)
    } @@ jvmOnly, // @@ jvmOnly only runs tests on the JVM
    test("A passing test run for JS only") {
      assert(true)(isTrue)
    } @@ jsOnly, // @@ jsOnly only runs tests on Scala.js
    test("A passing test with a timeout") {
      assert(true)(isTrue)
    } @@ timeout(10.nanos), // @@ timeout will fail a test that doesn't pass within the specified time
    test("A failing test... that passes") {
      assert(true)(isFalse)
    } @@ failing, //@@ failing turns a failing test into a passing test
    test("A ignored test") {
      assert(false)(isTrue)
    } @@ ignore, //@@ ignore marks test as ignored
    test("A flaky test that only works on the JVM and sometimes fails; let's compose some aspects!") {
      assert(false)(isTrue)
    } @@ jvmOnly           // only run on the JVM
      @@ eventually        // @@ eventually retries a test indefinitely until it succeeds
      @@ timeout(20.nanos) // it's a good idea to compose `eventually` with `timeout`, or the test may never end
  ) @@ timeout(60.seconds) // apply a timeout to the whole suite
}
```

#### Timing Out with Safe Interruption

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

#### Non Flaky

Whenever we deal with concurrency issues or race conditions, we should ensure that our tests pass consistently. The `nonFlaky` is a test aspect to do that. 

It will run a test several times, by default 100 times, and if all those times pass, it will pass, otherwise, it will fail:

```scala mdoc:silent:nest
test("random value is always greater than zero") {
  for {
    random <- Random.nextIntBounded(100)
  } yield assert(random)(Assertion.isGreaterThan(0))
} @@ nonFlaky
```

#### Platform-specific Tests

Sometimes we have platform-specific tests. Instead of creating separate sources for each platform to test those tests, we can use a proper aspect to run those tests on a specific platform. 

To do that we can use `jvmOnly`, `jsOnly` or `nativeOnly` aspects:

```scala mdoc:silent:nest
import zio.test.environment.live

test("Java virtual machine name can be accessed") {
  for {
    vm <- live(System.property("java.vm.name"))
  } yield
    assert(vm)(Assertion.isSome(Assertion.containsString("VM")))
} @@ jvmOnly
```

#### Tagging

ZIO Test allows us to define some arbitrary tags. By labeling tests with one or more tags, we can categorize them, and then, when running tests, we can filter tests according to their tags.

Let's tag all slow tests and run them separately:

```scala mdoc:invisible
val longRunningAssertion        = assertTrue(true)
val anotherLongRunningAssertion = assertTrue(true)
```

```scala mdoc:compile-only
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

#### Conditional Aspects

When we apply a conditional aspect, it will run the spec only if the specified predicate is satisfied:

- **`ifEnv`** — An aspect that only runs a test if the specified environment variable satisfies the specified assertion.
- **`ifEnvSet`** — An aspect that only runs a test if the specified environment variable is set.
- **`ifProp`** An aspect that only runs a test if the specified Java property satisfies the specified assertion.
- **`ifPropSet`** — An aspect that only runs a test if the specified Java property is set.

```scala mdoc:compile-only
test("a test that will run if the product is deployed in the testing environment") {
  ???
} @@ ifEnv("ENV")(_ == "testing")

test("a test that will run if the java.io.tmpdir property is available") {
  ???
} @@ ifEnvSet("java.io.tmpdir")
```
