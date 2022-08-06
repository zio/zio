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



