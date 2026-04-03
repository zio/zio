---
id: running-tests
title: "Running Tests"
---

We can run ZIO Tests in two ways:

1. If we [added](installation.md) `zio.test.sbt.ZTestFramework` to SBT's `testFrameworks`, our tests should be automatically picked up by SBT on invocation of `test`:

```bash
sbt test                      // run all tests
sbt testOnly HelloWorldSpec   // run a specific test
```

To run a specific test by their labels, we can use the `-t "<label>"` option. Assume we have multiple tests like the below:

```scala mdoc:compile-only
import zio.test._

object ExampleSpec extends ZIOSpecDefault {
  def spec = suite("clock")(
    test("foo") {
      assertTrue(true)
    },
    test("foo bar") {
      assertTrue(true)
    },
    test("foo bar baz") {
      assertTrue(true)
    }
  )
}
```

We can run those test that contains the "bar" label using the following SBT command:
```
testOnly ExampleSpec -- -t "bar"
```
You can also execute the single SBT command directly from the shell with this command that properly quotes and escapes arguments:
```
sbt "testOnly ExampleSpec -- -t \"bar\""
```

It will print the following results after running all tests containing the "bar" label:

```scala
sbt:zio-2.0> testOnly ExampleSpec -- -t "bar"
+ example suite
  + foo bar
  + foo bar baz
3 tests passed. 0 tests failed. 0 tests ignored.
```

2. However, if we're not using SBT or have some other special needs, the `ZIOSpecDefault` has a `main` method which can be invoked directly or with SBTs `Test/run` or `Test/runMain` commands:

```bash
sbt Test/run                       // prompt to choose which test to run
sbt Test/runMain HelloWorldSpec    // run a specific test
```
