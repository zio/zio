---
id: system
title: "TestSystem"
---

`TestSystem` supports deterministic testing of effects involving system properties. 

Internally, `TestSystem` maintains mappings of environment variables and system properties that can be set and accessed. No actual environment variables or system properties will be accessed or set as a result of these actions:

```scala mdoc:compile-only
import zio.System
import zio.test._

for {
  _      <- TestSystem.putProperty("java.vm.name", "VM")
  result <- System.property("java.vm.name")
} yield result == Some("VM")
```
