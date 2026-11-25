# SLF4J v2

> [SLF4J](https://www.slf4j.org/) (Simple Logging Facade for Java) is the common logging API of the Java ecosystem; frameworks such as Logback and Log4j 2 implement it as providers. The `zio-logging-slf4j2` module is a backend for SLF4J v2: it sends every ZIO log line to the `slf4j-api` 2.x API, and the SLF4J provider on the classpath, for example Logback 1.3 or later, or Log4j 2 with `log4j-slf4j2-impl`, writes it. The module requires Java 17 or later.

In order to use this logging backend, we need to add the following line to our build.sbt file:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2" % "2.5.5"
```

## Choosing SLF4J v1 or v2

ZIO Logging has two SLF4J backends, and both require Java 17 or later. They differ in the version of `slf4j-api` they are built against and in how they pass annotations to the provider. This v2 backend passes annotations as [key-value pairs](https://www.slf4j.org/manual.html#fluent) of the SLF4J 2 fluent API, which Logback prints with `%kvp`; it never touches the thread's MDC. The [v1 backend](slf4j1.md) puts annotations into the MDC (Mapped Diagnostic Context, a per-thread map), which is the only option for providers that support only `slf4j-api` 1.7.x, such as Logback 1.2. Prefer v2 when your provider supports it. [How to Use ZIO Logging With Logback or Log4j 2](how-to-logback-log4j2.md) walks through both setups.

## Installing the Backend

To replace the default ZIO loggers with the `SLF4J` logger, combine `Runtime.removeDefaultLoggers` with `SLF4J.slf4j` in a logger layer:

```scala
import zio.Runtime
import zio.logging.backend.SLF4J

val logger = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
```

With the default setup, each ZIO log line becomes one SLF4J call:

* The SLF4J logger name comes from the `logger_name` annotation when it is set, and otherwise from the `zio.Trace` of the log call; for example, the trace `zio.logging.example.Slf4jSimpleApp.run(Slf4jSimpleApp.scala:17)` gives the logger name `zio.logging.example.Slf4jSimpleApp`.
* All annotations, except the logger name and the marker name, are passed as [key-value pairs](https://www.slf4j.org/manual.html#fluent).
* The cause, when there is one, is passed to SLF4J as a `Throwable`.

To set a custom logger name for a single effect, apply the `zio.logging.loggerName` aspect:

```scala
import zio.ZIO

ZIO.logInfo("Starting user operation") @@ zio.logging.loggerName("zio.logging.example.UserOperation")
```

To attach an SLF4J marker name to a single effect, apply the `SLF4J.logMarkerName` aspect:

```scala
import zio.ZIO
import zio.logging.backend.SLF4J

ZIO.logInfo("Confidential user operation") @@ SLF4J.logMarkerName("CONFIDENTIAL")
```

## Customizing the Backend

The no-argument `SLF4J.slf4j` layer is enough for most applications. The overloads below change what goes into each SLF4J call, and the last part of this section explains which levels are written. For the complete API, see [Backends and Bridges](reference-integrations.md).

### Format, Logger Name and Cause

`SLF4J.slf4j(format)` replaces the default format, `SLF4J.logFormatDefault`, which is `LogFormat.allAnnotations` (without the logger name and marker name keys) followed by `LogFormat.line` and `LogFormat.cause`. The appender behind this backend routes each part of a format to a different place: text such as `LogFormat.line` builds the message, key-value parts such as `LogFormat.allAnnotations` and `LogFormat.spans` become SLF4J key-value pairs, and `LogFormat.cause` becomes the `Throwable`. See [Creating a Custom LogFormat](formatting-log-records.md#creating-a-custom-logformat) for how formats talk to an appender.

`SLF4J.slf4j(format, loggerName, causeToThrowable)` also takes two functions. The `loggerName` function turns the `Trace` into a logger name when no `logger_name` annotation is set; `SLF4J.getLoggerName(default)` builds the standard one, which falls back to `default` (`"zio-slf4j-logger"` when omitted) for traces it cannot parse. The `causeToThrowable` function turns a ZIO `Cause` into the `Throwable` that SLF4J receives; the default, `SLF4J.causeToThrowableDefault`, wraps a non-empty cause in a `zio.FiberFailure`, so the provider prints the full ZIO cause.

The following layer adds every log span as a key-value pair, names lines whose trace cannot be parsed `billing-service`, and passes the original exception to SLF4J instead of a `FiberFailure`, so that Logback shows the exception's own class and stack trace:

```scala
import zio.{ Cause, Runtime }
import zio.logging.LogFormat
import zio.logging.backend.SLF4J

val originalThrowable: Cause[Any] => Option[Throwable] =
  cause => cause.failureOption.collect { case t: Throwable => t }.orElse(cause.dieOption)

val billingLogger =
  Runtime.removeDefaultLoggers >>> SLF4J.slf4j(
    SLF4J.logFormatDefault + LogFormat.spans,
    SLF4J.getLoggerName("billing-service"),
    originalThrowable
  )
```

With this function, a cause that holds only an interruption, or a failure that is not a `Throwable`, reaches SLF4J without a `Throwable`.

### Composing the Logger

`SLF4J.slf4j` returns a layer, so it cannot be combined with other logger operators. `SLF4J.slf4jLogger(format, loggerName, causeToThrowable)` returns the underlying `ZLogger[String, Unit]` instead. Use it when you want to wrap the SLF4J logger before installing it, for example to drop ZIO lines with a ZIO `LogFilter` before they are formatted and handed to SLF4J. The following logger keeps `INFO` and above, and only `WARN` and above for the `com.example.db` package:

```scala
import zio.{ LogLevel, Runtime }
import zio.logging.{ LogFilter, ZLoggerOps }
import zio.logging.backend.SLF4J

val slf4jLogger =
  SLF4J.slf4jLogger(SLF4J.logFormatDefault, SLF4J.getLoggerName(), SLF4J.causeToThrowableDefault)

val filteredLogger =
  slf4jLogger.filter(LogFilter.logLevelByName[String](LogLevel.Info, "com.example.db" -> LogLevel.Warning))

val filteredLayer = Runtime.removeDefaultLoggers >>> Runtime.addLogger(filteredLogger)
```

### Which Levels Are Written

This backend applies no ZIO `LogFilter` of its own. For each line, it asks the SLF4J logger whether the level is enabled (`isInfoEnabled`, `isDebugEnabled` and so on, with the marker when one is set), so the provider's configuration, such as `logback.xml` or `log4j2.xml`, decides which levels are written. A ZIO `LogFilter` runs only if you add one, as in the previous example. ZIO levels map to SLF4J levels as follows:

| ZIO `LogLevel` | SLF4J level |
| -------------- | ----------- |
| `All`          | `TRACE`     |
| `Trace`        | `TRACE`     |
| `Debug`        | `DEBUG`     |
| `Info`         | `INFO`      |
| `Warning`      | `WARN`      |
| `Error`        | `ERROR`     |
| `Fatal`        | `ERROR`     |
| `None`         | not logged  |

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples)

### SLF4J Logger Name and Annotations

The following application logs from two concurrent user operations. It sets a logger name and a log marker name with aspects, and it adds a `user` annotation and a trace ID:

```scala
import zio.logging.LogAnnotation
import zio.logging.backend.SLF4J
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZIOAspect, ZLayer, _ }

import java.util.UUID

object Slf4jSimpleApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val users = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _       <- ZIO.logInfo("Start")
      traceId <- ZIO.succeed(UUID.randomUUID())
      _       <- ZIO.foreachPar(users) { uId =>
        {
          ZIO.logInfo("Starting user operation") *>
            ZIO.logInfo("Confidential user operation") @@ SLF4J.logMarkerName("CONFIDENTIAL") *>
            ZIO.sleep(500.millis) *>
            ZIO.logInfo("Stopping user operation")
        } @@ ZIOAspect.annotated("user", uId.toString)
      } @@ LogAnnotation.TraceId(traceId) @@ zio.logging.loggerName("zio.logging.example.UserOperation")
      _       <- ZIO.logInfo("Done")
    } yield ExitCode.success

}
```

Logback configuration, which filters out logs with the `CONFIDENTIAL` marker:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <layout class="ch.qos.logback.classic.PatternLayout">
            <Pattern>%d{HH:mm:ss.SSS} [%thread] [%kvp] %-5level %logger{36} %msg%n</Pattern>
        </layout>
    </appender>
    <turboFilter class="ch.qos.logback.classic.turbo.MarkerFilter">
        <Name>CONFIDENTIAL_FILTER</Name>
        <Marker>CONFIDENTIAL</Marker>
        <OnMatch>DENY</OnMatch>
    </turboFilter>
    <root level="debug">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

Expected Console Output:
```
12:13:17.495 [ZScheduler-Worker-8] [] INFO  zio.logging.example.Slf4jSimpleApp Start
12:13:17.601 [ZScheduler-Worker-11] [trace_id="7826dd28-7e37-4b54-b84d-05399bb6cbeb" user="7b6a1af5-bad2-4846-aa49-138d31b40a24"] INFO  zio.logging.example.UserOperation Starting user operation
12:13:17.601 [ZScheduler-Worker-10] [trace_id="7826dd28-7e37-4b54-b84d-05399bb6cbeb" user="4df9cdbc-e770-4bc9-b884-756e671a6435"] INFO  zio.logging.example.UserOperation Starting user operation
12:13:18.167 [ZScheduler-Worker-13] [trace_id="7826dd28-7e37-4b54-b84d-05399bb6cbeb" user="7b6a1af5-bad2-4846-aa49-138d31b40a24"] INFO  zio.logging.example.UserOperation Stopping user operation
12:13:18.167 [ZScheduler-Worker-15] [trace_id="7826dd28-7e37-4b54-b84d-05399bb6cbeb" user="4df9cdbc-e770-4bc9-b884-756e671a6435"] INFO  zio.logging.example.UserOperation Stopping user operation
12:13:18.173 [ZScheduler-Worker-13] [] INFO  zio.logging.example.Slf4jSimpleApp Done
```
