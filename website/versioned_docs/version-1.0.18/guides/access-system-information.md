---
id: access-system-information
title: "How to Access System Information?"
---

Sometimes, environment variables are relevant information to an application. ZIO provides a `system` package to interface with this functionality.

```scala
import zio.system
```

## Environment Variables

With the `env` method, you can safely read an environment variable:

```scala
// Read an environment variable
system.env("JAVA_HOME")
// res0: zio.ZIO[system.package.System, SecurityException, Option[String]] = zio.ZIO$Read@2111d7b9
```

## Properties

With the `property` method, you can safely access Java properties:

```scala
// Read a system property
system.property("java.version")
// res1: zio.ZIO[system.package.System, Throwable, Option[String]] = zio.ZIO$Read@7a023e34
```

## Miscellaneous

With the `lineSeparator` method, you can determine the line separator for the underlying platform:

```scala
system.lineSeparator
// res2: zio.package.URIO[system.package.System, String] = zio.ZIO$Read@260f05ee
```
