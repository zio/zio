---
id: configuring-tests 
title: "Configuring Tests"
---

To run cases, there are some [default configuration settings](../services/test-config.md) which are used by test runner, such as _repeats_, _retries_, _samples_ and _shrinks_. We can change these settings using test aspects:

## Number of Repeats

The `repeats(n: Int)` test aspect runs each test with the number of times to repeat tests to ensure they are stable set to the specified value:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

test("repeating a test") {
  ZIO.attempt("Repeating a test to ensure its stability")
    .debug
    .map(_ => assertTrue(true))
} @@ TestAspect.nonFlaky @@ TestAspect.repeats(5)
```

## Number of Retries

The `retries(n: Int)` test aspect runs each test with the number of times to retry flaky tests set to the specified value.

## Number of Samples

The `samples(n: Int)` test aspect runs each test with the number of sufficient samples to check for a random variable set to the specified value.

Let's change the number of default samples in the following example:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

test("customized number of samples") {
  for {
    ref <- Ref.make(0)
    _ <- check(Gen.int)(_ => assertZIO(ref.update(_ + 1))(Assertion.anything))
    value <- ref.get
  } yield assertTrue(value == 50)
} @@ TestAspect.samples(50)
```

## Maximum Number of Shrinks

The `shrinks(n: Int)` test aspect runs each test with the maximum number of shrinkings to minimize large failures set to the specified value.
