---
layout: docs
section: advanced
title: "System"
---

# {{page.title}}

Sometimes, environment variables are relevant information to an application. ZIO provides a `system` package to interface with this functionality.

```scala mdoc:silent
import scalaz.zio.system
```

## Environment Variables

With the `env` method, you can safely read an environment variable:

```scala mdoc
// Read an environment variable
system.env("JAVA_HOME")
```

## Properties

With the `property` method, you can safely access Java properties:

```scala mdoc
// Read a system property
system.property("java.version")
```

## Miscellaneous

With the `lineSeparator` method, you can determine the line separator for the underlying platform:

```scala mdoc
system.lineSeparator
```
