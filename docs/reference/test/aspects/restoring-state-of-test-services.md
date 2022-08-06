---
id: restoring-state-of-test-services
title: "Restoring State of Test Services"
---

ZIO Test has some test aspects which restore the state of given restorable test services, such as `TestClock`, `TestConsole`, `TestRandom` and `TestSystem`, to their starting state after the test is run. Note that these test aspects are only useful when we are repeating tests.

Here is a list of restore methods:

- `TestAspect.restore`
- `TestAspect.restoreTestClock`
- `TestAspect.restoreTestConsole`
- `TestAspect.restoreTestRandom`
- `TestAspect.restoreTestSystem`
- `TestAspect.restoreTestEnvironment`

Let's try an example. Assume we have written the following test aspect, which repeats the test 5 times:

```scala mdoc:invisible:nest
import zio._
import zio.test._
import zio.test.TestAspect._

def repeat5 =
  new PerTest[Nothing, Any, Nothing, Any] {
    override def perTest[R, E](test: ZIO[R, TestFailure[E], TestSuccess])(
      implicit trace: Trace
    ): ZIO[R, TestFailure[E], TestSuccess] =
      test.repeatN(5)
  }
```

When we run a test with this testing aspect, on each try, we have a polluted test environment:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }
import java.util.concurrent.TimeUnit

suite("clock suite")(
  test("adjusting clock") {
    for {
      clock <- ZIO.clock
      _     <- TestClock.adjust(1.second)
      time  <- clock.currentTime(TimeUnit.SECONDS).debug("current time")
    } yield assertTrue(time == 1)
  } @@ repeat5
)
```

This test fails in the second retry:

```
current time: 1
current time: 2
- some suite
  - clock suite
    - adjusting clock
      ✗ 2 was not equal to 1
      time == 1
      time = 2
```

It failed because of the first run of the test changed the state of the `TestClock` service, so on the next run, the initial state of the test is not zero. In such a situation, when we are repeating a test, after each run we can restore the state of the test to its initial state, using `TestAspect.restore*` test aspects:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }
import java.util.concurrent.TimeUnit

suite("clock suite")(
  test("adjusting clock") {
    for {
      clock <- ZIO.clock
      _     <- TestClock.adjust(1.second)
      time  <- clock.currentTime(TimeUnit.SECONDS).debug("current time")
    } yield assertTrue(time == 1)
  } @@ TestAspect.restoreTestClock @@ repeat5
)
```

The output of running this test would be as follows:

```
current time: 1
current time: 1
current time: 1
current time: 1
current time: 1
current time: 1
+ clock suite
  + adjusting clock
  Ran 1 test in 470 ms: 1 succeeded, 0 ignored, 0 failed
```
