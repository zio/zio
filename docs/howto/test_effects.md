---
id: test-effects
title:  "How to Test effects?"
---

## How zio-test was designed

`zio-test` is designed around the idea of _making tests first-class objects_. What it means is that tests (and other accompanying concepts like assertions) become ordinary values that can be passed around, transformed, and composed together.

This approach allows for greater flexibility compared to some other testing frameworks where tests and additional logic around tests had to be put into callbacks so that framework could make use of them.

This approach also fits better with other `ZIO` concepts like `ZManaged` which can only be used within a scoped block of code. This also created a mismatch between `BeforeAll`, `AfterAll` callback-like methods when there were resources that should be opened and closed during test suite execution.

Another thing worth pointing out is that tests being values are also effects. Implications of this design are far-reaching.

- First, the well-known problem of testing asynchronous value is gone. Whereas in other frameworks you have to somehow "run" your effects and at best wrap them in `scala.util.Future` because blocking would eliminate running on ScalaJS, `zio-test` expects you to create `ZIO` objects. There is no need for indirect transformations from one wrapping object to another.

- Second, because our tests are ordinary `ZIO` values we don't need to turn to a testing framework for things like retries, timeouts, and resource management. We can solve all those problems with the full richness of functions that `ZIO` exposes.

## Constructing tests

All below code assumes that you have imported `zio.test._`

The backbone of `zio-test` is the `Spec[L, T]` class. Every spec is labeled with `L`, and can be a suite that contains other specs, or a test of type `T`.

The most common and easy way to create suites is to use the `suite` function. For testing of pure functions and for effectful testing there is `test`:

```scala mdoc
import zio.test._
import zio.Clock.nanoTime
import Assertion.isGreaterThan

val clockSuite = suite("clock") (
  test("time is non-zero") {
    assertM(Live.live(nanoTime))(isGreaterThan(0L))
  }
)
```

As you can see the whole suite was assigned to `clockSuite` val. As it was said suites can contain other suites, so we can aggregate them as much as needed.

For example, we can have multiple suites that test external HTTP APIs and one big suite that will aggregate them all:


```scala mdoc
import zio.test._
import Assertion._

val paymentProviderABCSuite  =
  suite("ABC payment provider tests") {
    test("Your test")(
      assert("Your value")(Assertion.isNonEmptyString)
    )
  }
val paymentProviderXYZSuite  =
  suite("XYZ payment provider tests") {
    test("Your other test")(
      assert("Your other value")(Assertion.isNonEmptyString)
    )
  }
val allPaymentProvidersTests =
  suite("All payment providers tests")(
    paymentProviderABCSuite,
    paymentProviderXYZSuite
  )
```

Real tests that run some logic and return testing result are created mostly with `test` function. It expects two arguments, first one will be the label of test which will be used for visual reporting back to the user, and an assertion of type `ZIO[R, E, TestResult]`.

This means writing test in `zio-test` mostly gets down to creating a `ZIO` object that will produce `TestResult`.

There is another variant of function for creating a test that are pure called simply `test`. It expects a thunk of code that will just return a `TestResult` without packing it into `ZIO`.

### Assertions - creating TestResults

As it was already mentioned, tests should return `TestResult`. The most common way to produce a `TestResult` is to resort to `assert` or its effectful counterpart `assertM`. Both of them accept a value of type `A` (effectful version wrapped in a `ZIO`) and an `Assertion[A]`.

To create `Assertion[A]` object one can use functions defined under `zio.test.Assertion`. There are already a number of useful assertions predefined like `equalTo`, `isFalse`, `isTrue`, `contains`, `throws` and more.

What is really useful in assertions is that they behave like boolean values and can be composed with operators
known from operating on boolean values like and (`&&`), or (`||`), negation (`negate`):

```scala mdoc
import zio.test.Assertion

val assertionForString: Assertion[String] = 
  Assertion.containsString("Foo") && Assertion.endsWithString("Bar")
```

What's more, assertions also compose with each other allowing for doing rich diffs not only simple value to value comparison:

```scala mdoc
import zio.test.Assertion.{isRight, isSome,equalTo, hasField}

test("Check assertions") {
  assert(Right(Some(2)))(isRight(isSome(equalTo(2))))
}
```

Here we're checking deeply nested values inside an `Either` and `Option`. Because `Assertion`s compose this is not a problem. All layers are being peeled off tested for the condition until the final value is reached.

Here the expression `Right(Some(2))` is of type `Either[Any, Option[Int]]`and our assertion `isRight(isSome(equalTo(2)))` is of type `Assertion[Either[Any, Option[Int]]]`

```scala mdoc:reset-object
import zio.test._
import zio.test.Assertion.{isRight, isSome,equalTo, isGreaterThanEqualTo, not, hasField}

final case class Address(country:String, city:String)
final case class User(name:String, age:Int, address: Address)

test("Rich checking") {
  assert(
    User("Jonny", 26, Address("Denmark", "Copenhagen"))
  )(
    hasField("age", (u:User) => u.age, isGreaterThanEqualTo(18)) &&
    hasField("country", (u:User) => u.address.country, not(equalTo("USA")))
  )
}
```

There is also an easy way to test an object's data for certain assertions with `hasField` which accepts besides a name, a mapping function from object to its tested property, and `Assertion` object which will validate this property. Here our test checks if a person has at least 18 years and is not from the USA.

What is nice about those tests is that test reporters will tell you exactly which assertion was broken. Let's say we would change `isGreaterThanEqualTo(18)` to `isGreaterThanEqualTo(40)` which will fail. Printout
on the console will be a nice detailed text explaining what exactly went wrong:

```bash
[info]       User(Jonny,26,Address(Denmark,Copenhagen)) did not satisfy (hasField("age", _.age, isGreaterThanEqualTo(45)) && hasField("country", _.country, not(equalTo(USA))))
[info]       26 did not satisfy isGreaterThanEqualTo(45)
```

Having this all in mind, probably the most common and also most readable way of structuring tests is to pass a for-comprehension to `test` function and yield a call to `assert` function.

```scala mdoc
import zio.{test => _, _}
import zio.test._
import Assertion._

test("Semaphore should expose an available number of permits") {
  for {
    s         <- Semaphore.make(1L)
    permits   <- s.available.commit
  } yield assert(permits)(equalTo(1L))
}
```

### Running tests

When all of our tests are constructed, we need to have a way to actually execute them. Your first stop is the `zio.test.DefaultRunnableSpec` which accepts a single suite that will be executed. A single suite might seem to be limiting but as it was already said suites can hold any number of other suites. You may structure your tests like this:

```scala mdoc
import zio.test._
import zio.Clock.nanoTime
import Assertion._

val suite1 = suite("suite1") (
  test("s1.t1") {assertM(nanoTime)(isGreaterThanEqualTo(0L))},
  test("s1.t2") {assertM(nanoTime)(isGreaterThanEqualTo(0L))}
)
val suite2 = suite("suite2") (
  test("s2.t1") {assertM(nanoTime)(isGreaterThanEqualTo(0L))},
  test("s2.t2") {assertM(nanoTime)(isGreaterThanEqualTo(0L))},
  test("s2.t3") {assertM(nanoTime)(isGreaterThanEqualTo(0L))}
)
val suite3 = suite("suite3") (
  test("s3.t1") {assertM(nanoTime)(isGreaterThanEqualTo(0L))}
)

object AllSuites extends DefaultRunnableSpec {
  def spec = suite("All tests")(suite1, suite2, suite3)
}
```

`DefaultRunnableSpec` is very similar in its logic of operations to `zio.App`. Instead of providing one `ZIO` application at the end of the world, we provide a suite that can be a tree of other suites and tests. Another resemblance is that `DefaultRunnableSpec` provides an Environment.

Here it is an instance of `TestEnvironment` which helps us with controlling our systems infrastructure. More info on using test environment can be found in the sections below.

Just like with `zio.App` where at the very end an instance of `ZIO[R,E,A]` is expected where `R` can be at maximum of type `Environment` in `DefaultRunnableSpec` `R` cannot be more than `TestEnvironment`. So just like in normal application if our `R` is composed of some other modules we need to provide them first before test can be executed. How can we provide our dependencies?

Here again, the design of `zio-test` shines. Since our tests are ordinary values we can just transform them with a call to `mapTest`. It accepts a lambda of type `ZIO[R with TestSystem, TestFailure[Throwable], TestSuccess[Unit] ] => T1`.

Without getting into too much detail about types, we can see that our lambda argument is a test instance (`ZIO`) that expects an environment of type `R with TestSystem`. This is no different from normal usage of ZIO in `zio.App`. We can use the same `provide`, `provideSome` methods to provide modules that `DefaultRunnableSpec` cannot provide itself as those are users modules.

When all dependencies are provided, we can run our tests in two ways. If we added `zio-test-sbt` to our dependencies and `zio.test.sbt.TestFramework` to SBT's `testFrameworks` our tests should be automatically picked up by SBT on invocation of `test`. However, if we're not using SBT or have some other special needs `DefaultRunnableSpec` has a `main` method which can be invoked directly or with SBTs `test:run`.

```sbt
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-test"     % zioVersion % "test",
  "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
),
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
```

## Using Test Environment

What we expect from tests (at least those that we consider unit tests) is to be stable, i.e., consecutive runs should yield the same results and take more or less the same amount of time.

The biggest source of complexity during testing comes from external services which we cannot control like external payment APIs, object storages, HTTP APIs, etc. It is normal to hide these kinds of services behind an interface and provide test instances to regain control and determinism.

However, there is another source of complexity that comes from the local infrastructure that is also hard to control without building prior abstractions. Things like stdin/stdout, clocks, random generators, schedulers can make writing tests hard or even impossible.

Fortunately, ZIO abstracted most of it in its runtime under `Environment` type. Thanks to this design `zio-test` could easily provide its own implementation named `TestEnvironment` which gives you test implementations of mentioned infrastructure.

In most of the cases when you'll be using `ZIO`s `test` test implementations are already created and should be controlled by exposed functions on companion object.

If for some reason you would like to provide a custom environment or are using another testing framework but still want to use test environment there are `make` functions on companion objects of test modules where you can construct your own.

It is easy to accidentally use different test instances at the same time.

```scala
import zio.test._
import Assertion._

test("`acquire` doesn't leak permits upon cancellation") {
  for {
      testClock <- TestClock.makeTest(TestClock.DefaultData)
      s         <- Semaphore.make(1L)
      sf        <- s.acquireN(2).timeout(1.millisecond).either.fork
      _         <- testClock.adjust(1.second)
      _         <- sf.join
      _         <- s.release
      permits   <- s.available
  } yield assert(permits, equalTo(2L))
}
```

The above code doesn't work. We created a new `TestClock` instance and are correctly adjusting its time. What might be surprising is that call to `timeout` will use the `TestClock` provided by the `TestEnvironment` not our `testClock` instance. It is easy to know why when you look at the signature of `timeout`:

```scala mdoc
import zio.Clock

sealed trait ZIO[-R, +E, +A] extends Serializable { self =>
    /* All other method declarations in this trait ignored to avoid clutter */

    def timeout(d: Duration): ZIO[R with Clock, E, Option[A]]
}
```

The returned type is `ZIO[R with Clock, E, Option[A]]` where our environment is "some R plus a Clock". Before running this `Clock` has to be provided, and the framework provides the Clock from the `TestEnvironment` not our instance variable as it is not aware that we created it.

If you need to provide real implementations instead of the test instances to some part of your tests there is a `live` method that will transform your `ZIO[R, E, A]` to `ZIO[Live[R], E, A]`. Going from `R` to `Live[R]` instructs the framework that we really want to be provided with live implementations.

### Testing Random

When working with randomness, testing might be hard because the inputs to the tested function change on every invocation. So our code behaves in an indeterministic way.

Precisely because of this reason `ZIO` exposes `TestRandom` module which allows for fully deterministic testing of code that deals with Randomness. `TestRandom` can operate in two modes based on the needed use-case.

In the first mode it is a purely functional pseudo-random number generator. During generation on random values like when calling `nextInt` no internal state is being mutated. It is expected to chain such operations with combinators like `flatMap`. To preserve the same values generated between invocation of tests `setSeed` method can be used. It is guaranteed to return the same sequence of values for any given seed:

```scala mdoc
import zio.test.assert
import zio.test.Assertion.equalTo

test("Use setSeed to generate stable values") {
  for {
    _  <- TestRandom.setSeed(27)
    r1 <- Random.nextLong
    r2 <- Random.nextLong
    r3 <- Random.nextLong
  } yield
    assert(List(r1,r2,r3))(equalTo(List[Long](
      -4947896108136290151L,
      -5264020926839611059L,
      -9135922664019402287L
    )))
}
```

In the second mode `TestRandom` maintains an internal buffer of values that can be "fed" upfront with methods such as `feedInts`. When random values are being generated, the first values from that buffer are being used.

```scala mdoc
test("One can provide its own list of ints") {
  for {
    _  <- TestRandom.feedInts(1, 9, 2, 8, 3, 7, 4, 6, 5)
    r1 <- Random.nextInt
    r2 <- Random.nextInt
    r3 <- Random.nextInt
    r4 <- Random.nextInt
    r5 <- Random.nextInt
    r6 <- Random.nextInt
    r7 <- Random.nextInt
    r8 <- Random.nextInt
    r9 <- Random.nextInt
  } yield assert(
    List(1, 9, 2, 8, 3, 7, 4, 6, 5)
  )(equalTo(List(r1, r2, r3, r4, r5, r6, r7, r8, r9)))
}
```

When we run out of values in buffer `TestRandom` it falls back to the first mode. If we want we can also clear internal buffers by calling methods like `clearInts`.

### Testing Clock

In most cases you want unit tests to be as fast as possible. Waiting for real time to pass by is a real killer for this.

ZIO exposes a `TestClock` in `TestEnvironment` that can control the time. We can deterministically and efficiently test effects involving the passage of time without actually having to wait for the full amount of time to pass.

Calls to `sleep` and methods derived from it will semantically block until the clock time is set/adjusted to on or after the time the effect is scheduled to run.

#### Clock Time

Clock time is just like a clock on the wall, except that in our `TestClock`, the clock is broken. Instead of moving by itself, the clock time only changes when adjusted or set by the user, using the `adjust` and `setTime` methods. The clock time never changes by itself.

When the clock is adjusted, any effects scheduled to run on or before the new clock time will automatically be run, in order.

#### Examples

**Example 1**

Thanks to the call to `TestClock.adjust(1.minute)` we moved the time instantly 1 minute.

```scala mdoc
import java.util.concurrent.TimeUnit
import zio.Clock.currentTime
import zio.test.Assertion.isGreaterThanEqualTo
import zio.test._

test("One can move time very fast") {
  for {
    startTime <- currentTime(TimeUnit.SECONDS)
    _         <- TestClock.adjust(1.minute)
    endTime   <- currentTime(TimeUnit.SECONDS)
  } yield assert(endTime - startTime)(isGreaterThanEqualTo(60L))
}
```

**Example 2**

`TestClock` affects also all code running asynchronously that is scheduled to run after a certain time:

```scala mdoc
import zio.test.Assertion.equalTo
import zio.test._

test("One can control time as he see fit") {
  for {
    promise <- Promise.make[Unit, Int]
    _       <- (ZIO.sleep(10.seconds) *> promise.succeed(1)).fork
    _       <- TestClock.adjust(10.seconds)
    readRef <- promise.await
  } yield assert(1)(equalTo(readRef))
}
```

The above code creates a write-once cell that will be set to "1" after 10 seconds asynchronously from a different thread thanks to the call to `fork`. In the end, we wait on the promise until it is set.

With the call to `TestClock.adjust(10.seconds)` we simulate the passing of 10 seconds of time. Because of it, we don't need to wait for the real 10 seconds to pass and thus our unit test can run faster.

This is a pattern that will very often be used when `sleep` and `TestClock` are being used for testing of effects that are based on time. The fiber that needs to sleep will be forked and `TestClock` will used to adjust the time so that all expected effects are run in the forked fiber.

**Example 3**

A more complex example leveraging dependencies and multiple services is shown below:

```scala mdoc:reset
import zio.test.Assertion._
import zio.test._
import zio.{test => _, _}

trait SchedulingService {
  def schedule(promise: Promise[Unit, Int]): ZIO[Any, Exception, Boolean]
}

trait LoggingService {
  def log(msg: String): ZIO[Any, Exception, Unit]
}

val schedulingLayer: ZLayer[Clock with LoggingService, Nothing, SchedulingService] =
  ZLayer.fromFunction { env =>
    new SchedulingService {
      def schedule(promise: Promise[Unit, Int]): ZIO[Any, Exception, Boolean] =
        (ZIO.sleep(10.seconds) *> promise.succeed(1))
          .tap(b => ZIO.service[LoggingService].flatMap(_.log(b.toString)))
          .provideEnvironment(env)
    }
}

test("One can control time for failing effects too") {
  val failingLogger = ZLayer.succeed(new LoggingService {
    override def log(msg: String): ZIO[Any, Exception, Unit] = ZIO.fail(new Exception("BOOM"))
  })

  val partialLayer = (Clock.any ++ failingLogger) >>> schedulingLayer

  val testCase =
    for {
      promise <- Promise.make[Unit, Int]
      result  <- ZIO.service[SchedulingService].flatMap(_.schedule(promise)).exit.fork
      _       <- TestClock.adjust(10.seconds)
      readRef <- promise.await
      result  <- result.join
    } yield assert(1)(equalTo(readRef)) && assert(result)(fails(isSubtype[Exception](anything)))
  testCase.provideSomeLayer[TestEnvironment](partialLayer)
}
```

In this case, we want to test an effect with dependencies that can potentially fail with an error. To do this we need to run the effect and use assertions that expect an `Exit` value.

Because we are providing dependencies to the test we need to provide everything expected by our test case and leave the test environment behind using `.provideSome[TestEnvironment]`.

Keep in mind we do not provide any implementation of the `Clock` because doing will make force `SchedulingService` to use it, while the clock we need here is the `TestClock` provided by the test environment.

The pattern with `Promise` and `await` can be generalized when we need to wait for multiple values using a `Queue`. We simply need to put multiple values into the queue and progress the clock multiple times and there is no need to create multiple promises.

Even if you have a non-trivial flow of data from multiple streams that can produce at different intervals and would like to test snapshots of data at a particular point in time `Queue` can help with that.

```scala mdoc
import zio.test.Assertion.equalTo
import zio.test._
import zio.stream._

test("zipWithLatest") {
  val s1 = Stream.iterate(0)(_ + 1).fixed(100.milliseconds)
  val s2 = Stream.iterate(0)(_ + 1).fixed(70.milliseconds)
  val s3 = s1.zipWithLatest(s2)((_, _))

  for {
    q      <- Queue.unbounded[(Int, Int)]
    _      <- s3.foreach(q.offer).fork
    fiber  <- ZIO.collectAll(ZIO.replicate(4)(q.take)).fork
    _      <- TestClock.adjust(1.second)
    result <- fiber.join
  } yield assert(result)(equalTo(List(0 -> 0, 0 -> 1, 1 -> 1, 1 -> 2)))
}
```

### Testing Console

`TestConsole` allows testing of applications that interact with the console by modeling working with standard input and output as writing and reading to and from internal buffers:

```scala mdoc
import zio.Console

val consoleSuite = suite("ConsoleTest")(
  test("One can test output of console") {
    for {
      _              <- TestConsole.feedLines("Jimmy", "37")
      _              <- Console.printLine("What is your name?")
      name           <- Console.readLine
      _              <- Console.printLine("What is your age?")
      age            <- Console.readLine.map(_.toInt)
      questionVector <- TestConsole.output
      q1             = questionVector(0)
      q2             = questionVector(1)
    } yield {
      assert(name)(equalTo("Jimmy")) &&
      assert(age)(equalTo(37)) &&
      assert(q1)(equalTo("What is your name?\n")) &&
      assert(q2)(equalTo("What is your age?\n"))
    }
  }
)
```

The above code simulates an application that will ask for the name and age of the user. To test it we prefill buffers with answers with the call to `TestConsole.feedLines` method. Calls to `Console.readLine` will get the value from the buffers instead of interacting with the users keyboard.

Also, all output that our program produces by calling `Console.printLine` (and other printing methods) is being gathered and can be accessed with a call to `TestConsole.output`.

### Testing System

With the increased usage of containers and runtimes like Kubernetes, more and more applications are being configured by means of environment variables. It is important to test this logic just like other parts of an application.

For this purpose `zio-test` exposes `TestSystem` module. Additionally, to setting the environment variables it also allows for setting JVM system properties like in the code below:

```scala mdoc
for {
  _      <- TestSystem.putProperty("java.vm.name", "VM")
  result <- System.property("java.vm.name")
} yield assert(result)(equalTo(Some("VM")))
```

It is worth noticing that no actual environment variables or properties will be set during testing so there will be no impact on other parts of the system.
