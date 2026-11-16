# SLF4J v1 bridge

> It is possible to use `zio-logging` for SLF4J loggers, usually third-party non-ZIO libraries. To do so, import the `zio-logging-slf4j-bridge` module for SLF4J v1 (working with JDK8):

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j-bridge" % "2.5.4"
```

Then use one of the `Slf4jBridge` layers when setting up logging. To provide the layer to a program:

```scala
import zio.ZIO
import zio.logging.slf4j.bridge.Slf4jBridge

object BridgeApp extends zio.ZIOAppDefault {
  override val bootstrap: zio.ZLayer[zio.ZIOAppArgs, Any, Any] = Slf4jBridge.init()

  override def run: zio.ZIO[Any, Any, zio.ExitCode] = zio.ZIO.logInfo("Program output").as(zio.ExitCode.success)
}
```

`Slf4jBridge` layers:
* `Slf4jBridge.init(configPath: NonEmptyChunk[String] = logFilterConfigPath)` - setup with `LogFilter` from [filter configuration](log-filter.md#configuration), default configuration path: `logger.filter`, default `LogLevel` is `INFO`
* `Slf4jBridge.init(filter: LogFilter[Any])` - setup with given `LogFilter`
* `Slf4jBridge.initialize` - setup without filtering

Need for log filtering in slf4j bridge: libraries with slf4j loggers, may have conditional logic for logging,
which using functions like [org.slf4j.Logger.isTraceEnabled()](https://github.com/qos-ch/slf4j/blob/master/slf4j-api/src/main/java/org/slf4j/Logger.java#L170).
logging parts may contain message and log parameters construction, which may be expensive and degrade performance of application.

SLF4J logger name is stored in log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`), following log format:

```scala
import zio.logging.LoggerNameExtractor

val loggerName       = LoggerNameExtractor.loggerNameAnnotationOrTrace
val loggerNameFormat = loggerName.toLogFormat()
```
This logger name extractor may be used to get logger name from log annotation or ZIO Trace.

SLF4J bridge with a custom logger can be set up:

```scala
import zio.{ Runtime, ZLayer }
import zio.logging.consoleJsonLogger
import zio.logging.slf4j.bridge.Slf4jBridge

val logger = Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> Slf4jBridge.init()
```

**NOTE:** You should either use `zio-logging-slf4j` to send all ZIO logs to an SLF4j provider (such as logback, log4j etc) OR `zio-logging-slf4j-bridge` to send all SLF4j logs to
ZIO logging. Enabling both causes circular logging and makes no sense.

## Examples

The following example shows the bridge in a complete application.

### SLF4J Bridge with JSON Console Logger

The following application logs through an SLF4J logger and a ZIO logger. The SLF4J logger name and the ZIO logger name appear in the JSON output, filtered by level:

```scala
import zio.logging.slf4j.bridge.Slf4jBridge
import zio.logging.{ ConsoleLoggerConfig, LogAnnotation, LogFilter, LogFormat, LoggerNameExtractor, consoleJsonLogger }
import zio.{ ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }

import java.util.UUID

object Slf4jBridgeExampleApp extends ZIOAppDefault {

  private val slf4jLogger = org.slf4j.LoggerFactory.getLogger("SLF4J-LOGGER")

  private val logFilterConfig = LogFilter.LogLevelByNameConfig(
    LogLevel.Info,
    "zio.logging.slf4j" -> LogLevel.Debug,
    "SLF4J-LOGGER"      -> LogLevel.Warning
  )

  private val logFormat = LogFormat.label(
    "name",
    LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()
  ) + LogFormat.logAnnotation(LogAnnotation.UserId) + LogFormat.logAnnotation(
    LogAnnotation.TraceId
  ) + LogFormat.default

  private val loggerConfig = ConsoleLoggerConfig(logFormat, logFilterConfig)

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger(loggerConfig) >+> Slf4jBridge.init(loggerConfig.toFilter)

  private val uuids = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.foreachPar(uuids) { u =>
        ZIO.succeed(slf4jLogger.info("Test {}!", "INFO")) *> ZIO.succeed(
          slf4jLogger.warn("Test {}!", "WARNING")
        ) @@ LogAnnotation.UserId(
          u.toString
        )
      } @@ LogAnnotation.TraceId(UUID.randomUUID())
      _ <- ZIO.logDebug("Done")
    } yield ExitCode.success

}
```

Expected Console Output:
```
{"name":"zio.logging.example.Slf4jBridgeExampleApp","timestamp":"2024-02-16T08:10:45.373807+01:00","level":"INFO","thread":"zio-fiber-6","message":"Start"}
{"name":"SLF4J-LOGGER","user_id":"d13f90ad-6b0a-45fd-bf94-1db7a0d8c0b7","trace_id":"561300a9-e6f1-4f61-8dcc-dfef476dab20","timestamp":"2024-02-16T08:10:45.421448+01:00","level":"WARN","thread":"zio-fiber-10","message":"Test WARNING!"}
{"name":"SLF4J-LOGGER","user_id":"0f28521f-ac8f-4d8e-beeb-13c85c90c041","trace_id":"561300a9-e6f1-4f61-8dcc-dfef476dab20","timestamp":"2024-02-16T08:10:45.421461+01:00","level":"WARN","thread":"zio-fiber-9","message":"Test WARNING!"}
{"name":"zio.logging.example.Slf4jBridgeExampleApp","timestamp":"2024-02-16T08:10:45.428162+01:00","level":"DEBUG","thread":"zio-fiber-6","message":"Done"}
```

## Feature Changes

The following changes affect the logger name annotation of this bridge.

### Version 2.1.9

SLF4J logger name is stored in log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`).
In previous versions, the logger name was stored in log annotation with key `slf4j_logger_name`.
`Slf4jBridge.initialize` takes no arguments, so no annotation key needs to be passed when setting it up.

The following log format gets the logger name from the log annotation with key `logger_name`, or from the ZIO `Trace` if that annotation is absent:

```scala
import zio.logging.LoggerNameExtractor

val loggerName       = LoggerNameExtractor.annotationOrTrace(zio.logging.loggerNameAnnotationKey)
val loggerNameFormat = loggerName.toLogFormat()
```

This logger name extractor can also be used in a log filter, which applies log filtering by defined logger name and level:

```scala
import zio.LogLevel
import zio.logging.LogFilter
import zio.logging.LoggerNameExtractor

val loggerName = LoggerNameExtractor.annotationOrTrace(zio.logging.loggerNameAnnotationKey)

val logFilter: LogFilter[String] = LogFilter.logLevelByGroup(
  LogLevel.Info,
  loggerName.toLogGroup(),
  "zio.logging.slf4j" -> LogLevel.Debug,
  "SLF4J-LOGGER"      -> LogLevel.Warning
)
```

### Version 2.2.0

The deprecated log annotation with key `slf4j_logger_name` was removed.
The common log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`) is now the only supported annotation for the logger name.
