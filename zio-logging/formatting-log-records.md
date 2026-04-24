# Formatting Log Records

> A `LogFormat` represents a DSL to describe the format of text log messages.

A `LogFormat` represents a DSL to describe the format of text log messages.

[//]: # (TODO: make snippet type-checked using mdoc)

```scala

val myLogFormat = timestamp.fixed(32) |-| level |-| label("message", quoted(line))
val myConsoleLogger = console(myLogFormat)
```

`LogFormat.filter` returns a new log format that produces the same result, if `LogFilter` is satisfied.

```scala

LogFormat.label("cause", LogFormat.cause).filter(LogFilter.causeNonEmpty)
```

## Log Format Configuration

String representation of LogFormat:

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

examples:

```
%timestamp %level [%fiberId] %name:%line %message %cause

%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause}

%label{timestamp}{%fixed{32}{%timestamp}} %label{level}{%level} %label{thread}{%fiberId} %label{message}{%message} %label{cause}{%cause}
```

## LogFormat and LogAppender

A `LogFormat` represents a DSL to describe the format of text log messages.

A `LogAppender` is a low-level interface designed to be the bridge between, ZIO Logging and logging backends, such as
Logback.
This interface is slightly higher-level than a string builder, because it allows for structured logging,
and preserves all ZIO-specific information about runtime failures.

`LogFormat` may be created by following function:

```scala
object LogFormat {
  def make(format: (LogAppender, Trace, FiberId, LogLevel, () => String, Cause[Any], FiberRefs, List[LogSpan], Map[String, String]) => Any): LogFormat
}
```

format function arguments can be split to two sections:

* LogAppender
* all others - all log inputs provided by ZIO core logging:
    * Trace - current trace (`zio.Trace`)
    * FiberId - fiber id (`zio.FiberId`)
    * LogLevel - log level (`zio.LogLevel`)
    * () => String - log message
    * Cause[Any] - cause (`zio.Cause`)
    * FiberRefs - fiber refs (`zio.FiberRefs`), collection of `zio.FiberRef` - ZIO's equivalent of Java's ThreadLocal
    * List[LogSpan] - log spans  (`zio.LogSpan`)
    * Map[String, String] - ZIO core log annotations values, where key is annotation key/name, and value is annotation
      value

essential `LogAppender` functions, which are used in predefined log formats:

* `def appendCause(cause: Cause[Any])` - appends a `zio.Cause` to the log, some logging backends may have special
  support for logging failures
* `def appendNumeric[A](numeric: A)` - appends a numeric value to the log
* `def appendText(text: String)` - appends unstructured text to the log
* `def appendKeyValue(key: String, value: String)` - appends a key/value string pair to the log

then it depends on the specific logging backend how these functions are implemented with respect to the backend output,
for example:

* [slf4j v1](slf4j1.md) logging backend - key/value is appended to
  slf4j [MDC context](https://logback.qos.ch/manual/mdc.html), Cause is transformed to Throwable and placed to slf4j
  throwable section, all other text and numeric parts are added to slf4j log message
* console logging backend - in general all values are added to log line, `Cause.prettyPrint` is used to log cause
  details

example of some predefined log formats implementations:

```scala
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
