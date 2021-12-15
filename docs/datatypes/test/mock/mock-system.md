---
id: system
title: MockSystem
---

`MockSystem` is a built-in mock version of the [`System`](../../contextual/services/system.md) service. It mocks all the system service capabilities.

Here's how we can mock the `MockSystem.property` capability:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.mock._

test("calling mocked property should return expected property") {
  for {
    property <- System.property("java.vm.name").provideLayer(
      MockSystem.Property(
        Assertion.equalTo("java.vm.name"),
        Expectation.value(Some("OpenJDK 64-Bit Server VM"))
      )
    )
  } yield assertTrue(property.get.contains("OpenJDK"))
}
```
