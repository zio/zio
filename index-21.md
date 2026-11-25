# Introduction to ZIO Logging

> [ZIO Logging](https://github.com/zio/zio-logging) is simple logging for ZIO apps, with correlation, context, and pluggable backends out of the box with integrations for common logging backends.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-logging/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-logging_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-logging_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-logging_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-logging_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-logging_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-logging_2.13) [![zio-logging](https://img.shields.io/github/stars/zio/zio-logging?style=social)](https://github.com/zio/zio-logging)

## Introduction

When we are writing our applications using ZIO effects, to log easy way we need a ZIO native solution for logging.

Key features of ZIO Logging:

- **ZIO Native** — Other than it is a type-safe and purely functional solution, it leverages ZIO's features.
- **Multi-Platform** - The core module supports JVM, JS and Native. File loggers are JVM and Native only, and all other modules are JVM only. See [Platforms](#platforms).
- **Composable** — Loggers are composable together via contraMap.
- **Pluggable Backends** — Support multiple backends like ZIO Console, SLF4j, JPL.
- **Logger Context** — It has a first citizen _Logger Context_ implemented on top of `FiberRef`. The Logger Context maintains information like logger name, filters, correlation id, and so forth across different fibers. It supports _Mapped Diagnostic Context (MDC)_ which manages contextual information across fibers in a concurrent environment.
- Richly integrated into ZIO 2's built-in logging facilities
- ZIO Console, SLF4j, and other backends

## Installation

Add the core module to your `build.sbt`:

```scala
// ZIO Logging core module (JVM)
libraryDependencies += "dev.zio" %% "zio-logging" % "2.5.5"
```

The core module is also published for Scala.js and Scala Native. In a Scala.js or Scala Native project, or in a cross-built project, use `%%%` instead of `%%`:

```scala
// ZIO Logging core module (JVM, Scala.js or Scala Native)
libraryDependencies += "dev.zio" %%% "zio-logging" % "2.5.5"
```

[Getting Started](getting-started.md) explains the core module and when to add the modules below.

## Modules

These modules are optional. Each one connects ZIO Logging to a Java logging library. Add only the ones you need.

### Backends

A backend sends the log lines of your ZIO code to a Java logging library, which formats and writes them. All backend modules are JVM only.

```scala
// SLF4J 2 backend (JVM)
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2" % "2.5.5"

// SLF4J 1.x backend (JVM)
libraryDependencies += "dev.zio" %% "zio-logging-slf4j" % "2.5.5"

// Java Platform/System Logger backend (JVM)
libraryDependencies += "dev.zio" %% "zio-logging-jpl" % "2.5.5"
```

[SLF4J v2](slf4j2.md), [SLF4J v1](slf4j1.md), and [Java Platform/System Logger](jpl.md) describe these modules.

### Bridges

A bridge does the opposite: it sends the log lines that Java libraries write to your ZIO Logging loggers. All bridge modules are JVM only.

```scala
// SLF4J 2 bridge (JVM)
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.5.5"

// SLF4J 1.x bridge (JVM)
libraryDependencies += "dev.zio" %% "zio-logging-slf4j-bridge" % "2.5.5"

// java.util.logging bridge (JVM)
libraryDependencies += "dev.zio" %% "zio-logging-jul-bridge" % "2.5.5"
```

[SLF4J v2 bridge](slf4j2-bridge.md), [SLF4J v1 bridge](slf4j1-bridge.md), and [java.util.logging bridge](jul-bridge.md) describe these modules. Do not combine a backend and a bridge for the same logging system in one application. See [How They Work Together](reference-integrations.md#how-they-work-together).

## Platforms

ZIO Logging is published for JVM, JS and Native. Which parts work on each platform depends on where the code lives in the source tree:

| Feature                                                                                            | JVM | JS  | Native | Why                                                |
| -------------------------------------------------------------------------------------------------- | --- | --- | ------ | -------------------------------------------------- |
| Console loggers (`consoleLogger`, `consoleJsonLogger`, `consoleErrLogger`, `consoleErrJsonLogger`) | yes | yes | yes    | shared code, writes to `System.out` / `System.err` |
| File loggers (`fileLogger`, `fileJsonLogger`, `fileAsyncLogger`, `fileAsyncJsonLogger`)            | yes | no  | yes    | JVM and Native code only                           |
| Log metrics (`logMetrics`), reconfigurable logger                                                  | yes | yes | yes    | shared code                                        |
| `LogFormat`, `LogFilter`, `LogAnnotation`                                                          | yes | yes | yes    | shared code                                        |
| SLF4J, SLF4J bridges, java.util.logging bridge, JPL                                                | yes | no  | no     | separate JVM-only modules                          |

## Example

Let's try an example of ZIO Logging which demonstrates a simple application of ZIO logging.

The recommended place for setting the logger is the application bootstrap. The logger is then set for the whole application runtime, so application failures are also logged with it. The following application installs the console logger in its bootstrap:

```scala
import zio.logging.consoleLogger
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

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

[Getting Started](getting-started.md) explains this program step by step. You can find the source code of more examples [here](https://github.com/zio/zio-logging/tree/master/examples).
