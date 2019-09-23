---
id: usecases_testing
title:  "Testing"
---

**ZIO Test** is a zero dependency testing library that makes it easy to test effectual programs. Begin by adding the required configuration in your SBT settings:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-test"     % zioVersion % "test",
  "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
),
testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
```

From there the fastest way to start writing tests is to extend `DefaultRunnableSpec`, which creates a Spec that is also an executable program you can run from within SBT using `test:run` or by using `test` with the SBT test runner.

```scala
import zio._
import zio.console._
import zio.test._
import zio.test.Assertion._
import zio.test.mock._

import HelloWorld._

object HelloWorld {
  def sayHello: ZIO[Console, Nothing, Unit] =
    console.putStrLn("Hello, World!")
}

object HelloWorldSpec
    extends DefaultRunnableSpec(
      suite("HelloWorldSpec")(
        testM("sayHello correctly displays output") {
          for {
            _      <- sayHello
            output <- MockConsole.output
          } yield assert(output, equalTo(Vector("Hello, World!\n")))
        }
      )
    )
```

In **ZIO Test**, all tests are immutable values and tests are tightly integrated with ZIO, so testing effectual programs is as natural as testing pure ones. In the example above, our test involved the effect of printing to the console but we didn't have to do anything differently in our test because of this other than use `testM` instead of `test`.

**Mocking**

The library also includes built-in mock versions of all the standard ZIO environmental effects (`Clock`, `Console`, `System`, and `Random`), so when we tested our program above the `helloWorld` method didn't actually print a String to the console but instead wrote the String to a buffer that could access for testing purposes. If you ever do need to access the "live" environment just use the `live` method in the `mock` package or specify the live environment in your type signature like `Live[Console]`.

_Mocking Random_

When working with randomness it is easier to controll what the radnom producer supplies. This can be controlled by a number of methods that allow for filling up buffers with values.

```scala
import zio.test.mock.MockRandom
testM("One can provide its own list of ints") {
  for {
    _  <- MockRandom.feedInts(1, 9, 2, 8, 3, 7, 4, 6, 5)
    r1 <- random.nextInt
    r2 <- random.nextInt
    r3 <- random.nextInt
    r4 <- random.nextInt
    r5 <- random.nextInt
    r6 <- random.nextInt
    r7 <- random.nextInt
    r8 <- random.nextInt
    r9 <- random.nextInt
  } yield assert(
    List(1, 9, 2, 8, 3, 7, 4, 6, 5),
    equalTo(List(r1, r2, r3, r4, r5, r6, r7, r8, r9))
  )
}
```

_Mocking Clock_

Sometimes one need to be able to control the flow of time. In most cases you want your unit test to be as fast as possible. Waiting for readl time to pass by is a real killer for this. ZIO exposes a MockClock in testing Environment that can controll time.

Example 1

Below test will pass. If we woule remove the line `MockClock.adjust(Duration(10, TimeUnit.SECONDS))` it would fail. If we wouldn't have access to `MockClock` to make it pass we would need to put manual `ZIO.sleep` call and that test would take 10 seconds every time when run.
```scala
import zio.test.mock.MockClock
testM("One can move time very fast") {  
  for {
    startTime <- currentTime(TimeUnit.SECONDS)
    _         <- MockClock.adjust(Duration(10, TimeUnit.SECONDS))
    endTime   <- currentTime(TimeUnit.SECONDS)
  } yield assert(endTime - startTime, isGreaterThanEqualTo(10L))
}
```

Example 2

`MockClock` affects also all code running asynchronously that is scheduled to run after certain time but with caveats to how runtime works.

```scala
testM("One can controll time as he see fit") {
  for {
    ref     <- Ref.make(0)
    _       <- (ZIO.sleep(Duration(10, TimeUnit.SECONDS)) *> ref.update(_+1)).fork
    _       <- MockClock.adjust(Duration(11, TimeUnit.SECONDS)) 
    readRef <- ref.get
  } yield assert(1, equalTo(readRef))
}
```

The above code cretes a shared mutable cell that will be incremented by 1 after 10 seconds. With call to `MockClock.adjust(Duration(11, TimeUnit.SECONDS))` we simulate passing of 11 seconds of time. Unfortunately *there is no gurantee* that the ref is already increamented. `ZIO.sleep` only guarantees that at least 10 seconds will pass. There is no upper bound. This depends on when the runtime decides to run fiber that control process of updating this cell.



**Property Based Testing**

Support for property based testing is included out of the box through the `check` method and its variants and the `Gen` and `Sample` classes. For example, here is how we could write a property to test that integer addition is associative.

```scala
val associativity =
  check(Gen.anyInt, Gen.anyInt, Gen.anyInt) { (x, y, z) =>
    assert((x + y) + z, equalTo(x + (y + z)))
  }
```

If a property fails, the failure will be automatically shrunk to the smallest failing cases to make it easier for you to diagnose the problem. And shrinking is integrated with the generation of random variables, so you are guaranteed that any shrunk counterexample will meet the conditions of your original generator.

**Results Reporting**

When tests do fail, it is easy to see what went wrong because the test reporter will show you the entire assertion that failed and the specific part of the assertion that failed. To facilitate this, a variety of assertion combinators are included in the `Assertion` class.

**Test Aspects**

Test aspects are powerful tools for modifying behavior of individual tests or even entire suites that you have already written. Convenient syntax `@@` is provided for applying test aspects. So for example you could apply a timeout to a test by using `test @@ timeout(60.seconds)` or only run a test on javascript by using `test @@ jsOnly`. Test aspects are highly composable so you can combine multiple test aspects together or apply them only to certain tests that match a predicate you specify.

**Zero Dependencies**

As a library with zero third party dependencies, this project is available on the JVM, ScalaJS, and will be available on Dotty and Scala Native in the near future. So you can write your tests once and make sure that your code works correctly across all platforms that you support.
