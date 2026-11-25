# Java Platform/System Logger

> The [Java Platform Logging API](https://openjdk.org/jeps/264) (JPL), introduced in Java 9, is the `java.lang.System.Logger` interface of the JDK. The JDK routes it to `java.util.logging` by default, and logging frameworks can plug in their own implementation through `System.LoggerFinder`. The `zio-logging-jpl` module is a backend: it sends every ZIO log line to a `System.Logger`, so you get JDK-only logging without an SLF4J dependency. The module requires Java 17 or later.

In order to use this logging backend, we need to add the following line to our build.sbt file:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-jpl" % "2.5.5"
```

## Installing the Backend

To replace the default ZIO loggers with the `JPL` logger, combine `Runtime.removeDefaultLoggers` with `JPL.jpl` in a logger layer:

```scala
import zio.Runtime
import zio.logging.backend.JPL

val logger = Runtime.removeDefaultLoggers >>> JPL.jpl
```

With the default setup, each ZIO log line becomes one `System.Logger` call:

* The logger name comes from the `logger_name` annotation when it is set, and otherwise from the `zio.Trace` of the log call; for example, the trace `zio.logging.example.JplSimpleApp.run(JplSimpleApp.scala:17)` gives the logger name `zio.logging.example.JplSimpleApp`.
* All annotations, except the logger name, are written as `key=value` text at the beginning of the message, because `System.Logger` has no MDC or key-value pairs.
* The cause, when there is one, is passed as a `Throwable`: the first `Throwable` failure or defect of the cause, or a `zio.FiberFailure` that wraps the whole cause when it holds none.

To set a custom logger name for a single effect, apply the `zio.logging.loggerName` aspect:

```scala
import zio.ZIO

ZIO.logInfo("Starting user operation") @@ zio.logging.loggerName("zio.logging.example.UserOperation")
```

:::caution[Do not combine with the JUL bridge]
With no `System.LoggerFinder` installed, the JDK's `System.Logger` writes to `java.util.logging`. If the [JUL bridge](jul-bridge.md) is also installed, it forwards those records back to the ZIO loggers, which include this backend, so every line goes round in a loop. Use either this backend or the JUL bridge, not both.
:::

## Customizing the Backend

The no-argument `JPL.jpl` layer is enough for most applications. The overloads below change the format and the logger name, and the last part of this section explains which levels are written. For the complete API, see [Backends and Bridges](reference-integrations.md).

### Format and Logger Name

`JPL.jpl(format)` replaces the default format, `JPL.logFormatDefault`, which is `LogFormat.allAnnotations` (without the logger name key) followed by `LogFormat.line` and `LogFormat.cause`. Everything except the cause is written into the message text, and `LogFormat.cause` becomes the `Throwable`. `JPL.jpl(format, loggerName)` also takes the function that turns the `Trace` into a logger name when no `logger_name` annotation is set; the default one falls back to `"zio-jpl-logger"` for traces it cannot parse. There is no parameter for the cause conversion.

The following layer adds the fiber ID to every message, and names lines whose trace cannot be parsed `billing-service`:

```scala
import zio.{ FiberRefs, Runtime, Trace }
import zio.logging.{ LogFormat, LoggerNameExtractor }
import zio.logging.backend.JPL

val loggerNameOrService: Trace => String =
  trace => LoggerNameExtractor.trace(trace, FiberRefs.empty, Map.empty).getOrElse("billing-service")

val billingLogger =
  Runtime.removeDefaultLoggers >>> JPL.jpl(
    LogFormat.label("fiber", LogFormat.fiberId) + JPL.logFormatDefault,
    loggerNameOrService
  )
```

### Composing the Logger

`JPL.jplLogger(format, loggerName)` returns the underlying `ZLogger[String, Unit]` instead of a layer; `loggerName` is optional. Use it to wrap the logger before installing it, for example with a ZIO `LogFilter`:

```scala
import zio.{ LogLevel, Runtime }
import zio.logging.{ LogFilter, ZLoggerOps }
import zio.logging.backend.JPL

val filteredLogger =
  JPL.jplLogger(JPL.logFormatDefault).filter(LogFilter.logLevelByName[String](LogLevel.Info, "com.example.db" -> LogLevel.Warning))

val filteredLayer = Runtime.removeDefaultLoggers >>> Runtime.addLogger(filteredLogger)
```

### Which Levels Are Written

This backend applies no ZIO `LogFilter` of its own. For each line, it calls `System.Logger#isLoggable` with the mapped level, so the `System.Logger` implementation decides; with the JDK default, that is the `java.util.logging` configuration, such as `logging.properties`. ZIO levels map to `System.Logger.Level` as follows:

| ZIO `LogLevel` | `System.Logger.Level` |
| -------------- | --------------------- |
| `All`          | `ALL`                 |
| `Trace`        | `TRACE`               |
| `Debug`        | `DEBUG`               |
| `Info`         | `INFO`                |
| `Warning`      | `WARNING`             |
| `Error`        | `ERROR`               |
| `Fatal`        | `ERROR`               |
| `None`         | `OFF`                 |

Unlike the SLF4J backends, `None` is not dropped here: it is passed on as `OFF`, and whether such a line is written depends on the `System.Logger` implementation.

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples)

### Java Platform/System Logger Name and Annotations

The following application runs two user operations concurrently. Each operation carries a `user` annotation, and both share a trace ID. The `zio.logging.loggerName` aspect sets the logger name for the operations:

```scala
import zio.logging.LogAnnotation
import zio.logging.backend.JPL
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, ZIOAspect, ZLayer, _ }

import java.util.UUID

object JplSimpleApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Runtime.removeDefaultLoggers >>> JPL.jpl

  private val users = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _       <- ZIO.logInfo("Start")
      traceId <- ZIO.succeed(UUID.randomUUID())
      _       <- ZIO.foreachPar(users) { uId =>
        {
          ZIO.logInfo("Starting user operation") *>
            ZIO.sleep(500.millis) *>
            ZIO.logInfo("Stopping user operation")
        } @@ ZIOAspect.annotated("user", uId.toString)
      } @@ LogAnnotation.TraceId(traceId) @@ zio.logging.loggerName("zio.logging.example.UserOperation")
      _       <- ZIO.logInfo("Done")
    } yield ExitCode.success

}
```

Expected Console Output:
```
Oct 28, 2022 1:47:01 PM zio.logging.backend.JPL$$anon$1 $anonfun$closeLogEntry$1
INFO: Start
Oct 28, 2022 1:47:01 PM zio.logging.backend.JPL$$anon$1 $anonfun$closeLogEntry$1
INFO: user=59c114fd-676d-4df9-a5a0-b8e132468fbf trace_id=7d3e3b84-dd3b-44ff-915a-04fb2d135e28 Starting user operation
Oct 28, 2022 1:47:01 PM zio.logging.backend.JPL$$anon$1 $anonfun$closeLogEntry$1
INFO: user=e1ebf0cc-2f61-484f-afcd-de7e20ec7829 trace_id=7d3e3b84-dd3b-44ff-915a-04fb2d135e28 Starting user operation
Oct 28, 2022 1:47:02 PM zio.logging.backend.JPL$$anon$1 $anonfun$closeLogEntry$1
INFO: user=e1ebf0cc-2f61-484f-afcd-de7e20ec7829 trace_id=7d3e3b84-dd3b-44ff-915a-04fb2d135e28 Stopping user operation
Oct 28, 2022 1:47:02 PM zio.logging.backend.JPL$$anon$1 $anonfun$closeLogEntry$1
INFO: user=59c114fd-676d-4df9-a5a0-b8e132468fbf trace_id=7d3e3b84-dd3b-44ff-915a-04fb2d135e28 Stopping user operation
Oct 28, 2022 1:47:02 PM zio.logging.backend.JPL$$anon$1 $anonfun$closeLogEntry$1
INFO: Done
```

## Feature Changes

The following changes affect the logger name annotation of this backend.

### Version 2.2.0

The deprecated log annotation with key `jpl_logger_name` was removed.
The common log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`) is now the only supported annotation for the logger name.
