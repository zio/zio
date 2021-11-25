---
id: test-aspect
title: "TestAspect"
---

A `TestAspect` is an aspect that can be weaved into specs. We can think of an aspect as a polymorphic function, capable of transforming one test into another, possibly enlarging the environment or error type.

## Before, After and Around

1. We can run a test _before_, _after_, or _around_ every test:
- `TestAspect.before`
- `TestAspect.after`
- `TestAspect.around`

```scala mdoc:invisible
import zio._
def deleteDir(dir: Option[String]): Task[Unit] = Task{
  val _ = dir
}
```

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

test("before and after") {
  for {
    tmp <- System.env("TEMP_DIR")
  } yield assertTrue(tmp.contains("/tmp/test"))
} @@ TestAspect.before(
  TestSystem.putEnv("TEMP_DIR", s"/tmp/test")
) @@ TestAspect.after(
  System.env("TEMP_DIR").flatMap(deleteDir)
)
```

2. The `TestAspect.aroundTest` takes a managed resource and evaluates every test within the context of the managed function.

3. There are also `TestAspect.beforeAll`, `TestAspect.afterAll`, and `TestAspect.aroundAll` variants.

4. Using `TestAspect.aroundWith` and `TestAspect.aroundAllWith` we can evaluate every test or all test between two given effects, `before` and `after`, where the result of the `before` effect can be used in the `after` effect.

## Execution Strategy

ZIO Test has two different strategies to run members of a test suite: _sequential_ and _parallel_. Accordingly, there are two test aspects for specifying the execution strategy:

1.**`TestAspect.parallel`** — The default strategy is parallel. We can explicitly enable it:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

suite("Parallel")(
  test("A")(Live.live(ZIO("Running Test A").delay(1.second)).debug.map(_ => assertTrue(true))),
  test("B")(ZIO("Running Test B").debug.map(_ => assertTrue(true))),
  test("C")(Live.live(ZIO("Running Test C").delay(500.millis)).debug.map(_ => assertTrue(true)))
) @@ TestAspect.parallel
```

After running this suite, we have the following output:

```
Running Test B
Running Test C
Running Test A
+ Parallel
  + A
  + B
  + C
```

To change the degree of the parallelism, we can use the `parallelN` test aspect. It takes the number of fibers and executes the members of a suite in parallel up to the specified number of concurrent fibers.

2. **`TestAspect.sequential`** — To execute them sequentially, we can use the `sequential` test aspect:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

suite("Sequential")(
  test("A")(Live.live(ZIO("Running Test A").delay(1.second)).debug.map(_ => assertTrue(true))),
  test("B")(ZIO("Running Test B").debug.map(_ => assertTrue(true))),
  test("C")(Live.live(ZIO("Running Test C").delay(500.millis)).debug.map(_ => assertTrue(true)))
) @@ TestAspect.sequential
```

And here is the output:

```
Running Test A
Running Test B
Running Test C
+ Sequential
  + A
  + B
  + C
```

## Timing Out with Safe Interruption

We can easily time out a long-running test:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

test("effects can be safely interrupted") {
  for {
    r <- ZIO.attempt(println("Still going ...")).forever
  } yield assert(r)(Assertion.isSuccess)
} @@ timeout(1.second)
```

By applying a `timeout(1.second)` test aspect, this will work with ZIO's interruption mechanism. So when we run this test, you can see a tone of print lines, and after a second, the `timeout` aspect will interrupt that.

## Flaky and Non-flaky Tests

Whenever we deal with concurrency issues or race conditions, we should ensure that our tests pass consistently. The `nonFlaky` is a test aspect to do that.

It will run a test several times, by default 100 times, and if all those times pass, it will pass, otherwise, it will fail:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

test("random value is always greater than zero") {
  for {
    random <- Random.nextIntBounded(100)
  } yield assert(random)(Assertion.isGreaterThan(0))
} @@ nonFlaky
```

Additionally, there is a `TestAspect.flaky` test aspect which retries a test until it succeeds.

## Debugging

The `TestConsole` service has two modes debug and silent state. ZIO Test has two corresponding test aspects to switch the debug state on and off:

1. `TestAspect.debug` — When the `TestConsole` is in the debug state, the console output is rendered to the standard output in addition to being written to the output buffer. We can manually enable this mode by using `TestAspect.debug` test aspect.

2. `TestAspect.silent` — This test aspect turns off the debug mode and turns on the silent mode. So the console output is only written to the output buffer and not rendered to the standard output.

## Platform-specific Tests

Sometimes we have platform-specific tests. Instead of creating separate sources for each platform to test those tests, we can use a proper aspect to run those tests on a specific platform.

To do that we can use `jvmOnly`, `jsOnly` or `nativeOnly` aspects:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

test("Java virtual machine name can be accessed") {
  for {
    vm <- live(System.property("java.vm.name"))
  } yield
    assert(vm)(Assertion.isSome(Assertion.containsString("VM")))
} @@ jvmOnly
```

## Restoring State of Test Services

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

def repeat5[R0 <: ZTestEnv with Live] =
  new PerTest[Nothing, R0, Nothing, Nothing] {
    override def perTest[R <: R0, E](test: ZIO[R, TestFailure[E], TestSuccess])(
      implicit trace: ZTraceElement
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
      clock <- ZIO.service[Clock]
      _ <- TestClock.adjust(1.second)
      time <- clock.currentTime(TimeUnit.SECONDS).debug("current time")
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
      clock <- ZIO.service[Clock]
      _ <- TestClock.adjust(1.second)
      time <- clock.currentTime(TimeUnit.SECONDS).debug("current time")
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

## Test Configs

To run cases, there are some [default configuration settings](environment/test-config.md) which are used by test runner, such as _repeats_, _retries_, _samples_ and _shrinks_. We can change these settings using test aspects:

1. **`TestAspect.repeats(n: Int)`** — Runs each test with the number of times to repeat tests to ensure they are stable set to the specified value.
2. **`TestAspect.retries(n: Int)`** — Runs each test with the number of times to retry flaky tests set to the specified value.

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

test("repeating a test") {
  ZIO("retry flaky test to ensure it is nonFlaky")
    .debug
    .map(_ => assertTrue(true))
} @@ TestAspect.nonFlaky @@ TestAspect.repeats(5)
```

3. **`TestAspect.samples(n: Int)`** — Runs each test with the number of sufficient samples to check for a random variable set to the specified value.
4. **`TestAspect.shrinks(n: Int)`** — Runs each test with the maximum number of shrinkings to minimize large failures set to the specified value.

Let's change the number of default samples in the following example:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

test("customized number of samples") {
  for {
    ref <- Ref.make(0)
    _ <- check(Gen.int)(_ => assertM(ref.update(_ + 1))(Assertion.anything))
    value <- ref.get
  } yield assertTrue(value == 50)
} @@ TestAspect.samples(50)
```

## Tagging

ZIO Test allows us to define some arbitrary tags. By labeling tests with one or more tags, we can categorize them, and then, when running tests, we can filter tests according to their tags.

Let's tag all slow tests and run them separately:

```scala mdoc:invisible
import zio.test.{test, _}

val longRunningAssertion        = assertTrue(true)
val anotherLongRunningAssertion = assertTrue(true)
```

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

object TaggedSpecsExample extends DefaultRunnableSpec {
  def spec =
    suite("a suite containing tagged tests")(
      test("a slow test") {
        longRunningAssertion
      } @@ tag("slow", "math"),
      test("a simple test") {
        assertTrue(1 + 1 == 2)
      } @@ tag("math"),
      test("another slow test") {
        anotherLongRunningAssertion
      } @@ tag("slow")
    )
}
```

By adding the `-tags slow` argument to the command line, we will only run the slow tests:

```
sbt> test:runMain TaggedSpecsExample -tags slow
```

The output would be:

```
[info] running (fork) TaggedSpecsExample -tags slow
[info] + a suite containing tagged tests - tagged: "slow", "math"
[info]   + a slow test - tagged: "slow", "math"
[info]   + another slow test - tagged: "slow"
[info] Ran 2 tests in 162 ms: 2 succeeded, 0 ignored, 0 failed
[success] Total time: 1 s, completed Nov 2, 2021, 12:36:36 PM
```

## Conditional Aspects

When we apply a conditional aspect, it will run the spec only if the specified predicate is satisfied:

- **`ifEnv`** — An aspect that only runs a test if the specified environment variable satisfies the specified assertion.
- **`ifEnvSet`** — An aspect that only runs a test if the specified environment variable is set.
- **`ifProp`** An aspect that only runs a test if the specified Java property satisfies the specified assertion.
- **`ifPropSet`** — An aspect that only runs a test if the specified Java property is set.

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

test("a test that will run if the product is deployed in the testing environment") {
  ???
} @@ ifEnv("ENV")(_ == "testing")

test("a test that will run if the java.io.tmpdir property is available") {
  ???
} @@ ifEnvSet("java.io.tmpdir")
```
