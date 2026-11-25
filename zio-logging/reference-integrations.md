# Backends and Bridges Reference

> ZIO Logging connects to the Java logging world in two directions. A **backend** sends ZIO log records to a Java logging system: `SLF4J` writes to whatever SLF4J provider is on the classpath (Logback, Log4j 2, ...) and `JPL` writes to the JDK's `java.lang.System.Logger`. A **bridge** does the opposite: `Slf4jBridge` and `JULBridge` capture log calls that Java libraries make through SLF4J or `java.util.logging` (JUL) and send them to the ZIO loggers. This page lists every public entry point of the six integration modules, their defaults, their level mappings, and what each one keeps or drops. For walkthroughs, see the per-module pages: [SLF4J v2](slf4j2.md), [SLF4J v1](slf4j1.md), [SLF4J v2 bridge](slf4j2-bridge.md), [SLF4J v1 bridge](slf4j1-bridge.md), [java.util.logging bridge](jul-bridge.md) and [Java Platform/System Logger](jpl.md).

The entry points are four objects:

```scala
object SLF4J       // zio.logging.backend, modules zio-logging-slf4j and zio-logging-slf4j2
object JPL         // zio.logging.backend, module zio-logging-jpl
object Slf4jBridge // zio.logging.slf4j.bridge, modules zio-logging-slf4j-bridge and zio-logging-slf4j2-bridge
object JULBridge   // zio.logging.jul.bridge, module zio-logging-jul-bridge
```

## Choosing a Module

Pick the module by the direction the logs travel and, for SLF4J, by the version of `slf4j-api` that ends up on the classpath. Libraries compiled against SLF4J 1.7 still call SLF4J 2.x correctly, so the classpath version is what counts:

| You want to                                                                | Module                      | Entry point        | Page                                |
| -------------------------------------------------------------------------- | --------------------------- | ------------------ | ----------------------------------- |
| Write ZIO logs through SLF4J 2.x (Logback 1.3+, `log4j-slf4j2-impl`)       | `zio-logging-slf4j2`        | `SLF4J.slf4j`      | [SLF4J v2](slf4j2.md)               |
| Write ZIO logs through SLF4J 1.7 (Logback 1.2)                             | `zio-logging-slf4j`         | `SLF4J.slf4j`      | [SLF4J v1](slf4j1.md)               |
| Write ZIO logs to `java.lang.System.Logger`                                | `zio-logging-jpl`           | `JPL.jpl`          | [JPL](jpl.md)                       |
| Send SLF4J logs from Java libraries to ZIO loggers, `slf4j-api` 2.x        | `zio-logging-slf4j2-bridge` | `Slf4jBridge.init` | [SLF4J v2 bridge](slf4j2-bridge.md) |
| Send SLF4J logs from Java libraries to ZIO loggers, `slf4j-api` 1.7        | `zio-logging-slf4j-bridge`  | `Slf4jBridge.init` | [SLF4J v1 bridge](slf4j1-bridge.md) |
| Send logs from Java libraries that call `java.util.logging` to ZIO loggers | `zio-logging-jul-bridge`    | `JULBridge.init`   | [JUL bridge](jul-bridge.md)         |

All six modules are JVM-only and are published for Java 17 and later. Every module depends on the `zio-logging` core module. The SLF4J modules also depend on `slf4j-api`: version 1.7.36 for `zio-logging-slf4j` and `zio-logging-slf4j-bridge`, and version 2.0.20 for `zio-logging-slf4j2` and `zio-logging-slf4j2-bridge`.

The v1 and v2 variants of a module declare classes with the same names in the same packages, so an application uses one or the other, never both.

## How They Work Together

A backend is a `ZLogger` that ZIO calls for every log record; a bridge is a Java logger implementation that turns each Java log call into a ZIO log record. The arrows show where each module sits:

```
Backends: ZIO log calls go out to a Java logging system

  ZIO.logInfo(...) ──▶ ZIO runtime ──┬──▶ SLF4J.slf4j ──▶ SLF4J provider (Logback, Log4j 2, ...)
                                     └──▶ JPL.jpl     ──▶ java.lang.System.Logger

Bridges: Java log calls come in to the ZIO loggers

  Java library ──▶ SLF4J API         ──▶ Slf4jBridge ──┐
  Java library ──▶ java.util.logging ──▶ JULBridge   ──┴──▶ ZIO runtime ──▶ ZIO loggers
                                                                           (console, file, JSON, ...)
```

The typical setups are:

1. A ZIO application that already runs on Logback or Log4j 2 installs `Runtime.removeDefaultLoggers >>> SLF4J.slf4j` and configures levels and appenders in `logback.xml` or `log4j2.xml`.
2. A ZIO application that writes with the ZIO console or file loggers installs a bridge after the logger layer, so that libraries such as database drivers or HTTP clients log into the same output: `Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> Slf4jBridge.init()`.
3. A bridge captures the ZIO runtime when its layer is built, and with it the loggers installed at that point. That is why the logger layer comes first and the bridge layer second.

:::caution
Never combine a backend and a bridge for the same logging system. `SLF4J.slf4j` together with `Slf4jBridge` sends each record from ZIO to SLF4J and from SLF4J back to ZIO. The same loop appears with `JPL.jpl` and `JULBridge` when the `System.Logger` implementation is backed by `java.util.logging`, which is the JDK default.
:::

## SLF4J Backends

The `zio-logging-slf4j` and `zio-logging-slf4j2` modules provide the same `SLF4J` object in package `zio.logging.backend`. Both versions share every public member, and they differ only in how annotations reach the provider. Add one of these dependencies:

```scala
// SLF4J 2.x API (Logback 1.3 and later, log4j-slf4j2-impl)
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2" % "2.5.5"

// SLF4J 1.7 API (Logback 1.2)
libraryDependencies += "dev.zio" %% "zio-logging-slf4j" % "2.5.5"
```

### SLF4J Entry Points

The `SLF4J` object has three overloads that return a layer, one that returns the bare logger, and helpers for the logger name, the marker and the cause. The members are declared as follows:

```scala
object SLF4J {
  val causeToThrowableDefault: Cause[Any] => Option[Throwable]
  val logMarkerNameAnnotationKey: String // "slf4j_log_marker_name"
  val logFormatDefault: LogFormat

  def logMarkerName(value: String): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any]
  def getLoggerName(default: String = "zio-slf4j-logger"): Trace => String

  def slf4j(
    format: LogFormat,
    loggerName: Trace => String,
    causeToThrowable: Cause[Any] => Option[Throwable]
  ): ZLayer[Any, Nothing, Unit]
  def slf4j(format: LogFormat): ZLayer[Any, Nothing, Unit]
  def slf4j: ZLayer[Any, Nothing, Unit]

  def slf4jLogger(
    format: LogFormat,
    loggerName: Trace => String,
    causeToThrowable: Cause[Any] => Option[Throwable]
  ): ZLogger[String, Unit]
}
```

The table describes each member:

| Member                                              | Description                                                                                                                                                                                                                               |
| --------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SLF4J.slf4j`                                       | Adds an SLF4J logger with `SLF4J.logFormatDefault`, `SLF4J.getLoggerName()` and `SLF4J.causeToThrowableDefault`.                                                                                                                          |
| `SLF4J.slf4j(format)`                               | Same, with a custom `LogFormat`.                                                                                                                                                                                                          |
| `SLF4J.slf4j(format, loggerName, causeToThrowable)` | Same, with all three settings chosen by the caller. The layer calls `Runtime.addLogger` with the result of `SLF4J.slf4jLogger`.                                                                                                           |
| `SLF4J.slf4jLogger`                                 | Returns the `ZLogger` without installing it, for wrapping in a `LogFilter` or combining with other loggers. It calls `LoggerFactory.getLogger` once, so SLF4J initialises as soon as the logger exists rather than on the first log call. |
| `SLF4J.logFormatDefault`                            | `LogFormat.allAnnotations(excludeKeys = Set(logger_name, slf4j_log_marker_name)) + LogFormat.line + LogFormat.cause`.                                                                                                                     |
| `SLF4J.causeToThrowableDefault`                     | Returns `None` for an empty cause and `Some(FiberFailure(cause))` otherwise.                                                                                                                                                              |
| `SLF4J.getLoggerName(default)`                      | Derives the SLF4J logger name from the `Trace` of the log call: `example.LivePingService.ping(PingService.scala:22)` becomes `example.LivePingService`. Returns `default` when the trace cannot be parsed.                                |
| `SLF4J.logMarkerName(value)`                        | An aspect that annotates an effect with `slf4j_log_marker_name = value`. The backend turns the annotation into an SLF4J `Marker` with `MarkerFactory.getMarker`.                                                                          |
| `SLF4J.logMarkerNameAnnotationKey`                  | The annotation key `"slf4j_log_marker_name"` that `SLF4J.logMarkerName` sets.                                                                                                                                                             |

Each `SLF4J.slf4j` layer adds one more logger to the runtime. Install it once, and remove ZIO's default logger with `Runtime.removeDefaultLoggers`, or ZIO's own console logger prints the same records a second time in its own format.

To install the backend with a custom message format and a fixed fallback logger name:

```scala
import zio.{ Runtime, ZLayer }
import zio.logging.LogFormat
import zio.logging.backend.SLF4J

val customSlf4j: ZLayer[Any, Nothing, Unit] =
  Runtime.removeDefaultLoggers >>> SLF4J.slf4j(
    format = LogFormat.allAnnotations(Set(zio.logging.loggerNameAnnotationKey, SLF4J.logMarkerNameAnnotationKey)) +
      LogFormat.text("[") + LogFormat.fiberId + LogFormat.text("] ") + LogFormat.line + LogFormat.cause,
    loggerName = SLF4J.getLoggerName("my-app"),
    causeToThrowable = SLF4J.causeToThrowableDefault
  )
```

### SLF4J Logger Names and Markers

For each record the backend picks the SLF4J logger name in this order:

1. The value of the `logger_name` annotation, which the `zio.logging.loggerName` and `zio.logging.appendLoggerName` aspects set.
2. The result of the `loggerName` function for the record's `Trace`; with `SLF4J.getLoggerName`, that is the class that makes the log call, or the fallback `"zio-slf4j-logger"`.

A record carries at most one marker: the one named by the `slf4j_log_marker_name` annotation. The backend passes the marker both to the level check (`isInfoEnabled(marker)` and so on) and to the log call, so provider features such as Logback's `MarkerFilter` work on it.

To set the logger name and a marker on one effect:

```scala
import zio.ZIO
import zio.logging.backend.SLF4J

val audited =
  ZIO.logInfo("Card number changed") @@ zio.logging.loggerName("com.example.audit") @@ SLF4J.logMarkerName("CONFIDENTIAL")
```

### SLF4J Level Mapping

The SLF4J provider decides which levels are written. Before formatting a record, the backend asks the SLF4J logger whether the mapped level is enabled, so the levels in `logback.xml` or `log4j2.xml` apply, and a ZIO `LogFilter` does not take part unless you wrap the logger yourself. The mapping is:

| ZIO `LogLevel`  | SLF4J level |
| --------------- | ----------- |
| `All`           | `TRACE`     |
| `Trace`         | `TRACE`     |
| `Debug`         | `DEBUG`     |
| `Info`          | `INFO`      |
| `Warning`       | `WARN`      |
| `Error`         | `ERROR`     |
| `Fatal`         | `ERROR`     |
| `None`          | not logged  |
| any other level | not logged  |

To apply a ZIO `LogFilter` in front of the provider's own level check, wrap the logger from `SLF4J.slf4jLogger` with `LogFilter#filter` and install it with `Runtime.addLogger`:

```scala
import zio.{ LogLevel, Runtime, ZLayer }
import zio.logging.LogFilter
import zio.logging.backend.SLF4J

val filter: LogFilter[String] = LogFilter.logLevelByName(LogLevel.Info, "com.example.db" -> LogLevel.Warning)

val filteredSlf4j: ZLayer[Any, Nothing, Unit] =
  Runtime.removeDefaultLoggers >>> Runtime.addLogger(
    filter.filter(SLF4J.slf4jLogger(SLF4J.logFormatDefault, SLF4J.getLoggerName(), SLF4J.causeToThrowableDefault))
  )
```

### SLF4J Annotations: MDC Versus Key-Value Pairs

The backend splits the output of the `LogFormat` into three parts. Every key-value pair that the format emits (from `LogFormat.allAnnotations`, `LogFormat.annotations`, `LogFormat.annotation`, `LogFormat.logAnnotation` or `LogFormat.spans`) goes to the provider as structured data. Everything else, such as `LogFormat.line`, `LogFormat.text`, `LogFormat.level` or `LogFormat.label`, is appended to the message string. `LogFormat.cause` becomes the `Throwable` of the log call, converted with `causeToThrowable`.

The two versions pass the structured data differently:

| Module               | Structured data       | How it reaches the provider                                                                                                                                                              | Logback pattern       |
| -------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------- |
| `zio-logging-slf4j`  | MDC (`org.slf4j.MDC`) | For each record that has at least one key-value pair, the backend saves the thread's MDC, replaces it with the record's pairs, logs, and restores the saved MDC, all on the same thread. | `%mdc`, `%X{user_id}` |
| `zio-logging-slf4j2` | Key-value pairs       | The backend logs through the fluent API: `atLevel(...).setMessage(...).setCause(...).addMarker(...).addKeyValue(...)...log()`. The MDC is not touched.                                   | `%kvp`                |

:::note
In the v1 module the MDC is replaced, not merged: values that Java code put into the thread's MDC are hidden while a ZIO record with annotations is logged. When a record has no key-value pairs, the backend leaves the MDC untouched, so whatever the worker thread's MDC holds at that moment is printed with the record.
:::

### SLF4J Backend: Kept and Dropped

With `SLF4J.logFormatDefault`, a record reaches the provider like this:

| Part of the ZIO record                       | What the provider receives                                                                  |
| -------------------------------------------- | ------------------------------------------------------------------------------------------- |
| Message                                      | The SLF4J message.                                                                          |
| Level                                        | The mapped SLF4J level; `None` and custom levels are dropped.                               |
| Fiber annotations and `LogAnnotation` values | MDC entries (v1) or key-value pairs (v2), except `logger_name` and `slf4j_log_marker_name`. |
| `logger_name` annotation                     | The SLF4J logger name.                                                                      |
| `slf4j_log_marker_name` annotation           | One SLF4J `Marker`.                                                                         |
| Cause                                        | The `Throwable`, by default a `FiberFailure` that wraps the whole cause.                    |
| Log spans, fiber id, call-site trace         | Not sent. Add `LogFormat.spans` or `LogFormat.fiberId` to the format to include them.       |

## SLF4J Bridges

The `zio-logging-slf4j2-bridge` and `zio-logging-slf4j-bridge` modules make ZIO Logging an SLF4J provider. Java code keeps calling `org.slf4j.LoggerFactory.getLogger(...)`, and the bridge turns each call into a ZIO log record. Both modules provide the same `Slf4jBridge` object in package `zio.logging.slf4j.bridge`. Add one of these dependencies:

```scala
// SLF4J 2.x API
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.5.5"

// SLF4J 1.7 API
libraryDependencies += "dev.zio" %% "zio-logging-slf4j-bridge" % "2.5.5"
```

### Slf4jBridge Entry Points

The bridge has layer constructors for application bootstrap and `Slf4jBridge.make` effects for code that sets up logging by hand. The members are declared as follows:

```scala
object Slf4jBridge {
  val logFilterConfigPath: NonEmptyChunk[String] // "logger", "filter"

  def initialize: ZLayer[Any, Nothing, Unit]
  def init(filter: LogFilter[Any]): ZLayer[Any, Nothing, Unit]
  def init(configPath: NonEmptyChunk[String] = logFilterConfigPath): ZLayer[Any, Config.Error, Unit]

  def initializeWithoutFiberRefPropagation: ZLayer[Any, Nothing, Unit]
  def initWithoutFiberRefPropagation(filter: LogFilter[Any]): ZLayer[Any, Nothing, Unit]

  def make(filter: LogFilter[Any]): ZIO[Any, Nothing, Unit]
  def make(configPath: NonEmptyChunk[String] = logFilterConfigPath): ZIO[Any, Config.Error, Unit]
}
```

The table describes each member:

| Member                                               | Returns                           | Fiber ref propagation      | Filter                                         |
| ---------------------------------------------------- | --------------------------------- | -------------------------- | ---------------------------------------------- |
| `Slf4jBridge.initialize`                             | `ZLayer[Any, Nothing, Unit]`      | on                         | `LogFilter.acceptAll`                          |
| `Slf4jBridge.init(filter)`                           | `ZLayer[Any, Nothing, Unit]`      | on                         | the given filter                               |
| `Slf4jBridge.init(configPath)`, `Slf4jBridge.init()` | `ZLayer[Any, Config.Error, Unit]` | on                         | loaded from config, by default `logger.filter` |
| `Slf4jBridge.initializeWithoutFiberRefPropagation`   | `ZLayer[Any, Nothing, Unit]`      | off                        | `LogFilter.acceptAll`                          |
| `Slf4jBridge.initWithoutFiberRefPropagation(filter)` | `ZLayer[Any, Nothing, Unit]`      | off                        | the given filter                               |
| `Slf4jBridge.make(filter)`                           | `ZIO[Any, Nothing, Unit]`         | off unless already enabled | the given filter                               |
| `Slf4jBridge.make(configPath)`, `Slf4jBridge.make()` | `ZIO[Any, Config.Error, Unit]`    | off unless already enabled | loaded from config                             |
| `Slf4jBridge.logFilterConfigPath`                    | `NonEmptyChunk[String]`           | —                          | the default config path, `logger.filter`       |

The config-based variants read a `LogFilter.LogLevelByNameConfig`, that is the `rootLevel` and `mappings` keys under the path; see [Configuration Keys Reference](reference-configuration.md#log-level-filter-keys). There is no config-based variant without fiber ref propagation; use `Slf4jBridge.initWithoutFiberRefPropagation` with a filter built in code instead.

Every variant captures the runtime of the fiber that builds the layer or runs `Slf4jBridge.make`, including the loggers installed at that moment, and attaches it to the SLF4J logger factory under a lock. Building the layer again replaces the runtime. Until the bridge is initialised, the factory reports every level as disabled, so SLF4J calls made before that point are dropped.

To install the bridge after a JSON console logger, with the filter read from the default config path:

```scala
import zio.{ Runtime, ZIOAppArgs, ZLayer }
import zio.logging.consoleJsonLogger
import zio.logging.slf4j.bridge.Slf4jBridge

val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
  Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> Slf4jBridge.init()
```

### Fiber Ref Propagation

The `Slf4jBridge.initialize` and `Slf4jBridge.init` layers are `Runtime.enableCurrentFiber ++` the bridge layer. With the current-fiber runtime flag on, the bridge can see which ZIO fiber makes the SLF4J call, and copies that fiber's annotations, log spans and `LogAnnotation` context into the record. When a Java library runs inside `ZIO.logAnnotate("request_id", "r-1")(...)`, its records therefore carry `request_id = r-1`.

The `WithoutFiberRefPropagation` variants and the `Slf4jBridge.make` effects do not enable the flag. Records then carry only the fiber refs of the runtime captured at initialisation, plus what the bridge adds itself. Use them when you do not need the caller's annotations. To get propagation with `Slf4jBridge.make`, provide `Runtime.enableCurrentFiber` yourself.

### SLF4J Bridge Level Mapping

The SLF4J level of each call maps to a ZIO level through the public value `ZioLoggerRuntime.logLevelMapping`:

| SLF4J level | ZIO `LogLevel` |
| ----------- | -------------- |
| `TRACE`     | `Trace`        |
| `DEBUG`     | `Debug`        |
| `INFO`      | `Info`         |
| `WARN`      | `Warning`      |
| `ERROR`     | `Error`        |

### What the Bridge Filter Sees

The bridge runs its `LogFilter` inside SLF4J's `isTraceEnabled`, `isDebugEnabled` (and so on), before the message exists. The filter receives the mapped level and a single annotation, `logger_name`, set to the SLF4J logger name. Trace, cause, fiber refs, spans and message are empty. Filters by level and logger name, such as `LogFilter.logLevelByName`, work as expected; filters that test the message text, the cause or fiber annotations never match there.

Records that pass the bridge filter then go to every installed ZIO logger, and each logger applies its own filter to the full record.

### SLF4J Bridge: Kept and Dropped

Each SLF4J call becomes one ZIO record. The table lists what survives:

| Part of the SLF4J call                               | v1 bridge                                                                       | v2 bridge                                                                    |
| ---------------------------------------------------- | ------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| Message pattern and arguments                        | Formatted with SLF4J's `MessageFormatter`.                                      | Same.                                                                        |
| Throwable                                            | `Cause.die(throwable)`.                                                         | Same.                                                                        |
| Logger name                                          | The `logger_name` annotation, plus a log span named after the logger.           | Same.                                                                        |
| Key-value pairs (`atInfo().addKeyValue(k, v).log()`) | Not part of the SLF4J 1.7 API.                                                  | Kept as ZIO log annotations. A `null` value throws a `NullPointerException`. |
| Markers                                              | Dropped.                                                                        | Dropped.                                                                     |
| MDC values set by Java code                          | Dropped: the bridge's MDC adapter stores them, but the bridge never reads them. | Dropped, for the same reason.                                                |
| Caller fiber's annotations, spans and log context    | Kept when fiber ref propagation is on.                                          | Same.                                                                        |

## JUL Bridge

The `zio-logging-jul-bridge` module installs a `java.util.logging.Handler` on the JUL root logger and turns each `LogRecord` into a ZIO log record. The entry point is `JULBridge` in package `zio.logging.jul.bridge`. Add the dependency:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-jul-bridge" % "2.5.5"
```

### JULBridge Entry Points

`JULBridge` has the same family of constructors as `Slf4jBridge`, with the same return types, the same fiber ref propagation rules and the same default config path `logger.filter`:

```scala
object JULBridge {
  val logFilterConfigPath: NonEmptyChunk[String] // "logger", "filter"

  def initialize: ZLayer[Any, Nothing, Unit]
  def init(filter: LogFilter[Any]): ZLayer[Any, Nothing, Unit]
  def init(configPath: NonEmptyChunk[String] = logFilterConfigPath): ZLayer[Any, Config.Error, Unit]

  def initializeWithoutFiberRefPropagation: ZLayer[Any, Nothing, Unit]
  def initWithoutFiberRefPropagation(filter: LogFilter[Any]): ZLayer[Any, Nothing, Unit]

  def make(filter: LogFilter[Any]): ZIO[Any, Nothing, Unit]
  def make(configPath: NonEmptyChunk[String] = logFilterConfigPath): ZIO[Any, Config.Error, Unit]
}
```

The JUL filter has the same limits as described in [What the Bridge Filter Sees](#what-the-bridge-filter-sees): it receives the level, the `logger_name` annotation and a `Trace` built from the logger name, while the message, cause and fiber refs are empty. Each record gets a log span and a `logger_name` annotation named after the JUL logger.

To install the JUL bridge with a filter built in code:

```scala
import zio.{ LogLevel, Runtime, ZIOAppArgs, ZLayer }
import zio.logging.{ LogFilter, consoleJsonLogger }
import zio.logging.jul.bridge.JULBridge

val julFilter: LogFilter[Any] = LogFilter.logLevelByName[Any](LogLevel.Info, "com.example.legacy" -> LogLevel.Warning)

val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
  Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> JULBridge.init(julFilter)
```

### Global LogManager Side Effects

JUL keeps its configuration in one JVM-wide `LogManager`, and the bridge changes it. Each `JULBridge` layer or `JULBridge.make` call:

1. Calls `LogManager.getLogManager.readConfiguration` with the single line `.level= ALL`. This resets the whole JUL configuration: it removes every handler from every logger, including the default console handler, and drops levels set earlier from `logging.properties` or code.
2. Adds the bridge handler to the root logger.

From then on the bridge's `LogFilter` decides which JUL records are kept. Initialising the bridge again repeats the reset, so the old bridge handler is removed and a new one is added. Handlers or levels that other code adds to JUL after the bridge is initialised still apply.

### JUL Level Mapping

JUL has more levels than ZIO, so several of them share a ZIO level:

| JUL `Level` | ZIO `LogLevel` |
| ----------- | -------------- |
| `SEVERE`    | `Fatal`        |
| `WARNING`   | `Warning`      |
| `INFO`      | `Info`         |
| `CONFIG`    | `Info`         |
| `FINE`      | `Debug`        |
| `FINER`     | `Debug`        |
| `FINEST`    | `Trace`        |
| `ALL`       | `All`          |
| `OFF`       | `None`         |

:::caution
Only these nine levels are mapped. A record with a custom `java.util.logging.Level` has no entry, and the bridge's `publish` throws `NoSuchElementException` back to the JUL caller.
:::

### JUL Bridge: Kept and Dropped

The bridge copies the message, the level, the logger name and the thrown exception (as `Cause.die`). It uses `LogRecord#getMessage` as the message, so the bridge does not substitute JUL parameters such as `{0}` and does not apply resource bundles: `logger.log(Level.INFO, "Hi {0}", name)` is logged as `Hi {0}`. Fiber annotations of the calling fiber are kept when fiber ref propagation is on.

## JPL Backend

The `zio-logging-jpl` module writes ZIO log records to `java.lang.System.Logger`, the logging API built into the JDK (Java Platform Logging). The entry point is `JPL` in package `zio.logging.backend`. Add the dependency:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-jpl" % "2.5.5"
```

### JPL Entry Points

`JPL` has two layer overloads, a default layer and a function that returns the bare logger:

```scala
object JPL {
  val logFormatDefault: LogFormat

  def jpl(format: LogFormat, loggerName: Trace => String): ZLayer[Any, Nothing, Unit]
  def jpl(format: LogFormat): ZLayer[Any, Nothing, Unit]
  val jpl: ZLayer[Any, Nothing, Unit]

  def jplLogger(format: LogFormat, loggerName: Trace => String = getLoggerName()): ZLogger[String, Unit]
}
```

The table describes each member:

| Member                        | Description                                                                                    |
| ----------------------------- | ---------------------------------------------------------------------------------------------- |
| `JPL.jpl`                     | Adds a JPL logger with `JPL.logFormatDefault` and the trace-based logger name.                 |
| `JPL.jpl(format)`             | Same, with a custom `LogFormat`.                                                               |
| `JPL.jpl(format, loggerName)` | Same, with a custom logger-name function.                                                      |
| `JPL.jplLogger`               | Returns the `ZLogger` without installing it. The loggers come from `System.getLogger(name)`.   |
| `JPL.logFormatDefault`        | `LogFormat.allAnnotations(excludeKeys = Set(logger_name)) + LogFormat.line + LogFormat.cause`. |

The logger name is the `logger_name` annotation when present, and otherwise the class derived from the trace, with the fallback `"zio-jpl-logger"`. The trace-based function is `private[backend]`; to choose another fallback, pass your own `Trace => String`.

Unlike SLF4J, `System.Logger` has no structured data. Key-value pairs from the format are written into the message as `key=value ` text, so the default format produces `user=alice trace_id=... Starting user operation`. For the cause, the backend passes the first `Throwable` failure or defect of the cause, or a `FiberFailure` that wraps the cause when it contains none.

To install the JPL backend in place of the default logger:

```scala
import zio.{ Runtime, ZLayer }
import zio.logging.backend.JPL

val jplLogger: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers >>> JPL.jpl
```

### JPL Level Mapping

The backend checks `System.Logger#isLoggable` with the mapped level before formatting, so the `System.Logger` implementation decides which levels are written:

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

:::note
`LogLevel.None` maps to `OFF` rather than being dropped. Whether a record at `OFF` is written depends on the `System.Logger` implementation; the default implementation, which is backed by `java.util.logging`, can still write it.
:::

## SPI Classes

SLF4J and JUL find their logger implementations by class name or through Java's `ServiceLoader`, so the bridge modules contain public classes that application code never calls. They are public only because the logging system loads them:

| Class                                                                     | Module             | Role                                                                                         |
| ------------------------------------------------------------------------- | ------------------ | -------------------------------------------------------------------------------------------- |
| `org.slf4j.impl.StaticLoggerBinder`                                       | v1 bridge          | SLF4J 1.7 looks up this class by name to find the logger factory.                            |
| `org.slf4j.impl.StaticMDCBinder`                                          | v1 bridge          | SLF4J 1.7 looks it up for the MDC adapter (a `BasicMDCAdapter` that the bridge never reads). |
| `org.slf4j.impl.StaticMarkerBinder`                                       | v1 bridge          | SLF4J 1.7 looks it up for the marker factory.                                                |
| `org.slf4j.helpers.ZioLoggerBase`                                         | v1 bridge          | Base logger; extends SLF4J's `MarkerIgnoringBase`, which is why v1 markers are ignored.      |
| `org.slf4j.impl.ZioLoggerFactory`, `ZioLogger`, `LoggerRuntime`           | v1 bridge          | The logger factory, the logger, and the interface between them and the ZIO runtime.          |
| `zio.logging.slf4j.bridge.ZioSLF4JServiceProvider`                        | v2 bridge          | The SLF4J 2 provider, registered in `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`.  |
| `zio.logging.slf4j.bridge.ZioLoggerFactory`, `ZioLogger`, `LoggerRuntime` | v2 bridge          | The logger factory, the logger, and the interface to the ZIO runtime.                        |
| `zio.logging.slf4j.bridge.LoggerData`                                     | both SLF4J bridges | Holds a logger name and its `logger_name` annotation.                                        |
| `zio.logging.slf4j.bridge.ZioLoggerRuntime`                               | both SLF4J bridges | Turns SLF4J calls into ZIO records; its companion holds `ZioLoggerRuntime.logLevelMapping`.  |
| `zio.logging.jul.bridge.ZioLoggerRuntime`                                 | JUL bridge         | The JUL `Handler` that `JULBridge` adds to the root logger.                                  |

The v2 bridge jar also ships a `module-info` for the Java module system. It declares the module `org.slf4j.zio`, which requires `org.slf4j` and provides `org.slf4j.spi.SLF4JServiceProvider` with `ZioSLF4JServiceProvider`. The backend modules ship no `module-info`.

Because a bridge is an SLF4J provider, it conflicts with every other provider on the classpath, such as `logback-classic` or `log4j-slf4j2-impl`:

- With SLF4J 2.x, when several providers are present SLF4J picks one and prints a warning. If it picks another provider, building the `Slf4jBridge` layer fails with a `ClassCastException` defect. SLF4J 2.0.9 and later let you choose the provider with the system property `slf4j.provider=zio.logging.slf4j.bridge.ZioSLF4JServiceProvider`.
- With SLF4J 1.7, the first `org.slf4j.impl.StaticLoggerBinder` on the classpath wins, and the same `ClassCastException` appears when it is not the bridge's.
- SLF4J 2.x does not load SLF4J 1.7 bindings, so the v1 bridge has no effect when `slf4j-api` 2.x ends up on the classpath, for example through dependency eviction.
