---
id: environment-specific-tests
title: "Environment-specific Tests"
---

## OS-specific Tests

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

## Platform-specific Tests

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

## Version-specific Tests

Various test aspects can be used to run tests for specific versions of Scala, including `scala2`, `scala211`, `scala212`, `scala213`, and `dotty`. As in the previous section, these test aspects have corresponding `*only` and `except*` versions.
