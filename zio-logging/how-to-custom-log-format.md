# How to Write a Custom Log Format

> You want log lines in a layout your team has agreed on, for example the timestamp without the time zone, a padded level, the logger name in brackets, the message, and the user ID, with the error only when there is one. ZIO Logging builds such a layout from small `LogFormat` values joined with operators, and the same layout can be written as a pattern string in configuration.

In this guide, we build the layout from the `LogFormat` combinators, write the same layout as a pattern string, derive a JSON variant, add one value that the combinators cannot express with `LogFormat.make`, and check the result in a test.

The steps are:

1. Build the layout from combinators.
2. Add colors for the terminal.
3. Write the same layout as a pattern string.
4. Derive a JSON variant.
5. Compute a value per line with `LogFormat.make`, only when needed.
6. Test the format.

## The Problem

The default console format writes `timestamp=... level=INFO thread=zio-fiber-7 message="..."`. If your team reads logs with tools that expect another layout, or simply wants a shorter line, you might be tempted to put the layout into each message by hand:

```scala
import java.time.LocalDateTime
import zio._

def chargeCard(userId: String): UIO[Unit] =
  ZIO.logInfo(s"${LocalDateTime.now()} INFO  [com.example.checkout] Charging card user_id=$userId")
```

Every call site now repeats the layout, the level is written twice, and nothing keeps the hundreds of calls consistent when the layout changes. The layout belongs in the logger, written once.

## Prerequisites

Add the core module to your build. Use `%%%` instead of `%%` for Scala.js or Scala Native:

```scala
libraryDependencies += "dev.zio" %% "zio-logging" % "2.5.5"
```

The examples use these imports:

```scala
import java.time.format.DateTimeFormatter
import zio._
import zio.logging.{
  ConsoleLoggerConfig,
  LogColor,
  LogFilter,
  LogFormat,
  LoggerNameExtractor,
  consoleJsonLogger,
  consoleLogger
}
```

## Build the Layout From Combinators

Each part of the line is one `LogFormat` value from the `LogFormat` companion object. `LogFormat#|-|` joins two formats with a space, and `LogFormat#+` joins them with nothing in between. We build the line in two parts: the timestamp, which changes on every call, and the body, which we can test with fixed inputs later:

```scala
val timePart: LogFormat =
  LogFormat.timestamp(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))

val body: LogFormat =
  LogFormat.level.fixed(5) |-|
    LogFormat.bracketed(LogFormat.loggerName(LoggerNameExtractor.loggerNameAnnotationOrTrace)) |-|
    LogFormat.line |-|
    LogFormat.anyAnnotation("user_id") +
    (LogFormat.space + LogFormat.cause).filter(LogFilter.causeNonEmpty)

val teamFormat: LogFormat = timePart |-| body
```

Each part does one thing:

- `LogFormat.timestamp` writes the current time with the given `DateTimeFormatter`. Without an argument, it uses ISO offset date-time.
- `LogFormat.level` writes the level label, such as `INFO` or `WARN`. `LogFormat#fixed` pads it with spaces, or cuts it, to exactly 5 characters, so the following columns line up.
- `LogFormat.loggerName` writes the logger name. `LoggerNameExtractor.loggerNameAnnotationOrTrace` takes it from the `zio.logging.loggerName` aspect when one is set, and otherwise from the class that made the log call. `LogFormat.bracketed` wraps it in `[` and `]`.
- `LogFormat.line` writes the message.
- `LogFormat.anyAnnotation` writes `user_id=<value>` when the line has a `user_id` annotation, either a ZIO core annotation or a `LogAnnotation`, and nothing otherwise.
- `LogFormat.cause` writes the error. `LogFormat#filter` with `LogFilter.causeNonEmpty` drops the space and the cause on lines without an error.

`LogFormat#|-|` binds more loosely than `LogFormat#+`, so the last two lines read as `anyAnnotation("user_id") + (...)`. The output is the same either way, because joining formats is associative.

To use the format, pass it to a logger in a `ConsoleLoggerConfig`, together with a filter:

```scala
val teamLogger: ZLayer[Any, Nothing, Unit] =
  Runtime.removeDefaultLoggers >>> consoleLogger(
    ConsoleLoggerConfig(teamFormat, LogFilter.LogLevelByNameConfig.default)
  )
```

A line from this logger looks like `2026-11-17 09:15:02.104 INFO  [com.example.checkout] Charging card user_id=u-42`.

## Add Colors for the Terminal

For local development, colors make the level easier to spot. `LogFormat#highlight` colors a format by the level of the line, and `LogFormat#color` gives it one fixed `LogColor`. Both write ANSI escape codes, so keep them out of formats that go to files or log collectors. The following variant colors the timestamp blue and the level by severity:

```scala
val teamColoredFormat: LogFormat =
  timePart.color(LogColor.BLUE) |-|
    LogFormat.level.fixed(5).highlight |-|
    LogFormat.bracketed(LogFormat.loggerName(LoggerNameExtractor.loggerNameAnnotationOrTrace)) |-|
    LogFormat.line |-|
    LogFormat.anyAnnotation("user_id") +
    (LogFormat.space + LogFormat.cause.highlight).filter(LogFilter.causeNonEmpty)
```

The default highlighter uses red for `ERROR`, yellow for `WARN`, cyan for `INFO`, green for `DEBUG`, and white for every other level, including `FATAL`. Pass your own `LogLevel => LogColor` function to `LogFormat#highlight` to change it.

## Write the Same Layout as a Pattern String

When the layout should change without a rebuild, write it as a pattern string under the `format` key of the logger configuration. Each element of the Scala format has a pattern counterpart:

```hocon
logger {
  format = "%timestamp{yyyy-MM-dd HH:mm:ss.SSS} %fixed{5}{%level} [%name] %message %kv{user_id} %cause"
}
```

`%fixed{5}{%level}` is `LogFormat.level.fixed(5)`, `%name` is the logger name taken from the annotation or the trace, and `%kv{user_id}` is `LogFormat.anyAnnotation("user_id")`. A bare `%cause` writes nothing on lines without an error, but the space before it is still written, so those lines end with a trailing space. If you want a `cause=` key, write `%label{cause}{%cause}`, which is written only on lines with an error. For colors, the names in `%color{...}` are upper case: `%color{BLUE}{%timestamp}` parses and `%color{blue}{%timestamp}` does not.

`consoleLogger()`, with no arguments, reads this key from the application's `ConfigProvider` (see [How to Set Log Levels Per Package From Config](how-to-log-levels-from-config.md)). A typo in the pattern fails the whole logger at startup with a configuration error, so check the string first with `LogFormat.Pattern.parse`:

```scala
LogFormat.Pattern.parse(
  "%timestamp{yyyy-MM-dd HH:mm:ss.SSS} %fixed{5}{%level} [%name] %message %kv{user_id} %cause"
).isRight
// res1: Boolean = true
```

See [Formatting Log Records](formatting-log-records.md#log-format-configuration) for every pattern element and its syntax rules.

## Derive a JSON Variant

For machine-read output, the same parts go into a JSON logger. `LogFormat.label` gives each part a JSON key, and annotation formats such as `LogFormat.anyAnnotation` already write their own key. Leave out `LogFormat#fixed`, the brackets and the colors, which only make sense in text:

```scala
val teamJsonFormat: LogFormat =
  LogFormat.label("timestamp", LogFormat.timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)) +
    LogFormat.label("level", LogFormat.level) +
    LogFormat.label("logger", LogFormat.loggerName(LoggerNameExtractor.loggerNameAnnotationOrTrace)) +
    LogFormat.label("message", LogFormat.line) +
    LogFormat.anyAnnotation("user_id") +
    LogFormat.label("cause", LogFormat.cause).filter(LogFilter.causeNonEmpty)

val teamJsonLogger: ZLayer[Any, Nothing, Unit] =
  Runtime.removeDefaultLoggers >>> consoleJsonLogger(
    ConsoleLoggerConfig(teamJsonFormat, LogFilter.LogLevelByNameConfig.default)
  )
```

Each line becomes one object, such as `{"timestamp":"...","level":"INFO","logger":"com.example.checkout","message":"Charging card","user_id":"u-42"}`. See [How to Log JSON](how-to-json-logs.md) for how values are written into the JSON.

## Compute a Value Per Line With LogFormat.make

The combinators cover the parts of a log event. When a part must be computed differently, for example the level in lower case because your log tool expects `info` and not `INFO`, write it with `LogFormat.make`. The function receives an appender and every input of the log event, and runs once per line:

```scala
val lowerCaseLevel: LogFormat =
  LogFormat.make { (builder, _, _, level, _, _, _, _, _) =>
    builder.appendText(level.label.toLowerCase)
  }
```

The nine arguments are the appender, the trace, the fiber ID, the level, the message, the cause, the fiber refs, the spans, and the ZIO core annotations. Write to the appender with its `appendText`, `appendNumeric`, `appendKeyValue` and `appendCause` methods; see [LogFormat Reference](reference-log-format.md#custom-formats-with-logformatmake) for each one.

Reach for `LogFormat.make` only when a value must be computed for each line. A value that never changes, such as the host name, is simpler as `LogFormat.text`. But note the opposite trap: the arguments of `LogFormat.text`, `LogFormat.label` and `LogFormat.timestamp(formatter)` look like by-name parameters, yet they are evaluated once, when the format is built, and not on each line. A format such as `LogFormat.text(java.time.Instant.now().toString)` writes the same instant on every line. Anything that changes between lines needs `LogFormat.make`, or one of the built-in formats such as `LogFormat.timestamp`, which reads the clock on each line.

## Test the Format

A format is a pure value, so you can check its output without running a program. `LogFormat#toLogger` turns it into a `ZLogger[String, String]` that returns the formatted line, and you call it with the inputs of one log event. We render the body of the team format for a `WARN` line with a logger name and a user ID passed as ZIO core annotations:

```scala
def render(format: LogFormat, level: LogLevel, message: String, annotations: Map[String, String]): String =
  format.toLogger(
    Trace.empty,
    FiberId.None,
    level,
    () => message,
    Cause.empty,
    FiberRefs.empty,
    Nil,
    annotations
  )
```

The output contains no timestamp, so it is the same on every run:

```scala
println(render(body, LogLevel.Warning, "Payment retried", Map("logger_name" -> "com.example.checkout", "user_id" -> "u-42")))
// WARN  [com.example.checkout] Payment retried user_id=u-42
println(render(lowerCaseLevel, LogLevel.Warning, "Payment retried", Map.empty))
// warn
```

In a ZIO Test spec, assert on the same `render` result with `assertTrue`. To check the lines a whole program writes, including annotations set with `LogAnnotation` values, use `ZTestLogger` and render each recorded entry; see [How to Test Log Output](how-to-test-log-output.md).

## See also

- [LogFormat Reference](reference-log-format.md) for every `LogFormat` value and operator.
- [Formatting Log Records](formatting-log-records.md) for the pattern syntax.
- [How to Log JSON](how-to-json-logs.md) for JSON output in detail.
- [How to Add a Correlation ID to Every Log Line](how-to-correlation-id.md) for printing a request ID with the format.
