# How to Log JSON

> You want your application to write each log line as a JSON object, so that a log shipper or search tool can read each field by name without parsing free text. The default text layout puts the timestamp, level, message, and annotations into one string, so a tool has to guess where each value starts and ends. ZIO Logging solves this with `consoleJsonLogger` for the console and `fileJsonLogger` for a file. Both take the same `LogFormat` that the text loggers use, but they emit one JSON object per line.

In this guide, we define a format that picks the fields we want, install a JSON logger, and then read what each field looks like in the output.

The steps are:

1. Define the fields with a `LogFormat`.
2. Install the console JSON logger.
3. Write the same logs to a file.
4. Read the output.

## Prerequisites

Add the core module to your build:

```scala
libraryDependencies += "dev.zio" %% "zio-logging" % "2.5.5"
```

The examples below use these imports:

```scala
import java.time.format.DateTimeFormatter
import java.util.UUID
import zio.{ Cause, ExitCode, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }
import zio.logging.{ ConsoleLoggerConfig, LogAnnotation, LogFilter, LogFormat, consoleJsonLogger }
```

`LogFormat` is the building block for log layouts. A `LogFormat` value describes which parts of a log event to write, and `LogFormat#+` joins two formats together.

## Choose the Log Fields

We build a `LogFormat` where each field is wrapped in `LogFormat.label`. A label gives the field a JSON key. Text without a label is not lost, but all of it goes into one field named `text_content`, so a shipper cannot tell the parts apart. When the unlabelled text of a line is only spaces, such as the separators that `LogFormat#|-|` writes between labels, no `text_content` field is written. The following format labels every field:

```scala
val jsonFormat: LogFormat =
  LogFormat.label("timestamp", LogFormat.timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)) +
    LogFormat.label("level", LogFormat.level) +
    LogFormat.label("fiber", LogFormat.fiberId) +
    LogFormat.label("message", LogFormat.line) +
    LogFormat.label("cause", LogFormat.cause).filter(LogFilter.causeNonEmpty) +
    LogFormat.allAnnotations(Set.empty) +
    LogFormat.spans

val jsonConfig: ConsoleLoggerConfig = ConsoleLoggerConfig(jsonFormat, LogFilter.LogLevelByNameConfig.default)
```

Each part of this format maps to a JSON key:

- `LogFormat.line` is the log message, which becomes the `message` key.
- `LogFormat.cause` is the error cause. The `filter(LogFilter.causeNonEmpty)` call drops the `cause` key when there is no error, so successful log lines stay short.
- `LogFormat.allAnnotations` writes every log annotation as its own key. An annotation's key is its name, such as `user_id` for `LogAnnotation.UserId`.
- `LogFormat.spans` writes each active span as a key with its elapsed time, such as `"checkout":"12ms"`.

String values in the JSON are escaped, so a message that contains quotes or line breaks still produces one valid JSON object per line. A few kinds of value are written differently:

- A value that is itself a valid JSON object or array is embedded as JSON, not as a string. The `ConsoleJsonApp` example renders a `user` annotation as JSON, and the line contains `"user":{"first_name":"John","last_name":"Doe"}`.
- When a value starts and ends with a double quote, those two quotes are removed. This is why `LogFormat.quoted(LogFormat.line)` from a text format still writes a plain JSON string, but it also removes the quotes from a message such as `"done"`.
- A value made only of spaces is written as an empty string, `""`.
- ANSI color codes from `LogFormat#color` and `LogFormat#highlight` are removed, so a colored format writes plain values. `LogFormat#fixed` still pads or cuts the value, so leave it out of JSON formats.

:::caution
Keep a JSON value in a label of its own. When one label holds both plain text and a JSON value, the JSON logger can write a value that is not valid JSON.
:::

## Install the Console JSON Logger

`Runtime.removeDefaultLoggers` stops ZIO from also writing its default text logger, so each event appears only once. We replace the default logger with the JSON logger in the `bootstrap` layer:

```scala
object JsonLogsApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger(jsonConfig)

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- ZIO.logInfo("Order received")
      _ <- ZIO
             .fail(new RuntimeException("card declined"))
             .catchAll(e => ZIO.logErrorCause("Payment failed", Cause.fail(e)))
    } yield ExitCode.success) @@ LogAnnotation.UserId("user-42") @@ LogAnnotation.TraceId(UUID.randomUUID())
}
```

The `@@ LogAnnotation.UserId(...)` and `@@ LogAnnotation.TraceId(...)` calls attach annotations to every log line inside the effect, so each line carries the same `user_id` and `trace_id` values.

Each line that the program writes is one JSON object. The shape of the two lines looks like this, with long values shortened:

```text
{"timestamp":"2026-11-10T09:15:02.104+01:00","level":"INFO","fiber":"zio-fiber-7","message":"Order received","user_id":"user-42","trace_id":"3f2c9d1e-..."}
{"timestamp":"2026-11-10T09:15:02.109+01:00","level":"ERROR","fiber":"zio-fiber-7","message":"Payment failed","cause":"...java.lang.RuntimeException: card declined...","user_id":"user-42","trace_id":"3f2c9d1e-..."}
```

The `cause` key appears only on the second line, because the first line has no error.

## Write the Same Logs to a File

We use `fileJsonLogger` when the logs must go to a file. It takes a `FileLoggerConfig`, which holds the destination path, the format, and the filter. The file logger is available on the JVM and Native platforms only. Scala.js has no file logger. The following layer writes the same format to a file:

```scala
import java.net.URI
import java.nio.file.Paths
import zio.logging.{ FileLoggerConfig, fileJsonLogger }

val jsonFileLayer: ZLayer[Any, Nothing, Unit] = fileJsonLogger(
  FileLoggerConfig(Paths.get(URI.create("file:///tmp/orders.json.log")), jsonFormat, LogFilter.LogLevelByNameConfig.default)
)
```

The destination is a `file:///` URI. `FileLoggerConfig` parses it with `Paths.get(URI.create(...))`, so a plain path such as `/tmp/orders.json.log` does not work in the `path` key. For rotation and file naming, see [How to Log to a File With Rotation](./how-to-file-logging-rotation.md).

## Read the Output

Each line is one JSON object, so a log shipper can split the stream on newlines and parse each line on its own. The keys come from the labels you chose in the format. If you rename a label, the key changes too. Downstream dashboards and alerts that read the old key stop matching, so pick the key names once and keep them stable.

Use `LogFormat.default` when you want the standard text layout from the console and file loggers for development. Use the JSON format for production, where machines read the output.

:::tip
To choose the fields with a config file instead of code, set the `logger/format` key to a format string, as the `ConsoleJsonApp` example does in `examples/core/jvm/src/main/scala/zio/logging/example/ConsoleJsonApp.scala`. The format string uses the same `%label{key}{%field}` syntax, such as `%label{message}{%message}`.
:::

## See also

- [Console Logger](./console-logger.md) for the full list of console logger configuration keys and the JSON console example.
- [File Logger](./file-logger.md) for the file logger configuration keys.
- [Formatting Log Records](./formatting-log-records.md) for the `LogFormat` building blocks.
- [Logger Context and Annotations](./logger-context-and-annotations.md) for how annotations and spans are attached to log events.
- [How to Set Log Levels Per Package From Config](./how-to-log-levels-from-config.md) for filtering which lines are written.
