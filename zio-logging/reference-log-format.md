# LogFormat Reference

> `LogFormat` is a sealed trait that describes how one log line is laid out. You build a format from the values and functions in the `LogFormat` companion object, join formats with the instance methods, and pass the result to a logger through its configuration. Each part of a format either writes fixed text or reads one part of the log event, such as the level, the message, or an annotation.

The string form of the same DSL, such as `%timestamp{...}` and `%level`, is described in [Formatting Log Records](./formatting-log-records.md). This page covers only the Scala API.

## Building Blocks

The companion object provides values and functions that write fixed text or wrap other formats. Each row lists one member of `LogFormat`.

| Member                   | Signature                                                  | Description                                                                                                                 |
| ------------------------ | ---------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `LogFormat.text`         | `def text(value: => String): LogFormat`                    | Writes the given string. `value` is evaluated once, when the format is built.                                               |
| `LogFormat.space`        | `val space: LogFormat`                                     | Writes one space character.                                                                                                 |
| `LogFormat.newLine`      | `val newLine: LogFormat`                                   | Writes the platform line separator.                                                                                         |
| `LogFormat.quote`        | `val quote: LogFormat`                                     | Writes one double quote character.                                                                                          |
| `LogFormat.quoted`       | `def quoted(inner: LogFormat): LogFormat`                  | Wraps `inner` in double quotes.                                                                                             |
| `LogFormat.bracketed`    | `def bracketed(inner: LogFormat): LogFormat`               | Wraps `inner` in square brackets.                                                                                           |
| `LogFormat.bracketStart` | `val bracketStart: LogFormat`                              | Writes `[`.                                                                                                                 |
| `LogFormat.bracketEnd`   | `val bracketEnd: LogFormat`                                | Writes `]`.                                                                                                                 |
| `LogFormat.label`        | `def label(label: => String, value: LogFormat): LogFormat` | Writes `label=value`, where `value` is the output of the inner format. `label` is evaluated once, when the format is built. |
| `LogFormat.empty`        | `val empty: LogFormat`                                     | Writes nothing.                                                                                                             |

`LogFormat.label` writes its name as a key, then the inner format as the value. In plain text output the result looks like `level=INFO`. The JSON output uses the same name as a JSON key.

:::note[By-name arguments are evaluated once]
`LogFormat.text`, `LogFormat.label` and `LogFormat.timestamp(formatter)` take by-name parameters, but each evaluates its argument once, when the format value is built, not for every line. `LogFormat.text(java.lang.System.currentTimeMillis().toString)` writes the same number on every line. For a value computed per line, write that part with [`LogFormat.make`](#custom-formats-with-logformatmake).
:::

## Composing Formats

The instance methods join formats together, add styling, or convert a format into a logger. Each method returns a new `LogFormat` and leaves the original unchanged.

| Method                   | Signature                                                        | Description                                                                                                 |
| ------------------------ | ---------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `+`                      | `final def +(other: LogFormat): LogFormat`                       | Joins two formats with no separator.                                                                        |
| `LogFormat#concat`       | `final def concat(other: LogFormat): LogFormat`                  | Same as `+`, with an alphanumeric name.                                                                     |
| `\|-\|`                  | `final def \|-\|(other: LogFormat): LogFormat`                   | Joins two formats with one space between them.                                                              |
| `LogFormat#spaced`       | `final def spaced(other: LogFormat): LogFormat`                  | Same as `\|-\|`, with an alphanumeric name.                                                                 |
| `LogFormat#color`        | `final def color(color: LogColor): LogFormat`                    | Wraps the output in the ANSI code of `color`, followed by the reset code.                                   |
| `LogFormat#highlight`    | `final def highlight: LogFormat`                                 | Colors the output by log level: ERROR red, WARN yellow, INFO cyan, DEBUG green, and all other levels white. |
| `LogFormat#highlight`    | `final def highlight(fn: LogLevel => LogColor): LogFormat`       | Colors the output with the color that `fn` returns for the event's level.                                   |
| `LogFormat#fixed`        | `final def fixed(size: Int): LogFormat`                          | Cuts the output to `size` characters, or pads it with spaces up to `size`. The output becomes plain text.   |
| `LogFormat#filter`       | `final def filter[M >: String](filter: LogFilter[M]): LogFormat` | Writes the output only when `filter` accepts the event.                                                     |
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

| Member                | Signature                                                   | Description                                                                                                                                                    |
| --------------------- | ----------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `LogFormat.timestamp` | `val timestamp: LogFormat`                                  | Writes the time in ISO offset date-time format, from `DateTimeFormatter.ISO_OFFSET_DATE_TIME`.                                                                 |
| `LogFormat.timestamp` | `def timestamp(formatter: => DateTimeFormatter): LogFormat` | Writes the time with the given `java.time.format.DateTimeFormatter`. `formatter` is evaluated once, when the format is built; the time is read for every line. |

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
| `LogFormat.annotation`     | `def annotation[A](ann: LogAnnotation[A]): LogFormat`     | Same as `LogFormat.logAnnotation`.                                                                                   |
| `LogFormat.logAnnotation`  | `def logAnnotation[A](ann: LogAnnotation[A]): LogFormat`  | Writes the value of a typed `LogAnnotation` from the logger context, rendered with the annotation's `render` method. |
| `LogFormat.anyAnnotation`  | `def anyAnnotation(name: String): LogFormat`              | Writes the string annotation `name` from the event. When the event lacks it, looks for `name` in the logger context. |
| `LogFormat.annotations`    | `val annotations: LogFormat`                              | Writes every string annotation on the event.                                                                         |
| `LogFormat.annotations`    | `def annotations(excludeKeys: Set[String]): LogFormat`    | Writes every string annotation on the event, except the keys in `excludeKeys`.                                       |
| `LogFormat.logAnnotations` | `val logAnnotations: LogFormat`                           | Writes every typed annotation in the logger context.                                                                 |
| `LogFormat.logAnnotations` | `def logAnnotations(excludeKeys: Set[String]): LogFormat` | Writes every typed annotation in the logger context, except the keys in `excludeKeys`.                               |
| `LogFormat.allAnnotations` | `val allAnnotations: LogFormat`                           | Writes the string annotations on the event and the entries in the logger context.                                    |
| `LogFormat.allAnnotations` | `def allAnnotations(excludeKeys: Set[String]): LogFormat` | Same as `LogFormat.allAnnotations`, except the keys in `excludeKeys` are skipped.                                    |

The annotation members differ in where they look, so choose by how the program sets the value:

| The program sets the value with                                                  | Use                                     | Key in the output       |
| -------------------------------------------------------------------------------- | --------------------------------------- | ----------------------- |
| `ZIO.logAnnotate("key", "value")` or `ZIOAspect.annotated` (a string annotation) | `LogFormat.annotation("key")`           | `key`                   |
| A typed `LogAnnotation`, such as `effect @@ LogAnnotation.UserId("u-1")`         | `LogFormat.logAnnotation(annotation)`   | the annotation's `name` |
| Either of the two, and the format should not care which                          | `LogFormat.anyAnnotation("key")`        | `key`                   |
| Many keys that you do not want to list one by one                                | `LogFormat.allAnnotations(excludeKeys)` | each annotation's key   |

`LogFormat.anyAnnotation` is what the `%kv{key}` element of a format string uses, and `LogFormat.allAnnotations` without the logger name is what `%kvs` uses. See [Log Annotations](reference-log-annotations.md) for the two kinds of annotation.

`LogFormat.allAnnotations` writes the string annotations first, then the logger context, and does not remove duplicate keys. When a string annotation and a `LogAnnotation` share a name, the key is written twice, which a JSON parser may reject or resolve by keeping only one of the values. Give the two kinds of annotation different names, or exclude one of them with `excludeKeys`.

The logger name travels as the string annotation whose key is `zio.logging.loggerNameAnnotationKey`, which holds the value `logger_name`. To write every annotation except the logger name, pass that key to `LogFormat.allAnnotations`:

```scala
import zio.logging.LogFormat

val withoutLoggerName: LogFormat = LogFormat.allAnnotations(Set(zio.logging.loggerNameAnnotationKey))
```

## Presets and Configuration

Two presets give ready-made formats. The `LogFormat.config` value reads a format from a configuration file.

| Member              | Signature                       | Description                                                                                                                                                                                                                 |
| ------------------- | ------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `LogFormat.default` | `val default: LogFormat`        | Writes labelled parts separated by spaces: `timestamp=` with the time in a 32-character column, `level=`, `thread=` with the fiber name, `message=` with the quoted message, and `cause=` only when the cause is not empty. |
| `LogFormat.colored` | `val colored: LogFormat`        | Writes the same labelled parts as `default`. The timestamp is blue and the thread is white; the level, the message and the cause are colored by log level, as `LogFormat#highlight` does.                                   |
| `LogFormat.config`  | `val config: Config[LogFormat]` | Reads a format from a string in the configuration file, using the string form described in [Formatting Log Records](./formatting-log-records.md#log-format-configuration).                                                  |

A line written with `LogFormat.default` looks like this:

```text
timestamp=2026-11-17T10:15:30.123456+01:00 level=INFO thread=zio-fiber-7 message="Order received"
```

:::caution[The default timestamp column is 32 characters]
`LogFormat.default` and `LogFormat.colored` write the timestamp with `LogFormat#fixed(32)`. When the JDK clock has more than microsecond precision, the ISO timestamp is longer than 32 characters and the end of the time-zone offset is cut, as in `2026-11-17T10:15:30.915147744+03`. Shorter timestamps are padded with spaces.
:::

To keep the full offset, build the same layout with `LogFormat.timestamp` and no fixed width:

```scala
import java.time.format.DateTimeFormatter
import zio.logging.{ LogFilter, LogFormat }
import zio.logging.LogFormat._

val fullTimestamp: LogFormat =
  label("timestamp", timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)) |-|
    label("level", level) |-|
    label("thread", fiberId) |-|
    label("message", quoted(line)) +
    (space + label("cause", cause)).filter(LogFilter.causeNonEmpty)
```

The console and file logger configurations read their `format` key through `LogFormat.config`. See [Configuration Keys Reference](./reference-configuration.md) for the keys.

## LogFormat.Pattern

`LogFormat.Pattern` is the parsed form of a format string. Each element of the string form, such as `%level` or `%label{name}{...}`, becomes one case of the sealed trait `Pattern`, and `Pattern#toLogFormat` turns the tree into a `LogFormat`. The elements are listed in [Log Format Configuration](./formatting-log-records.md#log-format-configuration), and the rules for escaping and labels in [Pattern Syntax Rules](./formatting-log-records.md#pattern-syntax-rules).

You rarely need `Pattern` directly, because `LogFormat.config` parses the string and converts it in one step. Use it when you want to validate a format string yourself, for example to report the parser error at startup, since `LogFormat.config` replaces the parser error with a generic message.

| Member                     | Signature                                                                 | Description                                                                                                                               |
| -------------------------- | ------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `LogFormat.Pattern.parse`  | `def parse(pattern: String): Either[Parser.ParserError[String], Pattern]` | Parses the string form into a `Pattern` tree. `Parser` is `zio.parser.Parser`.                                                            |
| `LogFormat.Pattern.config` | `val config: Config[Pattern]`                                             | Reads a string from the configuration and parses it, without converting it to a `LogFormat`.                                              |
| `Pattern#toLogFormat`      | `def toLogFormat: LogFormat`                                              | Converts the pattern into the `LogFormat` it describes.                                                                                   |
| `Pattern#isDefinedFilter`  | `def isDefinedFilter: Option[LogFilter[Any]]`                             | The filter that a `%label` around this pattern applies. It is `LogFilter.causeNonEmpty` for `%cause`, and `None` for most other elements. |

The following example parses a format string and keeps the parser error, so that a startup check can print where the string is wrong:

```scala
import zio.logging.LogFormat

val parsed: Either[String, LogFormat] =
  LogFormat.Pattern
    .parse("%timestamp %level [%fiberId] %name:%line %message %cause")
    .left
    .map(error => s"Invalid log format: $error")
    .map(_.toLogFormat)
```

## LogColor

`LogColor` is an ANSI color for `LogFormat#color` and for the function given to `LogFormat#highlight`. Its constructor is private, so the values below are the only colors.

| Member             | Signature                      | Description                                                                                 |
| ------------------ | ------------------------------ | ------------------------------------------------------------------------------------------- |
| `LogColor.RED`     | `val RED: LogColor`            | Red.                                                                                        |
| `LogColor.BLUE`    | `val BLUE: LogColor`           | Blue.                                                                                       |
| `LogColor.YELLOW`  | `val YELLOW: LogColor`         | Yellow.                                                                                     |
| `LogColor.CYAN`    | `val CYAN: LogColor`           | Cyan.                                                                                       |
| `LogColor.GREEN`   | `val GREEN: LogColor`          | Green.                                                                                      |
| `LogColor.MAGENTA` | `val MAGENTA: LogColor`        | Magenta.                                                                                    |
| `LogColor.WHITE`   | `val WHITE: LogColor`          | White.                                                                                      |
| `LogColor.RESET`   | `val RESET: LogColor`          | The reset code, which ends the current color. Use it for levels that should not be colored. |
| `LogColor.config`  | `val config: Config[LogColor]` | Reads a color name from a string, in any case, such as `red` or `RED`.                      |

`LogColor.config` accepts any case, but the `%color{...}` element of a format string accepts only upper-case names.

The default highlighter of `LogFormat#highlight` writes FATAL in white. The following highlighter gives FATAL its own color and leaves TRACE uncolored:

```scala
import zio.LogLevel
import zio.logging.{ LogColor, LogFormat }

val levelColors: LogLevel => LogColor = {
  case LogLevel.Fatal   => LogColor.MAGENTA
  case LogLevel.Error   => LogColor.RED
  case LogLevel.Warning => LogColor.YELLOW
  case LogLevel.Info    => LogColor.CYAN
  case LogLevel.Debug   => LogColor.GREEN
  case _                => LogColor.RESET
}

val coloredLevel: LogFormat = LogFormat.level.fixed(5).highlight(levelColors)
```

## Custom Formats With LogFormat.make

`LogFormat.make` creates a format from a function. The function receives the parts of the log event and writes into a builder. Only use it when no combinator above fits your output.

:::warning[Internal builder type]
The first argument of the function is a `LogAppender`. The type is internal to the library, so you cannot name it in your own code, and its methods can change between releases. The builder is an advanced interface for writing a custom backend. Use the combinators on this page for ordinary formats.
:::

Choose `LogFormat.make` over the combinators in two cases: when a value must be computed for every line from something the members above do not read, such as the clock in another unit or the name of the current thread, and when a value must be rendered differently from the members above, such as the level in lower case. For text that never changes, `LogFormat.text` is simpler. For anything the members above already write, combine those members instead.

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

The following format writes the time as epoch milliseconds, which some log stores expect. The function runs for every line, so each line gets the current time. `LogFormat.text(System.currentTimeMillis().toString)` would instead repeat one fixed time, taken when the format is built, on every line:

```scala
import zio.logging.LogFormat

val epochMillis: LogFormat =
  LogFormat.make { (builder, _, _, _, _, _, _, _, _) =>
    builder.appendNumeric(java.lang.System.currentTimeMillis())
  }

val epochLine: LogFormat =
  LogFormat.label("ts", epochMillis) |-| LogFormat.label("level", LogFormat.level) |-| LogFormat.label("message", LogFormat.line)
```

Because the value is written with `LogAppender#appendNumeric`, the JSON loggers write `ts` as a JSON number, not as a string. For the string-based way to write output, and for a walkthrough of the builder, see [Formatting Log Records](./formatting-log-records.md#creating-a-custom-logformat) and [How to Write a Custom Log Format](how-to-custom-log-format.md).

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
