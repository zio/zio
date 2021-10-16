---
id: index
title: "Introduction"
---

**ZIO Test** is a zero dependency testing library that makes it easy to test effectual programs. In **ZIO Test**, all tests are immutable values and tests are tightly integrated with ZIO, so testing effectual programs is as natural as testing pure ones. 

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

## Our Fist Lines of ZIO Test

The fastest way to start writing tests is to extend `DefaultRunnableSpec`, which creates a Spec that is also an executable program you can run from within SBT using `test:run` or by using `test` with the SBT test runner.

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
