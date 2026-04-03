---
id: conditional
title: "Conditional Aspects"
---

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
