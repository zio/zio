# java.util.logging bridge

> It is possible to use `zio-logging` for included `java.util.logging` Loggers (do not confuse with `java.platform.logging`),
usually third-party non-ZIO libraries (most notable: OpenTelemetry used by ZIO-telemetry). To do so, import the `zio-logging-jul-bridge` module
```scala
libraryDependencies += "dev.zio" %% "zio-logging-jul-bridge" % "2.5.3"
```

It is possible to use `zio-logging` for included `java.util.logging` Loggers (do not confuse with `java.platform.logging`),
usually third-party non-ZIO libraries (most notable: OpenTelemetry used by ZIO-telemetry). To do so, import the `zio-logging-jul-bridge` module
```scala
libraryDependencies += "dev.zio" %% "zio-logging-jul-bridge" % "2.5.3"
```

and use one of the `JULBridge` layers when setting up logging

```scala

program.provideCustom(JULBridge.init())
```

`JULBridge` layers:
* `JULBridge.init(configPath: NonEmptyChunk[String] = logFilterConfigPath)` - setup with `LogFilter` from [filter configuration](log-filter.md#configuration), default configuration path: `logger.filter`, default `LogLevel` is `INFO`
* `JULBridge.init(filter: LogFilter[Any])` - setup with given `LogFilter`
* `JULBridge.initialize` - setup without filtering

Need for log filtering in JUL bridge: filtering in JUL is made on higher level than `jul-bridge` (on `Logger` level and not `Handler` level - which `JULBridge` is). Due to that the whole
filtering in JUL is disabled and is implemented in JULBridge. This may cause degraded performance and much more logs when using other Handlers.

JUL logger name is stored in log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`), following log format

```scala

val loggerName = LoggerNameExtractor.loggerNameAnnotationOrTrace
val loggerNameFormat = loggerName.toLogFormat()
```
may be used to get logger name from log annotation or ZIO Trace.

This logger name extractor is used by default in log filter, which applying log filtering by defined logger name and level:

```scala
val logFilterConfig = LogFilter.LogLevelByNameConfig(
  LogLevel.Info,
  "zio.logging.jul  " -> LogLevel.Debug,
  "JUL-LOGGER"        -> LogLevel.Warning
)

val logFilter: LogFilter[String] = logFilterConfig.toFilter
```

JUL bridge with custom logger can be setup:

```scala

val logger = Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> JULBridge.init()
```

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples)

### JUL bridge with JSON console logger

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

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
