# SLF4J v1

> The Simple Logging Facade for Java ([`SLF4J v1`](https://www.slf4j.org/) - working with JDK8) serves as a simple facade or abstraction for various logging frameworks (e.g. java.util.logging, logback, log4j).

The Simple Logging Facade for Java ([`SLF4J v1`](https://www.slf4j.org/) - working with JDK8) serves as a simple facade or abstraction for various logging frameworks (e.g. java.util.logging, logback, log4j).

In order to use this logging backend, we need to add the following line in our build.sbt file:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j" % "2.5.3"
```

>**_NOTE:_** SLF4J v1 implementation is similar to [v2](slf4j2.md), 
however there are some differences, v1 using [MDC context](https://logback.qos.ch/manual/mdc.html), working with JDK8, 
v2 using [key-value pairs](https://www.slf4j.org/manual.html#fluent), working with JDK9+. 
It is recommended to use v2, as it is the latest version 
and also there may be MDC (using [ThreadLocal](https://docs.oracle.com/javase/8/docs/api/java/lang/ThreadLocal.html)) issues,
where in the case of parallel executions it can happen that MDC will be reset by different Fiber before the message is logged.

Logger layer:

```scala

val logger = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
```

Default `SLF4J` logger setup:
* logger name (by default)  is extracted from `zio.Trace`
    * for example, trace `zio.logging.example.Slf4jSimpleApp.run(Slf4jSimpleApp.scala:17)` will have `zio.logging.example.Slf4jSimpleApp` as logger name
    * NOTE: custom logger name may be set by `zio.logging.loggerName` aspect
* all annotations (logger name and log marker name annotations are excluded) are placed into [MDC context](https://logback.qos.ch/manual/mdc.html)
* cause is logged as throwable

See also [LogFormat and LogAppender](formatting-log-records.md#logformat-and-logappender)

Custom logger name set by aspect:

```scala
ZIO.logInfo("Starting user operation") @@ zio.logging.loggerName("zio.logging.example.UserOperation")
```

Log marker name set by aspect:

```scala
ZIO.logInfo("Confidential user operation") @@ SLF4J.logMarkerName("CONFIDENTIAL")
```

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples)

### SLF4J logger name and annotations

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

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

Logback configuration:

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

### Custom tracing annotation

Following application has custom aspect `currentTracingSpanAspect` implementation which taking current span from `Tracing` service 
which then is added to logs by log annotation.

```scala
package zio.logging.example

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

## Feature changes

### Version 2.2.0

Deprecated log annotation with key `slf4j_logger_name` (`SLF4J.loggerNameAnnotationKey`) removed,
only common log annotation with key `logger_name` (`zio.logging.loggerNameAnnotationKey`) for logger name is supported now.
