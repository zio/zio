---
id: my
title: "Draft"
---

A `TestAspect` is an aspect that can be weaved into specs. We can think of an aspect as a polymorphic function, capable of transforming one test into another, possibly enlarging the environment or error type.

We can think of a test aspect as a Spec transformer. It takes one spec, transforms it, and produces another spec (`Spec => Spec`).

Test aspects encapsulate cross-cutting concerns and increase the modularity of our tests. So we can focus on the primary concerns of our tests and at the end of the day, we can apply required aspects to our tests.

We can apply each test aspect as an ordinary function to a spec. They are also compostable, so we can compose multiples of them.

For example, assume we have the following test:

```scala mdoc:compile-only
import zio.test._

test("test") {
  assertTrue(true)
}
```

We can pass this test to whatever test aspect we want. For example, to run this test only on the JVM and repeat it five times, we can write the test as below:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

repeat(Schedule.recurs(5))(
  jvmOnly(
    test("test") {
      assertTrue(true)
    }
  )
)
```

To compose the aspects, we have a very nice `@@` syntax, which helps us to write tests concisely. So the previous example can be written as follows:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

test("test") {
  assertTrue(true)
} @@ jvmOnly @@ repeat(Schedule.recurs(5))
```

When composing test aspects, **the order of test aspects is important**. So if we change the order, their behavior may change. For example, the following test will repeat the test 2 times:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

suite("suite")(
  test("A") {
    ZIO.debug("executing test")
      .map(_ => assertTrue(true))
  },
) @@ nonFlaky @@ repeats(2)
```

The output:

```
executing test
executing test
executing test
+ suite - repeated: 2
  + A - repeated: 2
Ran 1 test in 343 ms: 1 succeeded, 0 ignored, 0 failed
```

But the following test aspect repeats the test 100 times:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

suite("suite")(
  test("A") {
    ZIO.debug("executing test")
      .map(_ => assertTrue(true))
  },
) @@ repeats(2) @@ nonFlaky
```

The output:

```
executing test
executing test
executing test
executing test
executing test
...
executing test
+ suite - repeated: 100
  + A - repeated: 100
Ran 1 test in 478 ms: 1 succeeded, 0 ignored, 0 failed
```

## Test Configs

To run cases, there are some [default configuration settings](../services/test-config.md) which are used by test runner, such as _repeats_, _retries_, _samples_ and _shrinks_. We can change these settings using test aspects:

1. **`TestAspect.repeats(n: Int)`** — Runs each test with the number of times to repeat tests to ensure they are stable set to the specified value.

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

test("repeating a test") {
  ZIO.attempt("Repeating a test to ensure its stability")
    .debug
    .map(_ => assertTrue(true))
} @@ TestAspect.nonFlaky @@ TestAspect.repeats(5)
```

2. **`TestAspect.retries(n: Int)`** — Runs each test with the number of times to retry flaky tests set to the specified value.
3. **`TestAspect.samples(n: Int)`** — Runs each test with the number of sufficient samples to check for a random variable set to the specified value.
4. **`TestAspect.shrinks(n: Int)`** — Runs each test with the maximum number of shrinkings to minimize large failures set to the specified value.

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

## Timing Out

The `TestAspect.timeout` test aspect takes a duration and times out each test. If the test case runs longer than the time specified, it is immediately canceled and reported as a failure, with a message showing that the timeout was exceeded:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("effects can be safely interrupted") {
  for {
    _ <- ZIO.attempt(println("Still going ...")).forever
  } yield assertTrue(true)
} @@ TestAspect.timeout(1.second)
```

By applying a `timeout(1.second)` test aspect, this will work with ZIO's interruption mechanism. So when we run this test, you can see a tone of print lines, and after a second, the `timeout` aspect will interrupt that.
