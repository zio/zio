# java.util.logging bridge

> It is possible to use `zio-logging` for included `java.util.logging` Loggers (do not confuse with `java.platform.logging`),
> usually third-party non-ZIO libraries (most notable: OpenTelemetry used by ZIO-telemetry). To do so, import the `zio-logging-jul-bridge` module:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-jul-bridge" % "2.5.4"
```

Then use one of the `JULBridge` layers when setting up logging. To provide the layer to a program:

```scala
import zio.ZIO
import zio.logging.jul.bridge.JULBridge

object BridgeApp extends zio.ZIOAppDefault {
  override val bootstrap: zio.ZLayer[zio.ZIOAppArgs, Any, Any] = JULBridge.init()

  override def run: zio.ZIO[Any, Any, zio.ExitCode] = zio.ZIO.logInfo("Program output").as(zio.ExitCode.success)
}
```

`JULBridge` layers:
* `JULBridge.init(configPath: NonEmptyChunk[String] = logFilterConfigPath)` - setup with `LogFilter` from [filter configuration](log-filter.md#configuration), default configuration path: `logger.filter`, default `LogLevel` is `INFO`
* `JULBridge.init(filter: LogFilter[Any])` - setup with given `LogFilter`
* `JULBridge.initialize` - setup without filtering

Need for log filtering in JUL bridge: filtering in JUL is made on higher level than `jul-bridge` (on `Logger` level and not `Handler` level - which `JULBridge` is). Due to that the whole
filtering in JUL is disabled and is implemented in JULBridge. This may cause degraded performance and much more logs when using other Handlers.

JUL logger name is stored in log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`), following log format:

```scala
import zio.logging.LoggerNameExtractor

val loggerName       = LoggerNameExtractor.loggerNameAnnotationOrTrace
val loggerNameFormat = loggerName.toLogFormat()
```
This logger name extractor may be used to get logger name from log annotation or ZIO Trace.

This logger name extractor is used by default in log filter, which applies log filtering by defined logger name and level:

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

JUL bridge with a custom logger can be set up:

```scala
import zio.{ Runtime, ZLayer }
import zio.logging.consoleJsonLogger
import zio.logging.jul.bridge.JULBridge

val logger = Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> JULBridge.init()
```

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

Expected console output:
```
{"name":"zio.logging.example.JULbridgeExampleApp","timestamp":"2024-05-26T13:50:20.6832831+02:0","level":"INFO","thread":"zio-fiber-1143120685","message":"Start"}
{"name":"JUL-LOGGER","trace_id":"08e9e10a-d3c5-4f90-8627-2ae4ddee1522","timestamp":"2024-05-26T13:50:20.7112909+02:0","level":"INFO","thread":"zio-fiber-1683803358","message":"Test INFO!"}
{"name":"JUL-LOGGER","trace_id":"08e9e10a-d3c5-4f90-8627-2ae4ddee1522","timestamp":"2024-05-26T13:50:20.7112909+02:0","level":"INFO","thread":"zio-fiber-71852457","message":"Test INFO!"}
{"name":"JUL-LOGGER","user_id":"85f762cc-e62c-4576-9f14-6a3ad0918d99","trace_id":"08e9e10a-d3c5-4f90-8627-2ae4ddee1522","timestamp":"2024-05-26T13:50:20.7142882+02:0","level":"WARN","thread":"zio-fiber-1911711828","message":"Test WARNING!"}
{"name":"JUL-LOGGER","user_id":"47850c02-bb60-4b6a-9c0f-0aa095066d10","trace_id":"08e9e10a-d3c5-4f90-8627-2ae4ddee1522","timestamp":"2024-05-26T13:50:20.7142882+02:0","level":"WARN","thread":"zio-fiber-1801412106","message":"Test WARNING!"}
```
