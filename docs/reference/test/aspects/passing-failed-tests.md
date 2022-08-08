---
id: passing-failed-tests 
title: "Passing Failed Tests"
---

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
