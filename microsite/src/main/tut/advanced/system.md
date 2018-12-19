---
layout: docs
section: advanced
title: "System"
---

# {{page.title}}

Sometimes, environment variables or the current system time are relevant information to an application.
ZIO provides a `system` package to interface with this functionality.

```tut:silent
import scalaz.zio.system
```

## Time

With `currentTime`, you can retrieve the current time as a `java.time.Instant`:

```tut
//
system.currentTime
```

If you need the current time in milliseconds since January 1, 1970, then you can use the `currentTimeMillis` method:

```tut
system.currentTimeMillis
```

Finally, if you need a high-resolution time source, not connected to system or wall-clock time, then you can use `nanoTime`:

```tut
system.nanoTime
```

## Environment Variables

With the `env` method, you can safely read an environment variable:

```tut
// Read an environment variable
system.env("JAVA_HOME")
```

## Properties

With the `property` method, you can safely access Java properties:

```tut
// Read a system property
system.property("java.version")
```

## Miscellaneous

With the `lineSeparator` method, you can determine the line separator for the underlying platform:

```tut
system.lineSeparator
```
