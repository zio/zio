---
id: test-aspect
title: "TestAspect"
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

## Before, After and Around

1. We can run a test _before_, _after_, or _around_ every test:
- `TestAspect.before`
- `TestAspect.after`
- `TestAspect.around`

```scala mdoc:invisible
import zio._
def deleteDir(dir: Option[String]): Task[Unit] = ZIO.attempt{
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

2. The `TestAspect.aroundTest` takes a scoped resource and evaluates every test within the context of the scoped function.

3. There are also `TestAspect.beforeAll`, `TestAspect.afterAll`, and `TestAspect.aroundAll` variants.

4. Using `TestAspect.aroundWith` and `TestAspect.aroundAllWith` we can evaluate every test or all test between two given effects, `before` and `after`, where the result of the `before` effect can be used in the `after` effect.

## Conditional Aspects

When we apply a conditional aspect, it will run the spec only if the specified predicate is satisfied.

- **`ifEnv`** — Only runs a test if the specified environment variable satisfies the specified assertion.
- **`ifEnvSet`** — Only runs a test if the specified environment variable is set.
- **`ifProp`** — Only runs a test if the specified Java property satisfies the specified assertion.
- **`ifPropSet`** — Only runs a test if the specified Java property is set.

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

## Debugging and Diagnostics

### Debugging
The `TestConsole` service has two modes debug and silent state. ZIO Test has two corresponding test aspects to switch the debug state on and off:

1. `TestAspect.debug` — When the `TestConsole` is in the debug state, the console output is rendered to the standard output in addition to being written to the output buffer. We can manually enable this mode by using `TestAspect.debug` test aspect.

2. `TestAspect.silent` — This test aspect turns off the debug mode and turns on the silent mode. So the console output is only written to the output buffer and not rendered to the standard output.

### Diagnostics

The `diagnose` is an aspect that runs each test on a separate fiber and prints a fiber dump if the test fails or has not terminated within the specified duration.

## Environment-specific Tests

### OS-specific Tests

To run a test on a specific operating system, we can use one of the `unix`, `mac` or `windows` test aspects or a combination of them. Additionally, we can use the `os` test aspect directly:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

suite("os")(
  test("unix test") {
    ZIO.attempt("running on unix/linux os")
      .debug
      .map(_ => assertTrue(true))
  } @@ TestAspect.unix,
  test("macos test") {
    ZIO.attempt("running on macos")
      .debug
      .map(_ => assertTrue(true))
  } @@ TestAspect.os(_.isMac)
)
```

### Platform-specific Tests

Sometimes we have platform-specific tests. Instead of creating separate sources for each platform to test those tests, we can use a proper aspect to run those tests on a specific platform.

To run a test on a specific platform, we can use one of the `jvm`, `js`, or `native` test aspects or a combination of them. If we want to run our test only on one of these platforms, we can use one of the `jvmOnly`, `jsOnly`, or `nativeOnly` test aspects. To exclude one of these platforms, we can use the `exceptJs`, `exceptJVM`, or `exceptNative` test aspects:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("Java virtual machine name can be accessed") {
  for {
    vm <- live(System.property("java.vm.name"))
  } yield
    assertTrue(vm.get.contains("VM"))
} @@ TestAspect.jvmOnly
```

### Version-specific Tests

Various test aspects can be used to run tests for specific versions of Scala, including `scala2`, `scala211`, `scala212`, `scala213`, and `dotty`. As in the previous section, these test aspects have corresponding `*only` and `except*` versions.

## Execution Strategy

ZIO Test has two different strategies to run members of a test suite: _sequential_ and _parallel_. Accordingly, there are two test aspects for specifying the execution strategy:

1.**`TestAspect.parallel`** — The default strategy is parallel. We can explicitly enable it:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

suite("Parallel")(
  test("A")(Live.live(ZIO.attempt("Running Test A").delay(1.second)).debug.map(_ => assertTrue(true))),
  test("B")(ZIO.attempt("Running Test B").debug.map(_ => assertTrue(true))),
  test("C")(Live.live(ZIO.attempt("Running Test C").delay(500.millis)).debug.map(_ => assertTrue(true)))
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
  test("A")(Live.live(ZIO.attempt("Running Test A").delay(1.second)).debug.map(_ => assertTrue(true))),
  test("B")(ZIO.attempt("Running Test B").debug.map(_ => assertTrue(true))),
  test("C")(Live.live(ZIO.attempt("Running Test C").delay(500.millis)).debug.map(_ => assertTrue(true)))
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
  } yield assertTrue(random > 0)
} @@ nonFlaky
```

Additionally, there is a `TestAspect.flaky` test aspect which retries a test until it succeeds.

## Ignoring Tests

To ignore running a test, we can use the `ignore` test aspect:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("an ignored test") {
  assertTrue(false)
} @@ TestAspect.ignore
```

To fail all ignored tests, we can use the `success` test aspect:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

suite("sample tests")(
  test("an ignored test") {
    assertTrue(false)
  } @@ TestAspect.ignore,
  test("another ignored test") {
    assertTrue(true)
  } @@ TestAspect.ignore
) @@ TestAspect.success 
```

## Non-deterministic

The random process of the `TestRandom` is said to be deterministic since, with the initial seed, we can generate a sequence of predictable numbers. So with the same initial seed, it will generate the same sequence of numbers.

By default, the initial seed of the `TestRandom` is fixed. So repeating a generator more and more results in the same sequence:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

test("pseudo-random number generator with fixed initial seed") {
  check(Gen.int(0, 100)) { n =>
    ZIO.attempt(n).debug.map(_ => assertTrue(true))
  }
} @@
  samples(5) @@
  after(Console.printLine("----").orDie) @@
  repeat(Schedule.recurs(1))
```

Regardless of how many times we repeat this test, the output would be the same:

```
99
51
81
48
51
----
99
51
81
48
51
----
+ pseudo-random numbers with fixed initial seed - repeated: 2
Ran 1 test in 522 ms: 1 succeeded, 0 ignored, 0 failed
```

The `nondeterministic` test aspect, will change the seed of the pseudo-random generator before each test repetition:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }
import zio.test.TestAspect._

test("pseudo-random number generator with random initial seed on each repetition") {
  check(Gen.int(0, 100)) { n =>
    ZIO.attempt(n).debug.map(_ => assertTrue(true))
  }
} @@
  nondeterministic @@
  samples(5) @@
  after(Console.printLine("----").orDie) @@
  repeat(Schedule.recurs(1))
```

Here is a sample output, which we have different sequences of numbers on each run:

```
73
9
17
33
10
----
42
85
38
2
73
----
+ pseudo-random number generator with random initial seed on each repetition - repeated: 2
Ran 1 test in 733 ms: 1 succeeded, 0 ignored, 0 failed
```

## Passing Failed Tests

The `failing` aspect makes a test that failed for any reason pass.

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("failing a passing test") {
  assertTrue(true)
} @@ TestAspect.failing
```

If the test passes this aspect will make it fail:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("passing a failing test") {
  assertTrue(false)
} @@ TestAspect.failing
```

It is also possible to pass a failing test on a specified failure:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("a test that will only pass on a specified failure") {
  ZIO.fail("Boom!").map(_ => assertTrue(true))
} @@ TestAspect.failing[String] {
  case TestFailure.Assertion(_, _) => true
  case TestFailure.Runtime(cause: Cause[String], _) => cause match {
    case Cause.Fail(value, _)
      if value == "Boom!" => true
    case _ => false
  }
}
```

## Repeat and Retry

There are some situations where we need to repeat a test with a specific schedule, or our tests might fail, and we need to retry them until we make sure that our tests pass. ZIO Test has the following test aspects for these scenarios:

1. **`TestAspect.repeat(schedule: Schedule)`** — It takes a schedule and repeats a test based on it. The test passes if it passes every time:

  ```scala mdoc:compile-only
  import zio._
  import zio.test.{ test, _ }
  
  test("repeating a test based on the scheduler to ensure it passes every time") {
    ZIO("repeating successful tests")
      .debug
      .map(_ => assertTrue(true))
  } @@ TestAspect.repeat(Schedule.recurs(5))
  ```

2. **`TestAspect.retry(schedule: Schedule)`** — If our test fails occasionally, we can retry failed tests by providing a scheduler to the `retry` test aspect.

  For example, the following test retries a maximum of five times. Once a successful assertion is made, the test passes:

  ```scala mdoc:compile-only
  import zio._
  import zio.test.{ test, _ }
  
  test("retrying a failing test based on the schedule until it succeeds") {
    ZIO("retrying a failing test")
      .debug
      .map(_ => assertTrue(true))
  } @@ TestAspect.retry(Schedule.recurs(5))
  ```
3. **`TestAspect.eventually`** — This test aspect keeps retrying a test until it passes, regardless of how many times it fails:

  ```scala mdoc:compile-only
  import zio._
  import zio.test.{ test, _ }
  
  test("retrying a failing test until it succeeds") {
    ZIO("retrying a failing test")
      .debug
      .map(_ => assertTrue(true))
  } @@ TestAspect.eventually
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

## Sized Tests

To change the default _size_ used by [sized generators](gen.md#sized-generators) we can use `sized` test aspect:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

test("generating small list of characters") {
  check(Gen.small(Gen.listOfN(_)(Gen.alphaNumericChar))) { n =>
    ZIO.attempt(n).debug *> Sized.size.map(s => assertTrue(s == 50))
  }
} @@ TestAspect.sized(50) @@ TestAspect.samples(5)
```

Sample output:

```
List(p, M)
List()
List(0, m, 5)
List(Y)
List(O, b, B, V)
+ generating small list of characters
Ran 1 test in 676 ms: 1 succeeded, 0 ignored, 0 failed
```

## Test Annotation

### Measuring Execution Time

We can annotate the execution time of each test using the `timed` test aspect:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }

suite("a timed suite")(
  test("A")(Live.live(ZIO.sleep(100.millis)).map(_ => assertTrue(true))),
  test("B")(assertTrue(true)),
  test("C")(assertTrue(true))
) @@ timed 
```

After running the test suite, the output should be something like this:

```
+ a timed suite - 178 ms (100.00%)
  + A - 108 ms (60.95%)
  + B - 34 ms (19.39%)
  + C - 35 ms (19.66%)
Ran 3 tests in 346 ms: 3 succeeded, 0 ignored, 0 failed
```

### Tagging

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

object TaggedSpecsExample extends ZIOSpecDefault {
  def spec =
    suite("a suite containing tagged tests")(
      test("a slow test") {
        longRunningAssertion
      } @@ TestAspect.tag("slow", "math"),
      test("a simple test") {
        assertTrue(1 + 1 == 2)
      } @@ TestAspect.tag("math"),
      test("another slow test") {
        anotherLongRunningAssertion
      } @@ TestAspect.tag("slow")
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

## Test Configs

To run cases, there are some [default configuration settings](environment/test-config.md) which are used by test runner, such as _repeats_, _retries_, _samples_ and _shrinks_. We can change these settings using test aspects:

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
    _ <- check(Gen.int)(_ => assertM(ref.update(_ + 1))(Assertion.anything))
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
