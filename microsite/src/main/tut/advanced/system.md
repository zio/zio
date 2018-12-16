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

## Current system time

```tut
// Current time as java.time.Instant
system.currentTime

// Current time in milliseconds since Jan 1, 1970 (accuracy depends on underlying system)
system.currentTimeMillis

// Returns the current value of the running Java Virtual Machine's high-resolution time source
// It is not related to any other notion of system or wall-clock time
system.nanoTime
```

## Environment / Properties

```tut
// Read an environment variable
system.env("JAVA_HOME")

// Read a system property
system.property("java.version")

// System's line separator
system.lineSeparator
```
