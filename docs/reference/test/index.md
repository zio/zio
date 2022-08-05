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

val random = Unsafe.unsafe { implicit unsafe =>
  Runtime.default.unsafe.run(
    Random.nextIntBounded(10)
  ).getOrThrowFiberFailure()
}

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

