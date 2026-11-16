# SLF4J v2 bridge

> It is possible to use `zio-logging` for SLF4J loggers, usually third-party non-ZIO libraries. To do so, import the `zio-logging-slf4j2-bridge` module for [SLF4J v2](https://www.slf4j.org/faq.html#changesInVersion200) (using JDK9+ module system ([JPMS](http://openjdk.java.net/projects/jigsaw/spec/))):

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.5.4"
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

SLF4J v2 [key-value pairs](https://www.slf4j.org/manual.html#fluent) are propagated like ZIO log annotations.

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

This logger name extractor is used by default in log filter, which applies log filtering by defined logger name and level:

```scala
import zio.LogLevel
import zio.logging.LogFilter

val logFilterConfig = LogFilter.LogLevelByNameConfig(
  LogLevel.Info,
  "zio.logging.slf4j" -> LogLevel.Debug,
  "SLF4J-LOGGER"      -> LogLevel.Warning
)

val logFilter: LogFilter[String] = logFilterConfig.toFilter
```

SLF4J bridge with a custom logger can be set up:

```scala
import zio.{ Runtime, ZLayer }
import zio.logging.consoleJsonLogger
import zio.logging.slf4j.bridge.Slf4jBridge

val logger = Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> Slf4jBridge.init()
```

**NOTE:** You should either use `zio-logging-slf4j2` to send all ZIO logs to an SLF4J provider (such as logback, log4j etc) OR `zio-logging-slf4j2-bridge` to send all SLF4J logs to
ZIO logging. Enabling both causes circular logging and makes no sense.

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples)

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
  ) + LogFormat.allAnnotations(Set(zio.logging.loggerNameAnnotationKey)) + LogFormat.default

  private val loggerConfig = ConsoleLoggerConfig(logFormat, logFilterConfig)

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger(loggerConfig) >+> Slf4jBridge.init(loggerConfig.toFilter)

  private val uuids = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.foreachPar(uuids) { u =>
        ZIO.succeed(slf4jLogger.info("Test {}!", "INFO")) *> ZIO.succeed(
          slf4jLogger.atWarn().addArgument("WARNING").log("Test {}!")
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
{"name":"zio.logging.example.Slf4jBridgeExampleApp","timestamp":"2024-05-28T08:05:48.056029+02:00","level":"INFO","thread":"zio-fiber-735317063","message":"Start"}
{"name":"SLF4J-LOGGER","trace_id":"416f3886-717c-4359-9f6e-c260e49fd158","user_id":"580376aa-f851-43a2-8b17-9456ce005e93","timestamp":"2024-05-28T08:05:48.065133+02:00","level":"WARN","thread":"zio-fiber-1749629949","message":"Test WARNING!"}
{"name":"SLF4J-LOGGER","trace_id":"416f3886-717c-4359-9f6e-c260e49fd158","user_id":"76d49e0d-7c28-47fd-b5a1-d05afb487cfe","timestamp":"2024-05-28T08:05:48.065174+02:00","level":"WARN","thread":"zio-fiber-697454589","message":"Test WARNING!"}
{"name":"zio.logging.example.Slf4jBridgeExampleApp","timestamp":"2024-05-28T08:05:48.068127+02:00","level":"DEBUG","thread":"zio-fiber-735317063","message":"Done"}
```
