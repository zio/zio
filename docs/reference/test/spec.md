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

## Operations

In ZIO Test, specs are just values like other data types in ZIO. So we can filter, map or manipulate these data types. In this section, we are going to learn some of the most important operations on the `Spec` data type:

