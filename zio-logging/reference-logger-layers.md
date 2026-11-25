# Logger Layers and Constructors Reference

> Every logger layer, make* constructor, install helper and filter syntax in the zio.logging package object, with platform support.

The `zio.logging` package object is where ZIO Logging's ready-made loggers live. It gives you three kinds of members: **layers** such as `zio.logging.consoleLogger`, which you put in your application's `bootstrap`; **constructors** such as `zio.logging.makeConsoleLogger`, which are ZIO effects that build a `ZLogger` without installing it; and **syntax** such as `ZLoggerZIOLayerOps#install`, which turns a constructor into a layer. Two small logger classes, `FilteredLogger` and `MetricLogger`, are what the config forms of the file constructors and `zio.logging.makeMetricLogger` return.

All members below are public as `zio.logging.<name>`. In the source, the `private[logging]` traits `LoggerLayers`, `FileLoggerLayers` and `LoggingPackageAllPlatforms` define them and the package object mixes those traits in, so you never refer to those trait names in your code. The shape of the package object is:

```scala
package object logging {
  // layers, ready to use in bootstrap
  def consoleLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit]
  def consoleLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit]
  // ... and the JSON, standard-error, file and metric variants

  // constructors, for composing your own layer
  def makeConsoleLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]]
  // ... and the other make* constructors

  // syntax
  implicit final class ZLoggerZIOLayerOps[RIn, +E, ROut <: ZLogger[String, Any]: Tag](self: ZIO[RIn, E, ROut])
  implicit final class ZLoggerOps[-Message, +Output](self: ZLogger[Message, Output])
}
```

Prefer the layers. Reach for a `make*` constructor when you need something a layer cannot express: two loggers with different filters, a logger that writes to your own `PrintStream`, or a logger wrapped by [`ReconfigurableLogger`](reconfigurable-logger.md).

## Installation

The console, metric and syntax members are in the core module, published for JVM, Scala.js and Scala Native:

```scala
libraryDependencies += "dev.zio" %%% "zio-logging" % "2.5.5"
```

Use `%%` instead of `%%%` in a JVM-only build. The file members exist on the JVM and Scala Native only; see [Platform Support](#platform-support).

## How They Work Together

Every layer on this page is built from the same steps. A layer such as `zio.logging.consoleLogger(config)` is literally `makeConsoleLogger(config).install`, so knowing the steps lets you build any variant yourself:

1. Pick a format and a filter. A `ConsoleLoggerConfig` or `FileLoggerConfig` carries both, or you use a `LogFormat` and a `LogFilter` directly.
2. Build the logger with a `make*` constructor. The result is a ZIO effect that produces a `ZLogger[String, Any]`. Constructors that take a config apply the config's filter for you; the multi-argument constructors do not.
3. Optionally call `ZLoggerZIOLayerOps#filter` to wrap the logger in a `FilteredLogger`.
4. Call `ZLoggerZIOLayerOps#install` (or `ZLoggerZIOLayerOps#installUnscoped`, `ZLoggerZIOLayerOps#installScoped`) to get a `ZLayer[..., Unit]`. Building that layer adds the logger to ZIO's set of loggers with `ZIO.withLoggerScoped`, and removes it again when the layer's scope closes.
5. Put the layer after `Runtime.removeDefaultLoggers` in `bootstrap`. Installing a logger only adds it; ZIO's default console logger keeps running until you remove it.

The data flow through these steps is:

```text
 ConsoleLoggerConfig / FileLoggerConfig            LogFormat#toLogger / #toJsonLogger
   (format + filter)                                  (ZLogger[String, String])
          │                                                     │
          ▼                                                     ▼
  make*Logger(config)                          make*Logger(..., logger, ...)
  filter applied                               no filter
          │                                                     │
          └──────────────► ZIO[R, E, ZLogger[String, Any]] ◄────┘
                                    │
                                    │  .filter(LogFilter)   ──► FilteredLogger
                                    │
                                    │  .install / .installUnscoped / .installScoped
                                    ▼
                          ZLayer[R', E, Unit]   ◄── consoleLogger, fileLogger, logMetrics, ...
                                    │                 are prebuilt layers of this shape
                                    ▼
             bootstrap = Runtime.removeDefaultLoggers >>> layer
```

## Platform Support

The file members live in source shared by the JVM and Scala Native builds only. Everything else is in source shared by all three platforms.

| Member                                                                                                                | JVM | JS  | Native |
| --------------------------------------------------------------------------------------------------------------------- | --- | --- | ------ |
| `consoleLogger`, `consoleJsonLogger`, `consoleErrLogger`, `consoleErrJsonLogger`                                      | yes | yes | yes    |
| `makeConsoleLogger`, `makeConsoleJsonLogger`, `makeConsoleErrLogger`, `makeConsoleErrJsonLogger`                      | yes | yes | yes    |
| `makeSystemOutLogger`, `makeSystemErrLogger`, `makePrintStreamLogger`                                                 | yes | yes | yes    |
| `logMetrics`, `logMetricsWith`, `makeMetricLogger`, `MetricLogger`                                                    | yes | yes | yes    |
| `fileLogger`, `fileJsonLogger`, `fileAsyncLogger`, `fileAsyncJsonLogger`                                              | yes | no  | yes    |
| `makeFileLogger`, `makeFileJsonLogger`, `makeFileAsyncLogger`, `makeFileAsyncJsonLogger`                              | yes | no  | yes    |
| `ZLoggerZIOLayerOps` syntax, `ZLoggerOps#filter`, `FilteredLogger`                                                   | yes | yes | yes    |
| `loggerConfigPath`                                                                                                    | yes | yes | yes    |

## Reading Configuration

Every console and file layer comes in two overloads. One takes a config value and cannot fail. The other takes a config path, reads the config with `ConsoleLoggerConfig.load` or `FileLoggerConfig.load`, and fails with `Config.Error` when the config is missing or invalid.

The default path is `zio.logging.loggerConfigPath`, which is `NonEmptyChunk("logger")`:

```scala
package object logging {
  val loggerConfigPath: NonEmptyChunk[String]
}
```

`ConsoleLoggerConfig.load` and `FileLoggerConfig.load` call `ZIO.config`, so the values come from the `ConfigProvider` of the running effect. By default that provider reads environment variables and system properties; set another one with `Runtime.setConfigProvider` before the logger layer. To read a console logger from the default `logger` path and a second one from `app.audit`:

```scala
import zio.{ Config, ConfigProvider, NonEmptyChunk, Runtime, ZLayer }
import zio.logging.{ consoleErrJsonLogger, consoleLogger }

val provider: ConfigProvider =
  ConfigProvider.fromMap(
    Map(
      "logger/filter/rootLevel"    -> "INFO",
      "app/audit/filter/rootLevel" -> "WARN"
    ),
    "/"
  )

val loggers: ZLayer[Any, Config.Error, Unit] =
  Runtime.removeDefaultLoggers >>>
    Runtime.setConfigProvider(provider) >>>
    consoleLogger() >>>
    consoleErrJsonLogger(NonEmptyChunk("app", "audit"))
```

Every key under the path is listed in [Configuration Keys Reference](reference-configuration.md).

## Logger Layers

A logger layer has the type `ZLayer[Any, E, Unit]`: it needs nothing, outputs no service, and installs one logger while it is alive. Use it as, or as part of, your application's `bootstrap`.

### Console Layers

The console layers write one formatted line per log event to standard output or standard error. The `Json` variants format with `LogFormat#toJsonLogger`, the others with `LogFormat#toLogger`:

```scala
package object logging {
  def consoleLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit]
  def consoleLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit]

  def consoleJsonLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit]
  def consoleJsonLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit]

  def consoleErrLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit]
  def consoleErrLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit]

  def consoleErrJsonLogger(config: ConsoleLoggerConfig): ZLayer[Any, Nothing, Unit]
  def consoleErrJsonLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit]
}
```

| Layer                  | Writes to         | Line format | When to use                                                                                                 |
| ---------------------- | ----------------- | ----------- | ----------------------------------------------------------------------------------------------------------- |
| `consoleLogger`        | `System.out`      | Text        | Local development and any environment where people read the output directly.                                |
| `consoleJsonLogger`    | `System.out`      | JSON        | Containers whose log collector parses JSON from standard output. See [JSON logs](how-to-json-logs.md).      |
| `consoleErrLogger`     | `System.err`      | Text        | Command-line tools that keep standard output for their real output, such as data piped to another program. |
| `consoleErrJsonLogger` | `System.err`      | JSON        | The same, when the collector reads standard error and expects JSON.                                         |

To send text logs to standard output and only errors, as JSON, to standard error, combine two layers built from config values:

```scala
import zio.{ LogLevel, Runtime, ZLayer }
import zio.logging.{ ConsoleLoggerConfig, LogFilter, LogFormat, consoleErrJsonLogger, consoleLogger }

val errorsOnly: ConsoleLoggerConfig =
  ConsoleLoggerConfig(LogFormat.default, LogFilter.LogLevelByNameConfig(LogLevel.Error))

val bootstrap: ZLayer[Any, Nothing, Unit] =
  Runtime.removeDefaultLoggers >>>
    consoleLogger(ConsoleLoggerConfig.default) >>>
    consoleErrJsonLogger(errorsOnly)
```

More examples are on [Console Logger](console-logger.md).

### File Layers

The file layers write to the file at `FileLoggerConfig#destination`, opened in append mode. They exist on the JVM and Scala Native only:

```scala
package object logging {
  def fileLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit]
  def fileLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit]

  def fileJsonLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit]
  def fileJsonLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit]

  def fileAsyncLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit]
  def fileAsyncLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit]

  def fileAsyncJsonLogger(config: FileLoggerConfig): ZLayer[Any, Nothing, Unit]
  def fileAsyncJsonLogger(configPath: NonEmptyChunk[String] = loggerConfigPath): ZLayer[Any, Config.Error, Unit]
}
```

| Layer                 | Line format | Who writes the file                                    | When to use                                                                     |
| --------------------- | ----------- | ------------------------------------------------------ | ------------------------------------------------------------------------------- |
| `fileLogger`          | Text        | The thread that logs, during the `ZIO.log*` call       | Low log volume, or when every line must be on disk before the call returns.     |
| `fileJsonLogger`      | JSON        | The same                                               | The same, for files read by a log shipper that expects JSON.                    |
| `fileAsyncLogger`     | Text        | A background fiber that takes lines from a queue       | High log volume, where the logging fiber should not wait for disk I/O.          |
| `fileAsyncJsonLogger` | JSON        | The same                                               | The same, for JSON files.                                                       |

To write JSON lines to a file through the background writer:

```scala
import zio.{ Runtime, ZLayer }
import zio.logging.{ FileLoggerConfig, LogFilter, LogFormat, fileAsyncJsonLogger }

import java.nio.file.Paths

val config: FileLoggerConfig =
  FileLoggerConfig(
    destination = Paths.get("/var/log/my-app/app.log"),
    format = LogFormat.default,
    filter = LogFilter.LogLevelByNameConfig.default
  )

val bootstrap: ZLayer[Any, Nothing, Unit] =
  Runtime.removeDefaultLoggers >>> fileAsyncJsonLogger(config)
```

:::caution[How file loggers behave today]
- The file is opened when the layer is built. When its directory does not exist, building the layer dies with a `FileNotFoundException`; it is not reported as a `Config.Error`.
- No file logger flushes or closes its writer when its layer's scope closes. With `autoFlushBatchSize` above `1` or a `bufferedIOSize`, lines still in the buffer at shutdown can be lost.
- The async loggers use a bounded queue of 1000 entries. When the queue is full, the thread that logs waits until there is room. When the layer's scope closes, the background fiber is interrupted, and lines still in the queue are not written.
:::

Rolling and the other file options are on [File Logger](file-logger.md) and [Rolling Log Files](how-to-file-logging-rotation.md).

### Metric Layers

The metric layers install a `MetricLogger`, which writes nothing and instead increments a counter for every log event, labelled with the event's level:

```scala
package object logging {
  val logMetrics: ZLayer[Any, Nothing, Unit]
  def logMetricsWith(name: String, logLevelLabel: String): ZLayer[Any, Nothing, Unit]
}
```

`zio.logging.logMetrics` counts into a counter named `zio_log_total` with the label name `level`. `zio.logging.logMetricsWith` uses your own counter name and label name, for example to match a naming convention in your metrics backend. Both count every event they receive, whatever the filters of your other loggers say. To keep the console logger and count log lines with custom names:

```scala
import zio.{ Config, Runtime, ZLayer }
import zio.logging.{ consoleLogger, logMetricsWith }

val bootstrap: ZLayer[Any, Config.Error, Unit] =
  Runtime.removeDefaultLoggers >>> consoleLogger() >>> logMetricsWith("app_log_lines_total", "severity")
```

See [Log Metrics](metrics.md) for exporting the counter.

## Logger Constructors

A constructor is a ZIO effect that builds a `ZLogger[String, Any]` without installing it. You rarely call one directly; it is the building block for the layers above. Use one when you need to change the logger before it is installed, such as adding a filter, writing to your own stream, or passing it to `ReconfigurableLogger.make`.

### Console and Stream Constructors

These constructors write formatted lines with `PrintStream#println`. The `makeConsole*` constructors take a `ConsoleLoggerConfig` and apply its filter. The `makeSystem*` and `makePrintStreamLogger` constructors take a formatting logger, usually `LogFormat#toLogger` or `LogFormat#toJsonLogger`, and apply no filter:

```scala
package object logging {
  def makeConsoleLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]]
  def makeConsoleJsonLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]]
  def makeConsoleErrLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]]
  def makeConsoleErrJsonLogger(config: ConsoleLoggerConfig): ZIO[Any, Nothing, ZLogger[String, Any]]

  def makeSystemOutLogger(logger: ZLogger[String, String]): ZIO[Any, Nothing, ZLogger[String, Any]]
  def makeSystemErrLogger(logger: ZLogger[String, String]): ZIO[Any, Nothing, ZLogger[String, Any]]
  def makePrintStreamLogger(logger: ZLogger[String, String], stream: PrintStream): ZIO[Any, Nothing, ZLogger[String, Any]]
}
```

`zio.logging.makeSystemOutLogger` and `zio.logging.makeSystemErrLogger` pass `System.out` and `System.err` to `zio.logging.makePrintStreamLogger`. The stream is read once, when the constructor is called, so a later `System.setOut` does not redirect a logger that already exists. Any exception from `PrintStream#println`, other than a `VirtualMachineError`, is ignored.

`zio.logging.makePrintStreamLogger` is the one to use when you want the lines somewhere you control. To capture formatted lines in memory, for example in a test:

```scala
import zio.{ Runtime, ZLayer }
import zio.logging.{ LogFormat, ZLoggerZIOLayerOps, makePrintStreamLogger }

import java.io.{ ByteArrayOutputStream, PrintStream }

val buffer: ByteArrayOutputStream = new ByteArrayOutputStream()

val captureLines: ZLayer[Any, Nothing, Unit] =
  Runtime.removeDefaultLoggers >>>
    makePrintStreamLogger(LogFormat.level.toLogger, new PrintStream(buffer, true)).install
```

After the program runs, `buffer.toString` holds one line per event, such as `INFO` and `WARN`.

### File Constructors

The file constructors come in a config form, which applies the config's filter and returns a `FilteredLogger`, and a six-argument form, which applies no filter. The async forms need a `Scope`, because they fork the background writer fiber into it:

```scala
package object logging {
  def makeFileLogger(config: FileLoggerConfig): ZIO[Any, Nothing, FilteredLogger[String, Any]]
  def makeFileJsonLogger(config: FileLoggerConfig): ZIO[Any, Nothing, FilteredLogger[String, Any]]
  def makeFileAsyncLogger(config: FileLoggerConfig): ZIO[Scope, Nothing, FilteredLogger[String, Any]]
  def makeFileAsyncJsonLogger(config: FileLoggerConfig): ZIO[Scope, Nothing, FilteredLogger[String, Any]]

  def makeFileLogger(
    destination: Path,
    logger: ZLogger[String, String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy]
  ): ZIO[Any, Nothing, ZLogger[String, Any]]

  def makeFileAsyncLogger(
    destination: Path,
    logger: ZLogger[String, String],
    charset: Charset,
    autoFlushBatchSize: Int,
    bufferedIOSize: Option[Int],
    rollingPolicy: Option[FileLoggerConfig.FileRollingPolicy]
  ): ZIO[Scope, Nothing, ZLogger[String, Any]]
}
```

There is no six-argument form of `makeFileJsonLogger` or `makeFileAsyncJsonLogger`; pass `LogFormat#toJsonLogger` as `logger` to the six-argument `makeFileLogger` or `makeFileAsyncLogger` instead. The arguments mean the same as the `FileLoggerConfig` fields of the same names, listed in [Configuration Keys Reference](reference-configuration.md#file-logger-keys). The file is opened when the effect runs.

To build an unfiltered async file logger from a format, and install it so it lives as long as the layer:

```scala
import zio.ZLayer
import zio.logging.{ LogFormat, ZLoggerZIOLayerOps, makeFileAsyncLogger }

import java.nio.charset.StandardCharsets
import java.nio.file.Paths

val everything: ZLayer[Any, Nothing, Unit] =
  makeFileAsyncLogger(
    destination = Paths.get("/var/log/my-app/debug.log"),
    logger = LogFormat.default.toLogger,
    charset = StandardCharsets.UTF_8,
    autoFlushBatchSize = 1,
    bufferedIOSize = None,
    rollingPolicy = None
  ).installUnscoped
```

### Metric Constructor

`zio.logging.makeMetricLogger` builds a `MetricLogger` for any `Long` counter. `zio.logging.logMetrics` and `zio.logging.logMetricsWith` are this constructor followed by `ZLoggerZIOLayerOps#install`:

```scala
package object logging {
  def makeMetricLogger(counter: Metric.Counter[Long], logLevelLabel: String): ZIO[Any, Nothing, MetricLogger]
}
```

Use it when you want to build the counter yourself, for example to add a fixed tag to it:

```scala
import zio.ZLayer
import zio.logging.{ ZLoggerZIOLayerOps, makeMetricLogger }
import zio.metrics.Metric

val counter: Metric.Counter[Long] = Metric.counter("log_events_total").tagged("service", "checkout")

val countLogs: ZLayer[Any, Nothing, Unit] = makeMetricLogger(counter, "level").install
```

## Installing and Filtering Loggers

The syntax that turns a constructor into a layer is the implicit class `ZLoggerZIOLayerOps`, defined in the `zio.logging` package object. It applies to any `ZIO[RIn, E, ROut]` whose result `ROut` is a `ZLogger[String, Any]`. When `ROut` is a type from `zio.logging`, such as `FilteredLogger`, Scala finds the syntax on its own; otherwise bring it into scope with `import zio.logging.ZLoggerZIOLayerOps` or `import zio.logging._`.

### Choosing an Install Method

The three install methods differ only in what happens to the `Scope` that keeps the logger installed:

```scala
implicit final class ZLoggerZIOLayerOps[RIn, +E, ROut <: ZLogger[String, Any]: Tag](self: ZIO[RIn, E, ROut]) {
  def filter(filter: LogFilter[String]): ZIO[RIn, E, FilteredLogger[String, Any]]
  def install: ZLayer[RIn, E, Unit]
  def installUnscoped[RIn2](implicit ev: RIn2 with Scope =:= RIn): ZLayer[RIn2, E, Unit]
  def installScoped: ZLayer[Scope with RIn, E, Unit]
}
```

| Method                                | Use it when                                           | Effect                                                                                                                                       |
| ------------------------------------- | ----------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `ZLoggerZIOLayerOps#install`          | The constructor needs no `Scope` (`RIn` is `Any`)     | Builds a scoped layer. The logger is installed while the layer is alive and removed when the layer is released.                              |
| `ZLoggerZIOLayerOps#installUnscoped`  | The constructor needs a `Scope`, such as `makeFileAsyncLogger` or `ReconfigurableLogger.make` | Same as `ZLoggerZIOLayerOps#install`, and the layer's own scope satisfies the constructor's `Scope`, so the layer's input type drops it: `RIn2` is `RIn` without `Scope`. |
| `ZLoggerZIOLayerOps#installScoped`    | You want the logger tied to a `Scope` that you provide | Builds a non-scoped layer that needs `Scope with RIn`. The logger stays installed until that outer scope closes, not when the layer is released. |

`ZLoggerZIOLayerOps#filter` wraps the produced logger in a `FilteredLogger`, which passes only the events the `LogFilter` accepts. Filtering before installing is how you give two loggers different levels. To keep every event at `INFO` and above on standard output, and also send `ERROR` and above to standard error in color:

```scala
import zio.{ LogLevel, Runtime, ZIO, ZLayer, ZLogger }
import zio.logging.{
  ConsoleLoggerConfig,
  FilteredLogger,
  LogFilter,
  LogFormat,
  ZLoggerZIOLayerOps,
  makeConsoleLogger,
  makeSystemErrLogger
}

val stdout: ZIO[Any, Nothing, ZLogger[String, Any]] =
  makeConsoleLogger(ConsoleLoggerConfig.default)

val stderrErrors: ZIO[Any, Nothing, FilteredLogger[String, Any]] =
  makeSystemErrLogger(LogFormat.colored.toLogger).filter(LogFilter.logLevel(LogLevel.Error))

val bootstrap: ZLayer[Any, Nothing, Unit] =
  Runtime.removeDefaultLoggers >>> stdout.install >>> stderrErrors.install
```

An error event goes to both loggers. An info event goes only to standard output, because the `FilteredLogger` around the second logger rejects it.

### Filtering a Plain ZLogger

`ZLoggerOps#filter` does the same wrapping for a `ZLogger` value that is not inside a ZIO effect. `LogFilter#filter` is the same operation called from the filter's side:

```scala
implicit final class ZLoggerOps[-Message, +Output](self: ZLogger[Message, Output]) {
  def filter[M <: Message](filter: LogFilter[M]): ZLogger[M, Option[Output]]
}
```

The returned value is a `FilteredLogger`, typed as `ZLogger[M, Option[Output]]`. Its output is `None` for an event the filter rejects. This is useful for a logger you write yourself. To collect the messages of error events in memory, and install that logger with `ZIO.succeed` and `ZLoggerZIOLayerOps#install`:

```scala
import zio.{ LogLevel, ZIO, ZLayer, ZLogger }
import zio.logging.{ LogFilter, LogFormat, ZLoggerOps, ZLoggerZIOLayerOps }

import java.util.concurrent.ConcurrentLinkedQueue

val collected: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()

val collector: ZLogger[String, Unit] =
  LogFormat.line.toLogger.map { line => collected.add(line); () }

val errorsOnly: ZLogger[String, Option[Unit]] =
  collector.filter(LogFilter.logLevel(LogLevel.Error))

val collectErrors: ZLayer[Any, Nothing, Unit] = ZIO.succeed(errorsOnly).install
```

## FilteredLogger

`FilteredLogger` is a `ZLogger` that runs a `LogFilter` before delegating to another logger. It is what `ZLoggerZIOLayerOps#filter`, `ZLoggerOps#filter`, `LogFilter#filter` and the config forms of the file constructors return:

```scala
final case class FilteredLogger[Message, Output](logger: ZLogger[Message, Output], filter: LogFilter[Message])
    extends ZLogger[Message, Option[Output]] {
  def withFilter(newFilter: LogFilter[Message]): FilteredLogger[Message, Output]
}
```

For each event, it calls `FilteredLogger#filter` with the event's trace, fiber id, level, message, cause, fiber refs, spans and annotations. When the filter accepts the event, it calls `FilteredLogger#logger` and returns `Some` of its output; otherwise it returns `None` and the inner logger never sees the event. `FilteredLogger#withFilter` returns a copy with the same inner logger and a different filter. The filters themselves are described in [LogFilter and LogGroup Reference](reference-log-filter.md).

## MetricLogger

`MetricLogger` is a `ZLogger[String, Unit]` that increments a counter by one for every event it receives. It is what `zio.logging.makeMetricLogger`, `zio.logging.logMetrics` and `zio.logging.logMetricsWith` install:

```scala
final case class MetricLogger(counter: Metric.Counter[Long], logLevelLabel: String) extends ZLogger[String, Unit]
```

Each increment carries the label `logLevelLabel` with the event's level label in lower case, such as `info`, `warn` or `off`, plus every metric tag set on the logging fiber with `ZIOAspect.tagged`. It never reads the message, so it does not format anything and costs one counter update per event.
