# LogFormat Reference

> `LogFormat` is a sealed trait that describes how one log line is laid out. You build a format from the values and functions in the `LogFormat` companion object, join formats with the instance methods, and pass the result to a logger through its configuration. Each part of a format either writes fixed text or reads one part of the log event, such as the level, the message, or an annotation.

The string form of the same DSL, such as `%timestamp{...}` and `%level`, is described in [Formatting Log Records](./formatting-log-records.md). This page covers only the Scala API.

## Building Blocks

The companion object provides values and functions that write fixed text or wrap other formats. Each row lists one member of `LogFormat`.

| Member                   | Signature                                                  | Description                                                            |
| ------------------------ | ---------------------------------------------------------- | ---------------------------------------------------------------------- |
| `LogFormat.text`         | `def text(value: => String): LogFormat`                    | Writes the given string.                                               |
| `LogFormat.space`        | `val space: LogFormat`                                     | Writes one space character.                                            |
| `LogFormat.newLine`      | `val newLine: LogFormat`                                   | Writes the platform line separator.                                    |
| `LogFormat.quote`        | `val quote: LogFormat`                                     | Writes one double quote character.                                     |
| `LogFormat.quoted`       | `def quoted(inner: LogFormat): LogFormat`                  | Wraps `inner` in double quotes.                                        |
| `LogFormat.bracketed`    | `def bracketed(inner: LogFormat): LogFormat`               | Wraps `inner` in square brackets.                                      |
| `LogFormat.bracketStart` | `val bracketStart: LogFormat`                              | Writes `[`.                                                            |
| `LogFormat.bracketEnd`   | `val bracketEnd: LogFormat`                                | Writes `]`.                                                            |
| `LogFormat.label`        | `def label(label: => String, value: LogFormat): LogFormat` | Writes `label=value`, where `value` is the output of the inner format. |
| `LogFormat.empty`        | `val empty: LogFormat`                                     | Writes nothing.                                                        |

`LogFormat.label` writes its name as a key, then the inner format as the value. In plain text output the result looks like `level=INFO`. The JSON output uses the same name as a JSON key.

## Composing Formats

The instance methods join formats together, add styling, or convert a format into a logger. Each method returns a new `LogFormat` and leaves the original unchanged.

| Method                   | Signature                                                        | Description                                                                                                 |
| ------------------------ | ---------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `+`                      | `final def +(other: LogFormat): LogFormat`                       | Joins two formats with no separator.                                                                        |
| `LogFormat#concat`       | `final def concat(other: LogFormat): LogFormat`                  | Same as `+`, with an alphanumeric name.                                                                     |
| `\|-\|`                  | `final def \|-\|(other: LogFormat): LogFormat`                   | Joins two formats with one space between them.                                                              |
| `LogFormat#spaced`       | `final def spaced(other: LogFormat): LogFormat`                  | Same as `\|-\|`, with an alphanumeric name.                                                                 |
| `LogFormat#color`        | `final def color(color: LogColor): LogFormat`                    | Wraps the output in the ANSI code for `LogFormat#color`, followed by the reset code.                                  |
| `LogFormat#highlight`    | `final def highlight: LogFormat`                                 | Colors the output by log level: ERROR red, WARN yellow, INFO cyan, DEBUG green, and all other levels white. |
| `LogFormat#highlight`    | `final def highlight(fn: LogLevel => LogColor): LogFormat`       | Colors the output with the color that `fn` returns for the event's level.                                   |
| `LogFormat#fixed`        | `final def fixed(size: Int): LogFormat`                          | Cuts the output to `size` characters, or pads it with spaces up to `size`. The output becomes plain text.   |
| `LogFormat#filter`       | `final def filter[M >: String](filter: LogFilter[M]): LogFormat` | Writes the output only when `LogFormat#filter` accepts the event.                                                     |
| `LogFormat#toLogger`     | `final def toLogger: ZLogger[String, String]`                    | Converts the format into a text logger that returns the formatted line.                                     |
| `LogFormat#toJsonLogger` | `final def toJsonLogger: ZLogger[String, String]`                | Converts the format into a logger that returns one JSON object per event.                                   |

The `LogFormat#fixed` method destroys structure. Use it only for plain text output, because it turns every part of the format into characters.

The `LogFormat#color` method and the `LogFormat#highlight` methods add ANSI escape codes. Terminals display them as colors, but log files show the raw codes unless the output goes to a console.

The following example builds a format from the building blocks and a logger name extractor:

```scala
import zio.logging.{ LogFormat, LoggerNameExtractor }

val customLine: LogFormat =
  LogFormat.bracketed(LogFormat.level) |-| LoggerNameExtractor.trace.toLogFormat() |-| LogFormat.quoted(LogFormat.line)
```

The `LogFormat#fixed` method gives the level a column of seven characters, so lines line up when the level names differ in length:

```scala
import zio.logging.LogFormat

val levelColumn: LogFormat = LogFormat.level.fixed(7)
```

To color only the cause, and to show it only when the event has one, the example combines `LogFormat#color` and `LogFormat#filter`:

```scala
import zio.logging.{ LogColor, LogFilter, LogFormat }

val errorDetails: LogFormat =
  (LogFormat.space + LogFormat.label("cause", LogFormat.cause).color(LogColor.RED)).filter(LogFilter.causeNonEmpty)
```

## Timestamps

Two members write the current time at the moment the line is built. The time does not come from the event itself.

| Member                | Signature                                                   | Description                                                                                    |
| --------------------- | ----------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `LogFormat.timestamp` | `val timestamp: LogFormat`                                  | Writes the time in ISO offset date-time format, from `DateTimeFormatter.ISO_OFFSET_DATE_TIME`. |
| `LogFormat.timestamp` | `def timestamp(formatter: => DateTimeFormatter): LogFormat` | Writes the time with the given `java.time.format.DateTimeFormatter`.                           |

The following example writes the time with a custom pattern:

```scala
import java.time.format.DateTimeFormatter
import zio.logging.LogFormat

val clockOnly: LogFormat = LogFormat.timestamp(DateTimeFormatter.ofPattern("HH:mm:ss"))
```

## Levels

Two members write the log level of the event. The level label comes from `zio.LogLevel`, so the warning level prints as `WARN`.

| Member                  | Signature                    | Description                                                                                                                             |
| ----------------------- | ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `LogFormat.level`       | `val level: LogFormat`       | Writes the level label, such as `INFO`.                                                                                                 |
| `LogFormat.levelSyslog` | `val levelSyslog: LogFormat` | Writes the syslog severity number of the level, such as `6` for INFO. FATAL is `2`, ERROR is `3`, WARN is `4`, DEBUG and TRACE are `7`. |

## Logger Name

Two members write the name of the logger that sent the event. The name comes from an extractor. The extractor members are listed in [LogFilter and LogGroup Reference](./reference-log-filter.md#loggernameextractor).

| Member                     | Signature                                                                                                       | Description                                                                                            |
| -------------------------- | --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| `LogFormat.loggerName`     | `def loggerName(loggerNameExtractor: LoggerNameExtractor, loggerNameDefault: String = "zio-logger"): LogFormat` | Writes the name from `loggerNameExtractor`, or `loggerNameDefault` when the extractor returns nothing. |
| `LogFormat.enclosingClass` | `val enclosingClass: LogFormat`                                                                                 | Writes the source file name from the `Trace` of the log call, or `not-available` when no trace exists. |

`LoggerNameExtractor#toLogFormat` calls `LogFormat.loggerName` for you, so you rarely call `LogFormat.loggerName` with an extractor directly.

## Message and Trace

These members write the message text and the source position of the log call.

| Member                | Signature                  | Description                                                                                    |
| --------------------- | -------------------------- | ---------------------------------------------------------------------------------------------- |
| `LogFormat.line`      | `val line: LogFormat`      | Writes the log message.                                                                        |
| `LogFormat.traceLine` | `val traceLine: LogFormat` | Writes the line number of the log call, from the `Trace`. Writes nothing when no trace exists. |
| `LogFormat.fiberId`   | `val fiberId: LogFormat`   | Writes the thread name of the fiber that logged the event.                                     |
| `LogFormat.cause`     | `val cause: LogFormat`     | Writes the cause of the event when it is not empty. Writes nothing otherwise.                  |

Use `LogFormat.cause` with `LogFormat#filter` and `LogFilter.causeNonEmpty` when the cause should appear only on failures. The Composing Formats section shows an example.

## Spans

Log spans measure how long a named block of work has run. These members write the elapsed time of each span as a key and value, such as `db=12ms`.

| Member            | Signature                           | Description                                                                     |
| ----------------- | ----------------------------------- | ------------------------------------------------------------------------------- |
| `LogFormat.spans` | `val spans: LogFormat`              | Writes every active span in the event.                                          |
| `LogFormat.span`  | `def span(name: String): LogFormat` | Writes the span with the label `name`. Writes nothing when no such span exists. |

## Annotations

Annotations are key and value pairs that the program attaches to log events. The string annotations come from the event itself. The typed annotations are stored in the logger context, which is described in [Logger Context and Annotations](./logger-context-and-annotations.md).

| Member                     | Signature                                                 | Description                                                                                                          |
| -------------------------- | --------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `LogFormat.annotation`     | `def annotation(name: String): LogFormat`                 | Writes the string annotation with key `name`, if the event has it.                                                   |
| `LogFormat.annotation`     | `def annotation[A](ann: LogAnnotation[A]): LogFormat`     | Same as `LogFormat.logAnnotation`.                                                                                             |
| `LogFormat.logAnnotation`  | `def logAnnotation[A](ann: LogAnnotation[A]): LogFormat`  | Writes the value of a typed `LogAnnotation` from the logger context, rendered with the annotation's `render` method. |
| `LogFormat.anyAnnotation`  | `def anyAnnotation(name: String): LogFormat`              | Writes the string annotation `name` from the event. When the event lacks it, looks for `name` in the logger context. |
| `LogFormat.annotations`    | `val annotations: LogFormat`                              | Writes every string annotation on the event.                                                                         |
| `LogFormat.annotations`    | `def annotations(excludeKeys: Set[String]): LogFormat`    | Writes every string annotation on the event, except the keys in `excludeKeys`.                                       |
| `LogFormat.logAnnotations` | `val logAnnotations: LogFormat`                           | Writes every typed annotation in the logger context.                                                                 |
| `LogFormat.logAnnotations` | `def logAnnotations(excludeKeys: Set[String]): LogFormat` | Writes every typed annotation in the logger context, except the keys in `excludeKeys`.                               |
| `LogFormat.allAnnotations` | `val allAnnotations: LogFormat`                           | Writes the string annotations on the event and the entries in the logger context.                                    |
| `LogFormat.allAnnotations` | `def allAnnotations(excludeKeys: Set[String]): LogFormat` | Same as `LogFormat.allAnnotations`, except the keys in `excludeKeys` are skipped.                                              |

The logger name travels as the string annotation whose key is `zio.logging.loggerNameAnnotationKey`, which holds the value `logger_name`. To write every annotation except the logger name, pass that key to `LogFormat.allAnnotations`:

```scala
import zio.logging.LogFormat

val withoutLoggerName: LogFormat = LogFormat.allAnnotations(Set(zio.logging.loggerNameAnnotationKey))
```

## Presets and Configuration

Two presets give ready-made formats. The `LogFormat.config` value reads a format from a configuration file.

| Member              | Signature                                                                 | Description                                                                                                                              |
| ------------------- | ------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `LogFormat.default` | `val default: LogFormat`                                                  | Writes the timestamp in a 32-character column, the level, the fiber thread name, the quoted message, and the cause when it is not empty. |
| `LogFormat.colored` | `val colored: LogFormat`                                                  | Writes the same parts as `default`, with colors on the timestamp, level, thread, and message.                                            |
| `LogFormat.config`  | `val config: Config[LogFormat]`                                           | Reads a format from a string in the configuration file, using the string form described in Formatting Log Records.                       |
| `LogFormat.parse`   | `def parse(pattern: String): Either[Parser.ParserError[String], Pattern]` | Parses the string form into a `LogFormat.Pattern` tree. Most code uses `LogFormat.config` instead.                                                 |

The console and file logger configurations read their `format` key through `LogFormat.config`. See [Configuration Keys Reference](./reference-configuration.md) for the keys.

## Custom Formats With LogFormat.make

`LogFormat.make` creates a format from a function. The function receives the parts of the log event and writes into a builder. Only use it when no combinator above fits your output.

:::warning[Internal builder type]
The first argument of the function is a `LogAppender`. The type is internal to the library, so you cannot name it in your own code, and its methods can change between releases. The builder is an advanced interface for writing a custom backend. Use the combinators on this page for ordinary formats.
:::

The signature of `LogFormat.make` is:

```scala
def make(
  format: (
    LogAppender,
    Trace,
    FiberId,
    LogLevel,
    () => String,
    Cause[Any],
    FiberRefs,
    List[LogSpan],
    Map[String, String]
  ) => Any
): LogFormat
```

For the string-based way to write output, and for a walkthrough of the builder, see [Formatting Log Records](./formatting-log-records.md).

## LogAppender Methods

A `LogAppender` writes text, numbers, causes, and key and value pairs to a backend. It is advanced, and it exists to write a custom backend, such as a logging library adapter. The methods below are the operations that the builder exposes to the function given to `LogFormat.make`. They are not part of the stable public API. For a worked custom format, see [Creating a Custom LogFormat](./formatting-log-records.md#creating-a-custom-logformat).

| Method                                | Signature                                                                        | Description                                                         |
| ------------------------------------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| `LogAppender#appendText`              | `def appendText(text: String): Unit`                                             | Writes unstructured text.                                           |
| `LogAppender#appendNumeric`           | `def appendNumeric[A](numeric: A): Unit`                                         | Writes a number.                                                    |
| `LogAppender#appendCause`             | `def appendCause(cause: Cause[Any]): Unit`                                       | Writes a cause. Backends that know causes can keep their structure. |
| `LogAppender#appendKeyValue`          | `def appendKeyValue(key: String, value: String): Unit`                           | Writes one key and value pair.                                      |
| `LogAppender#appendKeyValue`          | `def appendKeyValue(key: String, appendValue: LogAppender => Unit): Unit`        | Writes one key, then lets `appendValue` write the value.            |
| `LogAppender#appendKeyValues`         | `def appendKeyValues(keyValues: Iterable[(String, String)]): Unit`               | Writes many pairs, with a separator between each pair.              |
| `LogAppender#appendKeyValueSeparator` | `def appendKeyValueSeparator(): Unit`                                            | Writes the separator between two pairs.                             |
| `LogAppender#openLogEntry`            | `def openLogEntry(): Unit`                                                       | Marks the start of one log entry.                                   |
| `LogAppender#closeLogEntry`           | `def closeLogEntry(): Unit`                                                      | Marks the end of one log entry.                                     |
| `LogAppender#openKey`                 | `def openKey(): Unit`                                                            | Marks the start of a key.                                           |
| `LogAppender#closeKeyOpenValue`       | `def closeKeyOpenValue(): Unit`                                                  | Marks the end of a key and the start of its value.                  |
| `LogAppender#closeValue`              | `def closeValue(): Unit`                                                         | Marks the end of a value.                                           |
| `LogAppender#withAppendText`          | `final def withAppendText(f: (String => Unit) => (String => Unit)): LogAppender` | Returns a new appender whose text output passes through `f`.        |
