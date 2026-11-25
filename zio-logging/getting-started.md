# Getting Started with ZIO Logging

> This page shows how to add ZIO Logging to a project, which extra module to pick for your setup, and how to run a first program that writes its log lines through a ZIO Logging logger.

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

The core module holds the [console loggers](console-logger.md), the [file loggers](file-logger.md), the [reconfigurable logger](reconfigurable-logger.md), [log formats](formatting-log-records.md), [log filters](log-filter.md) and [log metrics](metrics.md). Many applications need nothing else. See [Platforms](index.md#platforms) for what works on JVM, Scala.js and Scala Native.

## Choosing Extra Modules

The other modules connect ZIO Logging to Java logging libraries. All of them are JVM only. Pick a module by the direction the log lines travel:

- A **backend** sends the log lines of your ZIO code to a Java logging library, which then formats and writes them.
- A **bridge** does the opposite: it takes the log lines that Java libraries write and sends them to your ZIO Logging loggers.

| Module                      | Kind    | Use it when                                                                                                     | Page                                  |
| --------------------------- | ------- | --------------------------------------------------------------------------------------------------------------- | ------------------------------------- |
| `zio-logging-slf4j2`        | backend | your project already writes logs through SLF4J 2 (for example with Logback 1.3+ or Log4j 2), and ZIO logs should go there too | [SLF4J v2](slf4j2.md)                  |
| `zio-logging-slf4j`         | backend | the same, with an SLF4J 1.x setup                                                                               | [SLF4J v1](slf4j1.md)                  |
| `zio-logging-jpl`           | backend | your project writes logs through the Java Platform/System Logger (`System.Logger`)                              | [Java Platform/System Logger](jpl.md) |
| `zio-logging-slf4j2-bridge` | bridge  | a library you depend on logs through SLF4J 2, and its lines should reach your ZIO loggers                       | [SLF4J v2 bridge](slf4j2-bridge.md)   |
| `zio-logging-slf4j-bridge`  | bridge  | the same, for a library that logs through SLF4J 1.x                                                             | [SLF4J v1 bridge](slf4j1-bridge.md)   |
| `zio-logging-jul-bridge`    | bridge  | a library you depend on logs through `java.util.logging` (JUL), and its lines should reach your ZIO loggers     | [java.util.logging bridge](jul-bridge.md) |

Add the line for each module you need:

```scala
// Backends: send ZIO log lines to a Java logging library
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2" % "2.5.5" // SLF4J v2
libraryDependencies += "dev.zio" %% "zio-logging-slf4j"  % "2.5.5" // SLF4J v1
libraryDependencies += "dev.zio" %% "zio-logging-jpl"    % "2.5.5" // Java Platform/System Logger

// Bridges: send Java library log lines to ZIO Logging
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.5.5" // SLF4J v2
libraryDependencies += "dev.zio" %% "zio-logging-slf4j-bridge"  % "2.5.5" // SLF4J v1
libraryDependencies += "dev.zio" %% "zio-logging-jul-bridge"    % "2.5.5" // java.util.logging
```

:::caution
Do not combine a backend and a bridge for the same logging system in one application. For example, the SLF4J backend sends ZIO log lines to SLF4J, and the SLF4J bridge sends SLF4J log lines back to ZIO, so each line goes round in a loop. Also pick either the v1 or the v2 variant of a module, never both. [How They Work Together](reference-integrations.md#how-they-work-together) lists the combinations.
:::

## Your First Program

ZIO already logs through `ZIO.logInfo`, `ZIO.logError` and the other `ZIO.log*` methods. ZIO Logging does not change those calls. It provides loggers that decide how the log lines look and where they go.

Install the logger in the application's `bootstrap` layer. The logger then serves the whole application runtime, so the failure at the end of the program below is logged with it too:

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

The `bootstrap` layer does two things:

1. `Runtime.removeDefaultLoggers` removes ZIO's built-in logger. Without this step, every line is printed twice: once by ZIO's default logger and once by the console logger.
2. `consoleLogger()` adds the ZIO Logging console logger. With no arguments, it reads its settings from the `logger` path of the application's configuration. When nothing is configured there, it uses the default format and logs at level `Info` and above. [Configuration](reference-configuration.md) lists every setting.

Running the program prints lines like these:

```
timestamp=2023-03-15T08:36:24.421098+01:00 level=INFO thread=zio-fiber-4 message="Start"
timestamp=2023-03-15T08:36:24.440181+01:00 level=ERROR thread=zio-fiber-0 message="" cause=Exception in thread "zio-fiber-4" java.lang.String: FAILURE
	at zio.logging.example.SimpleApp.run(SimpleApp.scala:29)
```

`"Done"` never appears, because `ZIO.fail` ends the program before it. The `ERROR` line comes from the ZIO runtime, which logs the failure through the same console logger.

## Next Steps

- Follow the [ZIO Logging Basics](tutorial.md) tutorial to learn log levels, annotations and JSON output step by step.
- Read [Console Logger](console-logger.md) to change the format, the level or the output stream.
- Browse the [Logger Layers reference](reference-logger-layers.md) for every logger ZIO Logging provides.
- Find task-focused recipes, such as [JSON logs](how-to-json-logs.md) or [correlation IDs](how-to-correlation-id.md), under How-to Guides.
- See the [examples](https://github.com/zio/zio-logging/tree/master/examples) folder for complete programs.
