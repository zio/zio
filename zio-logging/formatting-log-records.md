# Formatting Log Records

> A `LogFormat` represents a DSL to describe the format of text log messages. Loggers accept a format through `ConsoleLoggerConfig` or `FileLoggerConfig`. Every member of the Scala DSL is listed in the [LogFormat Reference](reference-log-format.md).

To build a format from the DSL and pass it to a console logger layer:

```scala
import zio.logging.LogFormat._
import zio.logging.{ consoleLogger, ConsoleLoggerConfig, LogFilter, LogFormat }

val myLogFormat: LogFormat = timestamp.fixed(32) |-| level |-| label("message", quoted(line))
val myConsoleLogger        = consoleLogger(ConsoleLoggerConfig(myLogFormat, LogFilter.LogLevelByNameConfig.default))
```

`LogFormat#filter` returns a new log format that produces the same result, if `LogFilter` is satisfied. The following format only outputs the `cause` label when the log event has a non-empty cause:

```scala
import zio.logging.{ LogFilter, LogFormat }

val causeFormat: LogFormat = LogFormat.label("cause", LogFormat.cause).filter(LogFilter.causeNonEmpty)
```

## Log Format Configuration

The string representation of a `LogFormat` uses the following tokens:

| format                                   | description                                                                                                                                                                |
| ---------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| %timestamp\{date-format\}%timestamp | Timestamp, where date format is a `java.time.format.DateTimeFormatter` pattern; without a pattern, ISO offset date-time,example: `%timestamp{yyyy-MM-dd'T'HH:mm:ssZ}` |
| %fiberId                                 | Fiber name, such as `zio-fiber-7`                                                                                                                                          |
| %fixed\{size\}\{format\}                 | Fixed size for format,example: `%fixed{7}{%level}`                                                                                                                    |
| %label\{name\}\{format\}                 | Labeled format, example: `%label{abcSpan}{%span{abc}}`. When `format` contains `%cause`, the label is written only for events with a cause                                 |
| %color\{LogColor\}\{format\}             | Colored format, log color values in upper case: `RED`, `BLUE`, `YELLOW`, `CYAN`, `GREEN`, `MAGENTA`, `WHITE`, `RESET`,example: `%color{CYAN}{%timestamp}`             |
| %level                                   | Log level                                                                                                                                                                  |
| %name                                    | Logger name (from the `logger_name` annotation or `Trace`)                                                                                                                 |
| %line                                    | Line number of the log call (from `Trace`)                                                                                                                                 |
| %message                                 | Log message                                                                                                                                                                |
| %cause                                   | Cause                                                                                                                                                                      |
| %kvs                                     | Key-values: every string annotation and every `LogAnnotation` in the logger context, except `logger_name`                                                                  |
| %kv\{key\}                               | Key-value: the string annotation `key`, or, when there is none, the `LogAnnotation` named `key` in the logger context                                                      |
| %spans                                   | All log spans                                                                                                                                                              |
| %span\{span\}                            | Log span with the given name                                                                                                                                               |
| %highlight\{format\}                     | Highlight given format with colors based on log Level                                                                                                                      |
| %%                                       | % character                                                                                                                                                                |
| %\{                                      | \{ character                                                                                                                                                               |
| %\}                                      | \} character                                                                                                                                                               |

Examples of format strings:

```
%timestamp %level [%fiberId] %name:%line %message %cause

%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause}

%label{timestamp}{%fixed{32}{%timestamp}} %label{level}{%level} %label{thread}{%fiberId} %label{message}{%message} %label{cause}{%cause}
```

The parsed form of a format string is a `LogFormat.Pattern`. See [LogFormat.Pattern](reference-log-format.md#logformatpattern) for parsing a string in code.

## Pattern Syntax Rules

When a format string does not parse, `LogFormat.config` fails with `Expected a LogFormat, but found <the string>`, without the position of the error. These rules cover the cases that most often fail or surprise:

- Plain text cannot contain a bare `%`, `{` or `}`. Write `%%`, `%{` and `%}` instead, as in `100%% done` or `%{id%}`. The name in `%label{name}` cannot contain these characters at all.
- Color names in `%color{...}` must be upper case. `%color{RED}{%level}` parses, `%color{red}{%level}` does not. Only `LogColor.config`, which reads a single color value, accepts any case.
- When the inner format of `%label{name}{...}` contains `%cause` directly, the whole label is written only for events that have a cause. This keeps `cause=` out of ordinary lines, but it also means that `%label{message}{%message %cause}` writes nothing for events without a cause. Give `%cause` a label of its own. When `%cause` is nested inside another element, as in `%label{cause}{%highlight{%cause}}`, the label is written on every line.
- `%kvs` leaves out the `logger_name` annotation; `%name` writes the logger name instead. To write it as a key-value, add `%kv{logger_name}`.
- The argument of `%timestamp{...}` must be a valid `DateTimeFormatter` pattern, and the size in `%fixed{...}` must be a number.

To check a pattern before it reaches a configuration file, parse it with `LogFormat.Pattern.parse`. The upper-case color name parses and the lower-case one does not:

```scala
LogFormat.Pattern.parse("%color{RED}{%level} 100%% done").isRight
// res0: Boolean = true
LogFormat.Pattern.parse("%color{red}{%level}").isRight
// res1: Boolean = false
```

## Creating a Custom LogFormat

Use `LogFormat.make` when the DSL cannot express the output you need. The function receives a `LogAppender` and all log inputs that ZIO core provides for each log event:

```scala
object LogFormat {
  def make(format: (LogAppender, Trace, FiberId, LogLevel, () => String, Cause[Any], FiberRefs, List[LogSpan], Map[String, String]) => Any): LogFormat
}
```

The format function arguments can be split into two sections:

* `LogAppender` - the output sink for the formatted log line
* All other arguments - the log inputs provided by ZIO core logging:
    * `Trace` - current trace (`zio.Trace`)
    * `FiberId` - fiber id (`zio.FiberId`)
    * `LogLevel` - log level (`zio.LogLevel`)
    * `() => String` - log message
    * `Cause[Any]` - cause (`zio.Cause`)
    * `FiberRefs` - fiber refs (`zio.FiberRefs`), collection of `zio.FiberRef` - ZIO's equivalent of Java's ThreadLocal
    * `List[LogSpan]` - log spans (`zio.LogSpan`)
    * `Map[String, String]` - ZIO core log annotations values, where key is annotation key/name, and value is annotation value

The function runs once for every log line, so anything it computes is computed per line.

The following predefined-style formats show how `LogFormat.make` combines the `LogAppender` functions described in the next section. Each block uses only the `LogAppender` argument and the annotations or cause that it needs:

```scala
import zio.logging.LogFormat

def annotation(name: String): LogFormat =
  LogFormat.make { (builder, _, _, _, _, _, _, _, annotations) =>
    annotations.get(name).foreach { value =>
      builder.appendKeyValue(name, value)
    }
  }

val cause: LogFormat =
  LogFormat.make { (builder, _, _, _, _, cause, _, _, _) =>
    if (!cause.isEmpty) {
      builder.appendCause(cause)
    }
  }

def text(value: => String): LogFormat =
  LogFormat.make { (builder, _, _, _, _, _, _, _, _) =>
    builder.appendText(value)
  }
```

This `text` function evaluates `value` again for every line. The library's own `LogFormat.text` evaluates its argument only once, when the format is built, so use a `LogFormat.make` function like this one for a value that changes between lines.

## LogFormat and LogAppender

The `LogAppender` provides these essential functions, which the predefined log formats use:

* `appendCause(cause: Cause[Any])` - appends a `zio.Cause` to the log, some logging backends may have special support for logging failures
* `appendNumeric[A](numeric: A)` - appends a numeric value to the log
* `appendText(text: String)` - appends unstructured text to the log
* `appendKeyValue(key: String, value: String)` - appends a key/value string pair to the log

How these functions render depends on the logging backend:

* [SLF4J v1](slf4j1.md) logging backend - key/value pairs from annotations and spans are put into the SLF4J [MDC context](https://logback.qos.ch/manual/mdc.html) while the line is logged, the cause is transformed to a `Throwable` and passed as the SLF4J throwable, and all other text and numeric parts, including `LogFormat.label` output written as `name=value`, are added to the SLF4J log message.
* [SLF4J v2](slf4j2.md) logging backend - the same as SLF4J v1, except that key/value pairs are added to the SLF4J 2 event as key-value pairs (`addKeyValue`), not to the MDC. Use a provider layout that prints key-value pairs, such as `%kvp` in Logback, to see them.
* Console and file logging backends - in general all values are added to the log line, `Cause.prettyPrint` renders the cause details.
* JSON loggers (`consoleJsonLogger`, `fileJsonLogger`) - each label and key/value pair becomes a JSON field, see [How to Log JSON](how-to-json-logs.md).
