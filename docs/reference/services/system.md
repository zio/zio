---
id: system
title: "System"
---

System service contains several useful functions related to system environments and properties. Both of **system environments** and **system properties** are key/value pairs. They are used to pass user-defined information to our application.

Environment variables are global operating system level variables available to all applications running on the same machine, while properties are application-level variables provided to our application.

## System Environment
The `env` function retrieves the value of an environment variable:

```scala mdoc:compile-only
import zio._

for {
  user <- System.env("USER")
  _    <- user match {
            case Some(value) => 
              Console.printLine(s"The USER env is: $value")
            case None        => 
              Console.printLine("Oops! The USER env is not set")
          }
} yield ()
```

## System Property
Also, the System service has a `property` function to retrieve the value of a system property:

```scala mdoc:compile-only
import zio._

for {
  user <- System.property("LOG_LEVEL")
  _    <- user match {
           case Some(value) => 
             Console.printLine(s"The LOG_LEVEL property is: $value")
           case None => 
             Console.printLine("Oops! The LOG_LEVEL property is not set")
         }
} yield ()
```

## Miscellaneous

With the `lineSeparator` method, we can determine the line separator for the underlying platform:

```scala mdoc
System.lineSeparator
```
