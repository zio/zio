# Formatting Log Records

> A `LogFormat` represents a DSL to describe the format of text log messages. Loggers accept a format through `ConsoleLoggerConfig` or `FileLoggerConfig`.

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

| format                                   | description                                                                                                                              |
| ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| %timestamp\{date-format\}%timestamp | Timestamp, where date format is `java.time.format.DateTimeFormatter`,example: `%timestamp{yyyy-MM-dd'T'HH:mm:ssZ}`                  |
| %fiberId                                 | Fiber Id                                                                                                                                 |
| %fixed\{size}\{format\}                  | Fixed size for format,example: `%fixed{7}{%level}`                                                                                  |
| %label\{name}\{format\}                  | Labeled format,example: `%label{abcSpan}{%span{abc}}`                                                                               |
| %color\{LogColor}\{format\}              | Colored format, log color values: `RED`, `BLUE`, `YELLOW`, `CYAN`, `GREEN`, `MAGENTA`, `WHITE`, example: `%color{CYAN}{%timestamp}` |
| %level                                   | Log level                                                                                                                                |
| %name                                    | Logger name (from logger name annotation or Trace)                                                                                       |
| %line                                    | Log/trace line (from Trace)                                                                                                              |
| %message                                 | Log message                                                                                                                              |
| %cause                                   | Cause                                                                                                                                    |
| %kvs                                     | Key-values - all annotations                                                                                                             |
| %kv\{key\}                               | Key-value - annotation with given key                                                                                                    |
| %spans                                   | All log spans                                                                                                                            |
| %span\{span\}                            | Log spans with key                                                                                                                       |
| %highlight\{format\}                     | Highlight given format with colors based on log Level                                                                                    |
| %%                                       | % character                                                                                                                              |
| %\{                                      | \{ character                                                                                                                             |
| %\}                                      | \} character                                                                                                                             |

Examples of format strings:

```
%timestamp %level [%fiberId] %name:%line %message %cause

%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause}

%label{timestamp}{%fixed{32}{%timestamp}} %label{level}{%level} %label{thread}{%fiberId} %label{message}{%message} %label{cause}{%cause}
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
    * `Map[String, String]` - ZIO core log annotations values, where key is annotation key/name, and value is annotation
      value

The `LogAppender` provides these essential functions, which the predefined log formats use:

* `appendCause(cause: Cause[Any])` - appends a `zio.Cause` to the log, some logging backends may have special
  support for logging failures
* `appendNumeric[A](numeric: A)` - appends a numeric value to the log
* `appendText(text: String)` - appends unstructured text to the log
* `appendKeyValue(key: String, value: String)` - appends a key/value string pair to the log

How these functions render depends on the logging backend. For example:

* [slf4j v1](slf4j1.md) logging backend - key/value is appended to
  slf4j [MDC context](https://logback.qos.ch/manual/mdc.html), Cause is transformed to Throwable and placed to slf4j
  throwable section, all other text and numeric parts are added to slf4j log message
* console logging backend - in general all values are added to log line, `Cause.prettyPrint` renders the cause
  details

The following predefined-style formats show how `LogFormat.make` combines these functions. Each block uses only the `LogAppender` argument and the annotations or cause that it needs:

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
