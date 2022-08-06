---
id: ignoring-tests
title: "Ignoring Tests"
---

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
