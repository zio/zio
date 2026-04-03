---
id: system
title: "TestSystem"
---

`TestSystem` supports deterministic testing of effects involving system properties. 

With the increased usage of containers and runtimes like Kubernetes, more and more applications are being configured by means of environment variables. It is important to test this logic just like other parts of an application.

For this purpose ZIO Test exposes `TestSystem` module. Additionally, to setting the _environment variables_. It also allows for setting _JVM system properties_ like in the code below:

```scala mdoc:compile-only
import zio._
import zio.test._
import zio.test.Assertion._

for {
  _      <- TestSystem.putProperty("java.vm.name", "VM")
  result <- System.property("java.vm.name")
} yield assertTrue(result == Some("VM"))
```

Internally, `TestSystem` maintains mappings of environment variables and system properties that can be set and accessed. It is worth noticing that no actual environment variables or system properties will be accessed or set as a result of these actions. So there will be no impact on other parts of the system.
