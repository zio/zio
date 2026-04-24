# Introduction to ZIO Logging

> [ZIO Logging](https://github.com/zio/zio-logging) is simple logging for ZIO apps, with correlation, context, and pluggable backends out of the box with integrations for common logging backends.

[ZIO Logging](https://github.com/zio/zio-logging) is simple logging for ZIO apps, with correlation, context, and pluggable backends out of the box with integrations for common logging backends.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-logging/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-logging_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-logging_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-logging_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-logging_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-logging-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-logging-docs_2.13) [![zio-logging](https://img.shields.io/github/stars/zio/zio-logging?style=social)](https://github.com/zio/zio-logging)

## Introduction

When we are writing our applications using ZIO effects, to log easy way we need a ZIO native solution for logging.

Key features of ZIO Logging:

- **ZIO Native** — Other than it is a type-safe and purely functional solution, it leverages ZIO's features.
- **Multi-Platform** - It supports JS, JVM and Native platforms for the core zio-logging module, all other modules are only available for JVM.
- **Composable** — Loggers are composable together via contraMap.
- **Pluggable Backends** — Support multiple backends like ZIO Console, SLF4j, JPL.
- **Logger Context** — It has a first citizen _Logger Context_ implemented on top of `FiberRef`. The Logger Context maintains information like logger name, filters, correlation id, and so forth across different fibers. It supports _Mapped Diagnostic Context (MDC)_ which manages contextual information across fibers in a concurrent environment.
- Richly integrated into ZIO 2's built-in logging facilities
- ZIO Console, SLF4j, and other backends

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
// ZIO Logging backends
libraryDependencies += "dev.zio" %% "zio-logging" % "2.5.3"
```

The main module contains the following features: 
* [console loggers](console-logger.md)
* [file loggers](file-logger.md)
* [log metrics](metrics.md)

Other modules:

* SLF4J backend - if you like to use [`SLF4J`](https://www.slf4j.org/) logging backends (e.g. java.util.logging, logback, log4j), add the one of following lines to your `build.sbt` file:

    ```scala
    // SLF4j v1 integration
    libraryDependencies += "dev.zio" %% "zio-logging-slf4j" % "2.5.3"
    
    // SLF4j v2 integration
    libraryDependencies += "dev.zio" %% "zio-logging-slf4j2" % "2.5.3"
    ```
   When to use this module: you are already using SLF4J logger in some other project, and you like to have same log outputs. 
   See SLF4J [v2](slf4j2.md) or [v1](slf4j1.md) section for more details.

* SLF4J bridge - with this logging bridge, it is possible to use `zio-logging` for SLF4J loggers (usually third-party non-ZIO libraries), add the one of following lines to your `build.sbt` file: 

    ```scala
    // Using ZIO Logging for SLF4j v1 loggers, usually third-party non-ZIO libraries
    libraryDependencies += "dev.zio" %% "zio-logging-slf4j-bridge" % "2.5.3"
    
    // Using ZIO Logging for SLF4j v2 loggers, usually third-party non-ZIO libraries
    libraryDependencies += "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.5.3"
    ```

    When to use this module: you want to use some zio-logger implementation, but also you are using some java library which using SLF4J interface for logging.
    See SLF4J bridge [v2](slf4j2-bridge.md) or [v1](slf4j1-bridge.md) section for more details.

* Java Platform/System Logger backend - if you like to use  [`Java Platform/System Logger`](https://openjdk.org/jeps/264) logging backend, add the following line to your `build.sbt` file:

    ```scala
    // JPL integration
    libraryDependencies += "dev.zio" %% "zio-logging-jpl" % "2.5.3"
    ```

    When to use this module: you are already using Java Platform/System Logger in some other project, and you like to have same log outputs.
    See [Java Platform/System Logger](jpl.md) section for more details.

* java.util.logging bridge - with this logging bridge, it is possible to use `zio-logging` for JUL loggers (usually third-party non-ZIO libraries), add the one of following lines to your `build.sbt` file:

    ```scala
    // JUL bridge
    libraryDependencies += "dev.zio" %% "zio-logging-jul-bridge" % "2.5.3"
    ```

    When to use this module: you are already using JUL logger in some other project, and you like to have same log outputs.
    See [java.util.logging bridge](jul-bridge.md) section for more details.

## Example

Let's try an example of ZIO Logging which demonstrates a simple application of ZIO logging.

The recommended place for setting the logger is application boostrap. In this case, custom logger will be set for whole application runtime (also application failures will be logged with specified logger).

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

object SimpleApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger()

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.fail("FAILURE")
      _ <- ZIO.logInfo("Done")
    } yield ExitCode.success

}
```

Expected console output:

```
timestamp=2023-03-15T08:36:24.421098+01:00 level=INFO thread=zio-fiber-4 message="Start"
timestamp=2023-03-15T08:36:24.440181+01:00 level=ERROR thread=zio-fiber-0 message="" cause=Exception in thread "zio-fiber-4" java.lang.String: FAILURE
	at zio.logging.example.SimpleApp.run(SimpleApp.scala:29)
```

You can find the source code of examples [here](https://github.com/zio/zio-logging/tree/master/examples)
