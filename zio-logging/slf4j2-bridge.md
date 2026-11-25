# SLF4J v2 bridge

> Many third-party Java libraries log through [SLF4J](https://www.slf4j.org/). The `zio-logging-slf4j2-bridge` module is an SLF4J provider for [SLF4J v2](https://www.slf4j.org/faq.html#changesInVersion200) (`slf4j-api` 2.x) that forwards those log calls to the ZIO loggers, so library logs get the same format, level filter and destination as your ZIO logs. The module requires Java 17 or later. It also ships a JPMS module descriptor, module `org.slf4j.zio`, which provides `org.slf4j.spi.SLF4JServiceProvider` with `zio.logging.slf4j.bridge.ZioSLF4JServiceProvider`.

To use the bridge, add the module to your build:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.5.5"
```

:::warning
Use either `zio-logging-slf4j2`, which sends ZIO logs to an SLF4J provider such as Logback or Log4j 2, or `zio-logging-slf4j2-bridge`, which sends SLF4J logs to ZIO Logging. Enabling both makes every line go round in a loop.
:::

## Installing the Bridge

Install the bridge by adding one of the `Slf4jBridge` layers to the `bootstrap` layer of your application:

```scala
import zio.ZIO
import zio.logging.slf4j.bridge.Slf4jBridge

object BridgeApp extends zio.ZIOAppDefault {
  override val bootstrap: zio.ZLayer[zio.ZIOAppArgs, Any, Any] = Slf4jBridge.init()

  override def run: zio.ZIO[Any, Any, zio.ExitCode] = zio.ZIO.logInfo("Program output").as(zio.ExitCode.success)
}
```

`Slf4jBridge` has three groups of entry points. The `Slf4jBridge.init` and `Slf4jBridge.initialize` layers are the usual choice. The `WithoutFiberRefPropagation` layers skip the per-fiber context described in [Fiber-Ref Propagation](#fiber-ref-propagation). The `Slf4jBridge.make` effects install the bridge from inside a running program, using the runtime of the fiber that runs them:

| Entry point                                          | Type                              | Filter                                    | Fiber-ref propagation |
| ---------------------------------------------------- | --------------------------------- | ----------------------------------------- | --------------------- |
| `Slf4jBridge.init(configPath)`                       | `ZLayer[Any, Config.Error, Unit]` | from config, default path `logger.filter` | yes                   |
| `Slf4jBridge.init(filter)`                           | `ZLayer[Any, Nothing, Unit]`      | the given `LogFilter[Any]`                | yes                   |
| `Slf4jBridge.initialize`                             | `ZLayer[Any, Nothing, Unit]`      | none, every level is accepted             | yes                   |
| `Slf4jBridge.initWithoutFiberRefPropagation(filter)` | `ZLayer[Any, Nothing, Unit]`      | the given `LogFilter[Any]`                | no                    |
| `Slf4jBridge.initializeWithoutFiberRefPropagation`   | `ZLayer[Any, Nothing, Unit]`      | none, every level is accepted             | no                    |
| `Slf4jBridge.make(configPath)`                       | `ZIO[Any, Config.Error, Unit]`    | from config, default path `logger.filter` | no                    |
| `Slf4jBridge.make(filter)`                           | `ZIO[Any, Nothing, Unit]`         | the given `LogFilter[Any]`                | no                    |

The default config path is also available as the value `Slf4jBridge.logFilterConfigPath`. When the config has no `rootLevel`, the root level is `INFO`. For the full API of every backend and bridge, see [Backends and Bridges](reference-integrations.md).

When the bridge's layer is built, it captures the current ZIO runtime, including the loggers installed at that moment. Every line that a Java library logs later goes to the loggers in that captured runtime, in particular lines from threads that do not run a ZIO fiber. The logger layer must therefore be built before the bridge layer. The `>>>` and `>+>` operators build their left side first, so put the logger on the left and the bridge on the right:

```scala
import zio.{ Runtime, ZLayer }
import zio.logging.consoleJsonLogger
import zio.logging.slf4j.bridge.Slf4jBridge

val logger = Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> Slf4jBridge.init()
```

Install the bridge early in `bootstrap`. Until the bridge is initialized, it reports every level as disabled and discards every call, so lines that libraries log before that point, for example while another layer starts up, are lost without a warning.

## Filtering Bridged Logs

Java libraries often guard expensive log statements with checks such as [`org.slf4j.Logger#isDebugEnabled`](https://www.slf4j.org/api/org/slf4j/Logger.html#isDebugEnabled()) before they build the message and its arguments. The bridge answers those checks with its `LogFilter`, so a level that the filter rejects costs the library almost nothing. Without a filter (`Slf4jBridge.initialize`), every check answers `true`, and libraries build every `TRACE` and `DEBUG` message only for the ZIO loggers to drop it later.

### What the Filter Can See

The bridge's filter runs inside `org.slf4j.Logger#isDebugEnabled` and its siblings, before the message exists. It receives only the level and one annotation, `logger_name`, which holds the SLF4J logger name; it receives no trace, message, cause, spans or fiber annotations. A filter that looks at fiber annotations or message text therefore never matches in the bridge. After the bridge accepts a line, the line still passes through the filter of each ZIO logger, such as the console logger's filter, and those filters see the complete record.

The SLF4J logger name is stored in the log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`). The following extractor reads the logger name from that annotation, or from the ZIO `Trace` when the annotation is missing, and turns it into a log format:

```scala
import zio.logging.LoggerNameExtractor

val loggerName       = LoggerNameExtractor.loggerNameAnnotationOrTrace
val loggerNameFormat = loggerName.toLogFormat()
```

`LogFilter.LogLevelByNameConfig` uses the same extractor, so a filter built from it applies its levels by SLF4J logger name. The following filter keeps `INFO` and above by default, `DEBUG` for `zio.logging.slf4j`, and only `WARN` and above for the `SLF4J-LOGGER` logger:

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

### Filter by Logger Name From Config

`Slf4jBridge.init()` reads the same keys as the `filter` block of the console and file loggers: `rootLevel` and a `mappings` table from logger-name prefix to level. Mapping keys contain dots, so quote them. The following HOCON keeps `INFO` and above, but only `WARN` and above for HikariCP and the Kafka client:

```hocon
logger {
  filter {
    rootLevel = INFO
    mappings {
      "com.zaxxer.hikari" = WARN
      "org.apache.kafka" = WARN
    }
  }
}
```

The bridge loads this block with `ZIO.config`, from the `ConfigProvider` of the runtime. To read it from `application.conf`, install `TypesafeConfigProvider` from `zio-config-typesafe` first. Because `consoleJsonLogger()` reads its filter from the same `logger.filter` block, one block sets the levels for both ZIO and library logs:

```scala
import zio.{ Runtime, ZIOAppArgs, ZLayer }
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.consoleJsonLogger
import zio.logging.slf4j.bridge.Slf4jBridge

val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
  Runtime.removeDefaultLoggers >>>
    Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath()) >>>
    consoleJsonLogger() >+>
    Slf4jBridge.init()
```

See [How to Set Log Levels Per Package From Config](how-to-log-levels-from-config.md) for how names match the mapping keys, and [Configuration Reference](reference-configuration.md) for every key.

## What the Bridge Keeps and Drops

The bridge turns each SLF4J call into one ZIO log record, but SLF4J and ZIO model a log call differently, so not everything carries over:

| SLF4J input                                         | In the ZIO log record                                                                             |
| --------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| Message pattern and arguments                       | The message, with `{}` placeholders filled in by SLF4J's `MessageFormatter`                       |
| `Throwable`, including a trailing argument          | The cause, as `Cause.die(throwable)`                                                              |
| Logger name                                         | The `logger_name` annotation, and an extra log span named after the logger                        |
| Level                                               | `TRACE` to `Trace`, `DEBUG` to `Debug`, `INFO` to `Info`, `WARN` to `Warning`, `ERROR` to `Error` |
| Key-value pairs (`LoggingEventBuilder#addKeyValue`) | Log annotations, with each value converted with `toString`                                        |
| Markers                                             | Dropped; the filter cannot see them either                                                        |
| MDC values put by Java code                         | Ignored; the bridge stores them but never reads them                                              |

Key-value pairs from the SLF4J 2 fluent API, such as `logger.atInfo().addKeyValue("orderId", id).log("Order paid")`, become ordinary ZIO log annotations, so `LogFormat.allAnnotations` or a JSON logger prints them next to the fiber's own annotations. When a key-value pair and a fiber annotation share a key, the key-value pair wins, and the `logger_name` annotation always holds the SLF4J logger name. A `null` value is not supported: `addKeyValue("k", null)` throws a `NullPointerException` in the calling code.

The extra log span means that a format with `LogFormat.spans` prints the SLF4J logger name as a span label on every bridged line.

## Fiber-Ref Propagation

A Java library that runs inside a ZIO effect, for example a JDBC driver called in `ZIO.attempt`, logs on the thread of the calling fiber. Fiber-ref propagation lets the bridge read that fiber's state, so the library's lines carry the fiber's log annotations, such as a `LogAnnotation.TraceId` or a `ZIOAspect.annotated` value, and its log spans, just like the fiber's own lines.

The `Slf4jBridge.init` and `Slf4jBridge.initialize` layers turn this on by adding `Runtime.enableCurrentFiber`, which makes ZIO record in a thread-local which fiber is running on each thread. ZIO documents this runtime flag as having a performance cost, because the thread-local is updated every time a fiber starts or resumes running on a thread. When you do not need fiber annotations on library lines, use `Slf4jBridge.initializeWithoutFiberRefPropagation` or `Slf4jBridge.initWithoutFiberRefPropagation(filter)`: the lines are still logged, but they carry only the logger name, its span, and what was set in the runtime that the bridge captured. The `Slf4jBridge.make` effects never enable the flag themselves. Lines logged from threads that run no fiber, such as a library's own thread pool, never carry fiber annotations, whatever variant you choose.

## Conflicts With Other SLF4J Providers

SLF4J 2 uses exactly one provider. When another provider is also on the classpath, such as `logback-classic` 1.3 or later, `slf4j-simple` 2.x or `log4j-slf4j2-impl`, SLF4J prints a warning and picks one of them. If it picks the other provider, building the bridge layer fails with a `ClassCastException` defect, because the bridge expects its own logger factory. Remove the other provider from the runtime classpath; if you cannot, choose the bridge explicitly with the `slf4j.provider` system property (supported since SLF4J 2.0.9), for example `-Dslf4j.provider=zio.logging.slf4j.bridge.ZioSLF4JServiceProvider`.

For SLF4J v1 libraries on `slf4j-api` 1.7.x, use the [SLF4J v1 bridge](slf4j1-bridge.md) instead. For the overall setup, including the `java.util.logging` bridge, see [How to Send Java Library Logs Through ZIO Logging](how-to-bridge-java-logs.md).

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

The `SLF4J-LOGGER` mapping drops the SLF4J `INFO` lines. The final `DEBUG` line, `Done`, is not written either: the application's logger name, `zio.logging.example.Slf4jBridgeExampleApp`, does not match the `zio.logging.slf4j` mapping, so the root level `INFO` applies. Expected console output:

```
{"name":"zio.logging.example.Slf4jBridgeExampleApp","timestamp":"2024-05-28T08:05:48.056029+02:00","level":"INFO","thread":"zio-fiber-735317063","message":"Start"}
{"name":"SLF4J-LOGGER","trace_id":"416f3886-717c-4359-9f6e-c260e49fd158","user_id":"580376aa-f851-43a2-8b17-9456ce005e93","timestamp":"2024-05-28T08:05:48.065133+02:00","level":"WARN","thread":"zio-fiber-1749629949","message":"Test WARNING!"}
{"name":"SLF4J-LOGGER","trace_id":"416f3886-717c-4359-9f6e-c260e49fd158","user_id":"76d49e0d-7c28-47fd-b5a1-d05afb487cfe","timestamp":"2024-05-28T08:05:48.065174+02:00","level":"WARN","thread":"zio-fiber-697454589","message":"Test WARNING!"}
```
