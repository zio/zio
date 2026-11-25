# java.util.logging bridge

> `java.util.logging` (JUL) is the logging package that ships with the JDK; do not confuse it with the Java Platform Logging API, `java.lang.System.Logger`, which has its own [backend](jpl.md). Some third-party libraries log through JUL, most notably the OpenTelemetry Java SDK used by ZIO Telemetry. The `zio-logging-jul-bridge` module adds a JUL `Handler` to the root JUL logger that forwards every JUL record to the ZIO loggers, so those logs get the same format, level filter and destination as your ZIO logs. The module requires Java 17 or later.

To use the bridge, add the module to your build:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-jul-bridge" % "2.5.5"
```

:::caution[Do not combine with the JPL backend]
With no `System.LoggerFinder` installed, the JDK's `System.Logger` writes to JUL. If the [JPL backend](jpl.md) is installed together with this bridge, ZIO lines go to `System.Logger`, then to JUL, then back through this bridge to the ZIO loggers, which include the JPL backend, so every line goes round in a loop. Use either the JPL backend or the JUL bridge, not both.
:::

## Installing the Bridge

Install the bridge by adding one of the `JULBridge` layers to the `bootstrap` layer of your application:

```scala
import zio.ZIO
import zio.logging.jul.bridge.JULBridge

object BridgeApp extends zio.ZIOAppDefault {
  override val bootstrap: zio.ZLayer[zio.ZIOAppArgs, Any, Any] = JULBridge.init()

  override def run: zio.ZIO[Any, Any, zio.ExitCode] = zio.ZIO.logInfo("Program output").as(zio.ExitCode.success)
}
```

`JULBridge` has the same entry points as the SLF4J bridges. The `JULBridge.init` and `JULBridge.initialize` layers are the usual choice. The `WithoutFiberRefPropagation` layers skip the per-fiber context described in [Fiber-Ref Propagation](#fiber-ref-propagation). The `JULBridge.make` effects install the bridge from inside a running program, using the runtime of the fiber that runs them:

| Entry point                                        | Type                              | Filter                                    | Fiber-ref propagation |
| -------------------------------------------------- | --------------------------------- | ----------------------------------------- | --------------------- |
| `JULBridge.init(configPath)`                       | `ZLayer[Any, Config.Error, Unit]` | from config, default path `logger.filter` | yes                   |
| `JULBridge.init(filter)`                           | `ZLayer[Any, Nothing, Unit]`      | the given `LogFilter[Any]`                | yes                   |
| `JULBridge.initialize`                             | `ZLayer[Any, Nothing, Unit]`      | none, every level is accepted             | yes                   |
| `JULBridge.initWithoutFiberRefPropagation(filter)` | `ZLayer[Any, Nothing, Unit]`      | the given `LogFilter[Any]`                | no                    |
| `JULBridge.initializeWithoutFiberRefPropagation`   | `ZLayer[Any, Nothing, Unit]`      | none, every level is accepted             | no                    |
| `JULBridge.make(configPath)`                       | `ZIO[Any, Config.Error, Unit]`    | from config, default path `logger.filter` | no                    |
| `JULBridge.make(filter)`                           | `ZIO[Any, Nothing, Unit]`         | the given `LogFilter[Any]`                | no                    |

The default config path is also available as the value `JULBridge.logFilterConfigPath`. When the config has no `rootLevel`, the root level is `INFO`. For the full API of every backend and bridge, see [Backends and Bridges](reference-integrations.md).

When the bridge's layer is built, it captures the current ZIO runtime, including the loggers installed at that moment, and every JUL record logged later goes to the loggers in that runtime. Build the logger layer first by putting it on the left of `>>>` or `>+>`, which build their left side first:

```scala
import zio.{ Runtime, ZLayer }
import zio.logging.consoleJsonLogger
import zio.logging.jul.bridge.JULBridge

val logger = Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> JULBridge.init()
```

## What Installing the Bridge Changes in JUL

Installing the bridge resets the global JUL configuration. It calls `LogManager#readConfiguration` with the single setting `.level= ALL`, which removes every handler and every level that was configured before, including the settings from `logging.properties` and the default `ConsoleHandler`, and sets the root level to `ALL`. It then adds its own handler to the root logger.

Two consequences follow. First, JUL no longer filters by level: every JUL logger creates a `LogRecord` for every level, and the bridge's `LogFilter` is the only level filter left, so `FINEST` logging in a chatty library costs a record per call even when the filter drops it. Second, any other JUL handler that your code or a library adds after the bridge receives records of every level. Before the bridge is installed, JUL works as configured, so records logged early in startup go to JUL's default handlers rather than to ZIO.

## Filtering Bridged Logs

The bridge runs its `LogFilter` on every record before forwarding it. The filter receives only the level and one annotation, `logger_name`, which holds the JUL logger name; it receives no message, cause, spans or fiber annotations, so a filter on fiber annotations or message text never matches in the bridge. After the bridge accepts a record, the line still passes through the filter of each ZIO logger, such as the console logger's filter, and those filters see the complete record.

The JUL logger name is stored in the log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`). The following extractor reads the logger name from that annotation, or from the ZIO `Trace` when the annotation is missing, and turns it into a log format:

```scala
import zio.logging.LoggerNameExtractor

val loggerName       = LoggerNameExtractor.loggerNameAnnotationOrTrace
val loggerNameFormat = loggerName.toLogFormat()
```

`LogFilter.LogLevelByNameConfig` uses the same extractor, so a filter built from it applies its levels by JUL logger name. The following filter keeps `INFO` and above by default, `DEBUG` for `zio.logging.jul`, and only `WARN` and above for the `JUL-LOGGER` logger:

```scala
import zio.LogLevel
import zio.logging.LogFilter

val logFilterConfig = LogFilter.LogLevelByNameConfig(
  LogLevel.Info,
  "zio.logging.jul" -> LogLevel.Debug,
  "JUL-LOGGER"      -> LogLevel.Warning
)

val logFilter: LogFilter[String] = logFilterConfig.toFilter
```

`JULBridge.init()` reads the same filter from config, using the `rootLevel` and `mappings` keys under `logger.filter`. Mapping keys contain dots, so quote them. The following HOCON keeps `INFO` and above, but only `WARN` and above for the OpenTelemetry SDK:

```hocon
logger {
  filter {
    rootLevel = INFO
    mappings {
      "io.opentelemetry" = WARN
    }
  }
}
```

The bridge loads this block with `ZIO.config`, from the `ConfigProvider` of the runtime, so install a provider such as `TypesafeConfigProvider` before the bridge. See [How to Set Log Levels Per Package From Config](how-to-log-levels-from-config.md) for a complete setup.

## Levels and Messages

Each JUL record becomes one ZIO log record. The record's logger name becomes the `logger_name` annotation and an extra log span named after the logger, and its `Throwable`, when there is one, becomes the cause as `Cause.die(throwable)`. JUL levels map to ZIO levels as follows:

| JUL `Level`     | ZIO `LogLevel` |
| --------------- | -------------- |
| `SEVERE`        | `Fatal`        |
| `WARNING`       | `Warning`      |
| `INFO`          | `Info`         |
| `CONFIG`        | `Info`         |
| `FINE`, `FINER` | `Debug`        |
| `FINEST`        | `Trace`        |
| `ALL`           | `All`          |
| `OFF`           | `None`         |

`SEVERE` maps to `Fatal`, not `Error`, so a library's `SEVERE` records appear as `FATAL` in your output and pass any filter set to `ERROR`. Levels outside this table, such as custom `Level` subclasses, are not supported: publishing such a record throws a `NoSuchElementException` in the calling code.

:::caution[Message parameters are not filled in]
The bridge uses the record's raw message. JUL parameters are not substituted, so `logger.log(Level.INFO, "User {0} logged in", userId)` is logged as `User {0} logged in`, and resource-bundle keys are not localized. Build the final message string before passing it to JUL where you control the call.
:::

## Fiber-Ref Propagation

A Java library that runs inside a ZIO effect logs on the thread of the calling fiber. Fiber-ref propagation lets the bridge read that fiber's state, so the library's lines carry the fiber's log annotations and log spans. The `JULBridge.init` and `JULBridge.initialize` layers turn it on by adding `Runtime.enableCurrentFiber`, which makes ZIO record in a thread-local which fiber is running on each thread; ZIO documents this runtime flag as having a performance cost. The `WithoutFiberRefPropagation` layers and the `JULBridge.make` effects do not enable it, and their lines carry only the logger name, its span, and what was set in the runtime that the bridge captured. Lines logged from threads that run no fiber never carry fiber annotations. The [SLF4J v2 bridge page](slf4j2-bridge.md#fiber-ref-propagation) describes the trade-off in more detail.

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples)

### JUL Bridge with JSON Console Logger

The following application logs through a JUL logger and a ZIO logger. The JUL logger name and the ZIO logger name appear in the JSON output, filtered by level:

```scala
import zio.logging.jul.bridge.JULBridge
import zio.logging.{ ConsoleLoggerConfig, LogAnnotation, LogFilter, LogFormat, LoggerNameExtractor, consoleJsonLogger }
import zio.{ ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

import java.util.UUID

object JULBridgeExampleApp extends ZIOAppDefault {

  private val julLogger = java.util.logging.Logger.getLogger("JUL-LOGGER")

  private val logFilterConfig = LogFilter.LogLevelByNameConfig(
    LogLevel.Info,
    "JUL-LOGGER" -> LogLevel.Warning
  )

  private val logFormat = LogFormat.label(
    "name",
    LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()
  ) + LogFormat.logAnnotation(LogAnnotation.UserId) + LogFormat.logAnnotation(
    LogAnnotation.TraceId
  ) + LogFormat.default

  private val loggerConfig = ConsoleLoggerConfig(logFormat, logFilterConfig)

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger(loggerConfig) >+> JULBridge.init(loggerConfig.toFilter)

  private val uuids = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.foreachPar(uuids) { u =>
        ZIO.succeed(julLogger.info("Test INFO!")) *> ZIO.succeed(
          julLogger.warning("Test WARNING!")
        ) @@ LogAnnotation.UserId(
          u.toString
        )
      } @@ LogAnnotation.TraceId(UUID.randomUUID())
      _ <- ZIO.logDebug("Done")
    } yield ExitCode.success

}
```

The `JUL-LOGGER` mapping drops the JUL `INFO` records, and the root level `INFO` drops the final `DEBUG` line. Expected console output:

```
{"name":"zio.logging.example.JULBridgeExampleApp","timestamp":"2024-05-26T13:50:20.683283+02:00","level":"INFO","thread":"zio-fiber-1143120685","message":"Start"}
{"name":"JUL-LOGGER","user_id":"85f762cc-e62c-4576-9f14-6a3ad0918d99","trace_id":"08e9e10a-d3c5-4f90-8627-2ae4ddee1522","timestamp":"2024-05-26T13:50:20.714288+02:00","level":"WARN","thread":"zio-fiber-1911711828","message":"Test WARNING!"}
{"name":"JUL-LOGGER","user_id":"47850c02-bb60-4b6a-9c0f-0aa095066d10","trace_id":"08e9e10a-d3c5-4f90-8627-2ae4ddee1522","timestamp":"2024-05-26T13:50:20.714288+02:00","level":"WARN","thread":"zio-fiber-1801412106","message":"Test WARNING!"}
```
