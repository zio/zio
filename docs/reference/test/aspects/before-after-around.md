---
id: before-after-around
title: "Before, After, and Around Test Aspects"
sidebar_label: "Before, After, and Around"
---

1. We can run an effect _before_, _after_, or _around_ every test:
- `TestAspect.before`
- `TestAspect.after`
- `TestAspect.afterFailure`
- `TestAspect.afterSuccess`
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

3. There are also `TestAspect.beforeAll`, `TestAspect.afterAll`, `afterAllFailure`, `afterAllSuccess`, and `TestAspect.aroundAll` variants.

4. Using `TestAspect.aroundWith` and `TestAspect.aroundAllWith` we can evaluate every test or all test between two given effects, `before` and `after`, where the result of the `before` effect can be used in the `after` effect.
