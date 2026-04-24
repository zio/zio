---
id: munit-zio
title: "MUnit ZIO"
---

[MUnit ZIO](https://github.com/poslegm/munit-zio) is an integration library between MUnit and ZIO.

## Introduction

[MUnit](https://scalameta.org/munit/) is a Scala testing library that is implemented as a JUnit runner. It has _actionable errors_, so the test reports are colorfully pretty-printed, stack traces are highlighted, error messages are pointed to the source code location where the failure happened.

The MUnit ZIO enables us to write tests that return `ZIO` values without needing to call any unsafe methods (e.g. `Runtime#unsafeRun`).

## Installation

In order to use this library, we need to add the following lines in our `build.sbt` file:

```scala
libraryDependencies += "org.scalameta" %% "munit" % "0.7.27" % Test
libraryDependencies += "com.github.poslegm" %% "munit-zio" % "0.0.2" % Test
```

If we are using a version of sbt lower than 1.5.0, we will also need to add:

```scala
testFrameworks += new TestFramework("munit.Framework")
```

## Example

Here is a simple MUnit spec that is integrated with the `ZIO` effect:

```scala
import munit._
import zio._

class SimpleZIOSpec extends ZSuite {
  testZ("1 + 1 = 2") {
    for {
      a <- ZIO(1)
      b <- ZIO(1)
    }
    yield assertEquals(a + b, 2)
  }
}
```
