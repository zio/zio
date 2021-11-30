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

import java.io.IOException

def sayHello: ZIO[Console, IOException, Unit] =
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
).provideCustomShared(EmbeddedKafka.layer)
```

```scala mdoc:reset
```