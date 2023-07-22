---
id: system
title: "System"
---

System service contains several useful functions related to system environments and properties. Both of **system environments** and **system properties** are key/value pairs. They used to pass user-defined information to our application.

Environment variables are global operating system level variables available to all applications running on the same machine while the properties are application-level variables provided to our application.

## System Environment
The `env` function retrieve the value of an environment variable:

```scala
import zio.console._
import zio.system._
for {
  user <- env("USER")
  _ <- user match {
    case Some(value) => putStr(s"The USER env is: $value")
    case None => putStr("Oops! The USER env is not set")
  }
} yield ()
```

## System Property
Also, the System service has a `property function to retrieve the value of a system property:

```scala
for {
  user <- property("LOG_LEVEL")
  _ <- user match {
    case Some(value) => putStr(s"The LOG_LEVEL property is: $value")
    case None => putStr("Oops! The LOG_LEVEL property is not set")
  }
} yield ()
```
