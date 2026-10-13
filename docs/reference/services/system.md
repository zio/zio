---
id: "system"
title: "System"
description: "Service providing access to environment variables, system properties, and platform-level information for application configuration."
keywords:
  - "System Service"
  - "Environment Variables"
  - "System Properties"
  - "Application Configuration"
  - "Platform Information"
  - "Configuration Access"
---

System service contains several useful functions related to system environments and properties. Both of **system environments** and **system properties** are key/value pairs. They are used to pass user-defined information to our application. It follows the shared [service pattern](./anatomy.md) common to all of ZIO's built-in services: a `System` trait, a `Live` implementation, and a synchronous `UnsafeAPI` for interop.

Environment variables are global operating system level variables available to all applications running on the same machine, while properties are application-level variables provided to our application. The two kinds of access fail differently: environment variable methods fail with `SecurityException` if a security manager denies access, while system property methods fail with the broader, unrefined `Throwable`. `lineSeparator` cannot fail at all (`UIO`).

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

`envOrElse` and `envOrOption` provide a fallback instead of an `Option`, and `envs` reads every environment variable as a `Map`:

```scala mdoc:compile-only
import zio._

for {
  logLevel   <- System.envOrElse("LOG_LEVEL", "INFO")
  logLevelOp <- System.envOrOption("LOG_LEVEL", Some("INFO"))
  all        <- System.envs
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

`propertyOrElse` and `propertyOrOption` provide a fallback instead of an `Option`, and `properties` reads every system property as a `Map`:

```scala mdoc:compile-only
import zio._

for {
  logLevel   <- System.propertyOrElse("log.level", "INFO")
  logLevelOp <- System.propertyOrOption("log.level", Some("INFO"))
  all        <- System.properties
} yield ()
```

## Operating System Detection

`System.os` reports which operating system the JVM is running on as a lazily-evaluated, cached `OS` value, derived from the `os.name` system property:

```scala
sealed trait OS {
  def isWindows: Boolean
  def isMac: Boolean
  def isUnix: Boolean
  def isSolaris: Boolean
  def isUnknown: Boolean
}
```

`OS` is a sealed trait with cases `OS.Windows`, `OS.Mac`, `OS.Unix`, `OS.Solaris`, and `OS.Unknown`, and each `is*` predicate simply checks equality against the corresponding case:

```scala mdoc:compile-only
import zio._

val describeOS: UIO[String] =
  ZIO.succeed(System.os).map {
    case os if os.isWindows => "Windows"
    case os if os.isMac     => "Mac"
    case os if os.isUnix    => "Unix"
    case os if os.isSolaris => "Solaris"
    case _                  => "Unknown"
  }
```

## Miscellaneous

With the `lineSeparator` method, we can determine the line separator for the underlying platform:

```scala mdoc
System.lineSeparator
```

## Synchronous Access (`unsafe`)

`System` also exposes a synchronous `UnsafeAPI`, following the pattern described in [Anatomy of a Built-in Service](./anatomy.md#synchronous-access-the-unsafeapi). `SystemLive`'s `unsafe` implementation delegates directly to `java.lang.System.getenv`/`getenv()` for `env`/`envs`, `java.lang.System.getProperty`/`getProperties()` for `property`/`properties`, and `java.lang.System.lineSeparator` for `lineSeparator`, without running an effect:

```scala mdoc:compile-only
import zio._

Unsafe.unsafe { implicit unsafe =>
  val user: Option[String] = System.SystemLive.unsafe.env("USER")
}
```

Prefer the ordinary `System.env`/`System.property`-style accessors everywhere else.

## Testing Environment/System Access

`TestSystem` lets tests set and clear in-memory environment variables and properties instead of touching the real process environment. See [Testing System](../test/services/system.md) for its `putEnv`/`putProperty`/`clearEnv`/`clearProperty`/`setLineSeparator` API.

## See Also

- [Built-in services](index.md) — Guide to ZIO's built-in services: Console, Clock, Random, and System with automatic environment management.
