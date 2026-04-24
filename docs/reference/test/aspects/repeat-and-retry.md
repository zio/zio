---
id: repeat-and-retry
title: "Repeat and Retry"
---

There are some situations where we need to repeat a test with a specific schedule, or our tests might fail, and we need to retry them until we make sure that our tests pass. ZIO Test has the following test aspects for these scenarios:

## Repeat 

The `repeat` test aspect takes a `schedule` and repeats a test based on it. The test passes if it passes every time:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

test("repeating a test based on the scheduler to ensure it passes every time") {
  ZIO.debug("repeating successful tests")
    .map(_ => assertTrue(true))
} @@ TestAspect.repeat(Schedule.recurs(5))
```

## Retry

If our test fails occasionally, we can retry failed tests by providing a `schedule` to the `retry` test aspect:

For example, the following test retries a maximum of five times. Once a successful assertion is made, the test passes:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

test("retrying a failing test based on the schedule until it succeeds") {
  ZIO.debug("retrying a failing test")
    .map(_ => assertTrue(true))
} @@ TestAspect.retry(Schedule.recurs(5))
```

## Eventually

3. The `eventually` test aspect keeps retrying a test until it passes, regardless of how many times it fails:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

test("retrying a failing test until it succeeds") {
  ZIO.debug("retrying a failing test")
    .map(_ => assertTrue(true))
} @@ TestAspect.eventually
```
