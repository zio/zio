---
id: timing-out-tests
title: "Timing-out Tests"
---

The `timeout` test aspect takes a duration and times out each test. If the test case runs longer than the time specified, it is immediately canceled and reported as a failure, with a message showing that the timeout was exceeded:

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
