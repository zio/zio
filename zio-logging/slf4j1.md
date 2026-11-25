# SLF4J v1

> [SLF4J](https://www.slf4j.org/) (Simple Logging Facade for Java) is the common logging API of the Java ecosystem; frameworks such as Logback and Log4j 2 implement it as providers. The `zio-logging-slf4j` module is a backend for SLF4J v1: it sends every ZIO log line to the `slf4j-api` 1.7.x API, and the SLF4J provider on the classpath, for example Logback 1.2, writes it. The module requires Java 17 or later.

In order to use this logging backend, we need to add the following line to our build.sbt file:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j" % "2.5.5"
```

## Choosing SLF4J v1 or v2

ZIO Logging has two SLF4J backends, and both require Java 17 or later. They differ in the version of `slf4j-api` they are built against and in how they pass annotations to the provider. This v1 backend puts annotations into the [MDC](https://logback.qos.ch/manual/mdc.html) (Mapped Diagnostic Context, a per-thread map that the provider can print, for example with `%mdc` in Logback). The [v2 backend](slf4j2.md) passes them as [key-value pairs](https://www.slf4j.org/manual.html#fluent) of the SLF4J 2 API. Use v1 when your provider only supports `slf4j-api` 1.7.x, such as Logback 1.2; use v2 for Logback 1.3 or later and for Log4j 2 with `log4j-slf4j2-impl`. [How to Use ZIO Logging With Logback or Log4j 2](how-to-logback-log4j2.md) walks through both setups.

:::note[How the MDC is set]
For each line, this backend formats the annotations into a map, then, on the same thread and before the log call returns, it saves the thread's current MDC, replaces it with that map, calls the SLF4J logger, and restores the saved MDC. Fibers that log in parallel therefore never see each other's annotations. Two effects follow from this design. While a line with annotations is logged, the thread's existing MDC is replaced rather than merged, so MDC values that Java code put on that thread do not appear in the line. When a line has no annotations, the backend leaves the thread's MDC untouched, so any values that Java code left in the MDC of that worker thread appear in the line.
:::

## Installing the Backend

To replace the default ZIO loggers with the `SLF4J` logger, combine `Runtime.removeDefaultLoggers` with `SLF4J.slf4j` in a logger layer:

```scala
import zio.Runtime
import zio.logging.backend.SLF4J

val logger = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
```

With the default setup, each ZIO log line becomes one SLF4J call:

* The SLF4J logger name comes from the `logger_name` annotation when it is set, and otherwise from the `zio.Trace` of the log call; for example, the trace `zio.logging.example.Slf4jSimpleApp.run(Slf4jSimpleApp.scala:17)` gives the logger name `zio.logging.example.Slf4jSimpleApp`.
* All annotations, except the logger name and the marker name, are placed into the MDC.
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

`SLF4J.slf4j(format)` replaces the default format, `SLF4J.logFormatDefault`, which is `LogFormat.allAnnotations` (without the logger name and marker name keys) followed by `LogFormat.line` and `LogFormat.cause`. The appender behind this backend routes each part of a format to a different place: text such as `LogFormat.line` builds the message, key-value parts such as `LogFormat.allAnnotations` and `LogFormat.spans` go into the MDC, and `LogFormat.cause` becomes the `Throwable`. See [Creating a Custom LogFormat](formatting-log-records.md#creating-a-custom-logformat) for how formats talk to an appender.

`SLF4J.slf4j(format, loggerName, causeToThrowable)` also takes two functions. The `loggerName` function turns the `Trace` into a logger name when no `logger_name` annotation is set; `SLF4J.getLoggerName(default)` builds the standard one, which falls back to `default` (`"zio-slf4j-logger"` when omitted) for traces it cannot parse. The `causeToThrowable` function turns a ZIO `Cause` into the `Throwable` that SLF4J receives; the default, `SLF4J.causeToThrowableDefault`, wraps a non-empty cause in a `zio.FiberFailure`, so the provider prints the full ZIO cause.

The following layer adds every log span to the MDC, names lines whose trace cannot be parsed `billing-service`, and passes the original exception to SLF4J instead of a `FiberFailure`, so that Logback shows the exception's own class and stack trace:

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

This backend applies no ZIO `LogFilter` of its own. For each line, it asks the SLF4J logger whether the level is enabled (`isInfoEnabled`, `isDebugEnabled` and so on, with the marker when one is set), so the provider's configuration, such as `logback.xml`, decides which levels are written. A ZIO `LogFilter` runs only if you add one, as in the previous example. ZIO levels map to SLF4J levels as follows:

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
            <Pattern>%d{HH:mm:ss.SSS} [%thread] [%mdc] %-5level %logger{36} %msg%n</Pattern>
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
12:16:21.951 [ZScheduler-Worker-8] [] INFO  zio.logging.example.Slf4jSimpleApp Start
12:16:22.024 [ZScheduler-Worker-12] [user=0e3bd69c-ee62-4096-82b2-593760d3fb19, trace_id=6e956bcf-d534-4c16-9402-fb6bca13c9ab] INFO  zio.logging.example.UserOperation Starting user operation
12:16:22.024 [ZScheduler-Worker-10] [user=869ed4c7-924d-4c02-ab5c-c30c1996a139, trace_id=6e956bcf-d534-4c16-9402-fb6bca13c9ab] INFO  zio.logging.example.UserOperation Starting user operation
12:16:22.592 [ZScheduler-Worker-14] [user=869ed4c7-924d-4c02-ab5c-c30c1996a139, trace_id=6e956bcf-d534-4c16-9402-fb6bca13c9ab] INFO  zio.logging.example.UserOperation Stopping user operation
12:16:22.592 [ZScheduler-Worker-1] [user=0e3bd69c-ee62-4096-82b2-593760d3fb19, trace_id=6e956bcf-d534-4c16-9402-fb6bca13c9ab] INFO  zio.logging.example.UserOperation Stopping user operation
12:16:22.598 [ZScheduler-Worker-14] [] INFO  zio.logging.example.Slf4jSimpleApp Done
```

### Custom Tracing Annotation

The following application has a custom aspect `currentTracingSpanAspect`, which takes the current span from a `Tracing` service and adds it to logs with a log annotation:

```scala
import zio.logging.backend.SLF4J
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZIOAspect, ZLayer, _ }

import java.util.UUID

trait Tracing {
  def getCurrentSpan(): UIO[String]
}

final class LiveTracing extends Tracing {
  override def getCurrentSpan(): UIO[String] = ZIO.succeed(UUID.randomUUID().toString)
}

object LiveTracing {
  val layer: ULayer[Tracing] = ZLayer.succeed(new LiveTracing)
}

object CustomTracingAnnotationApp extends ZIOAppDefault {

  private def currentTracingSpanAspect(key: String): ZIOAspect[Nothing, Tracing, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Tracing, Nothing, Any, Nothing, Any] {
      def apply[R <: Tracing, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        ZIO.serviceWithZIO[Tracing] { tracing =>
          tracing.getCurrentSpan().flatMap { span =>
            ZIO.logAnnotate(key, span)(zio)
          }
        }
    }

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val users = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- ZIO.foreachPar(users) { uId =>
        {
          ZIO.logInfo("Starting operation") *>
            ZIO.sleep(500.millis) *>
            ZIO.logInfo("Stopping operation")
        } @@ ZIOAspect.annotated("user", uId.toString)
      } @@ currentTracingSpanAspect("trace_id")
      _ <- ZIO.logInfo("Done")
    } yield ExitCode.success).provide(LiveTracing.layer)

}
```

Expected Console Output:
```
15:53:20.145 [ZScheduler-Worker-9] [user=1abd8458-aefd-4780-88ec-cccd1310d4c8, trace_id=71436dd4-22d5-4e06-aaa7-f3ff7b108037] INFO  z.l.e.CustomTracingAnnotationApp Starting operation
15:53:20.145 [ZScheduler-Worker-13] [user=878689e0-da30-49f8-8923-ed915c00db9c, trace_id=71436dd4-22d5-4e06-aaa7-f3ff7b108037] INFO  z.l.e.CustomTracingAnnotationApp Starting operation
15:53:20.688 [ZScheduler-Worker-15] [user=1abd8458-aefd-4780-88ec-cccd1310d4c8, trace_id=71436dd4-22d5-4e06-aaa7-f3ff7b108037] INFO  z.l.e.CustomTracingAnnotationApp Stopping operation
15:53:20.688 [ZScheduler-Worker-11] [user=878689e0-da30-49f8-8923-ed915c00db9c, trace_id=71436dd4-22d5-4e06-aaa7-f3ff7b108037] INFO  z.l.e.CustomTracingAnnotationApp Stopping operation
15:53:20.691 [ZScheduler-Worker-15] [] INFO  z.l.e.CustomTracingAnnotationApp Done
```

## Feature Changes

The following changes affect the logger name annotation of this backend.

### Version 2.2.0

The deprecated log annotation with key `slf4j_logger_name` was removed.
The common log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`) is now the only supported annotation for the logger name.
