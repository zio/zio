# SLF4J v2

> The Simple Logging Facade for Java ([`SLF4J v2`](https://www.slf4j.org/) - using JDK9+ module system [JPMS](http://openjdk.java.net/projects/jigsaw/spec/)) serves as a simple facade or abstraction for various logging frameworks (e.g. java.util.logging, logback, log4j).

The Simple Logging Facade for Java ([`SLF4J v2`](https://www.slf4j.org/) - using JDK9+ module system [JPMS](http://openjdk.java.net/projects/jigsaw/spec/)) serves as a simple facade or abstraction for various logging frameworks (e.g. java.util.logging, logback, log4j).

In order to use this logging backend, we need to add the following line in our build.sbt file:

```scala
libraryDependencies += "dev.zio" %% "zio-logging-slf4j2" % "2.5.3"
```

>**_NOTE:_** SLF4J v2 implementation is similar to [v1](slf4j1.md),
however there are some differences, v1 using [MDC context](https://logback.qos.ch/manual/mdc.html), working with JDK8,
v2 using [key-value pairs](https://www.slf4j.org/manual.html#fluent), working with JDK9+.

Logger layer:

```scala

val logger = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
```

Default `SLF4J` logger setup:
* logger name (by default)  is extracted from `zio.Trace`
    * for example, trace `zio.logging.example.Slf4jSimpleApp.run(Slf4jSimpleApp.scala:17)` will have `zio.logging.example.Slf4jSimpleApp` as logger name
    * NOTE: custom logger name may be set by `zio.logging.loggerName` aspect
* all annotations (logger name and log marker name annotations are excluded) are passed like [key-value pairs](https://www.slf4j.org/manual.html#fluent)
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
12:13:17.495 [ZScheduler-Worker-8] [] INFO  zio.logging.example.Slf4j2SimpleApp Start
12:13:17.601 [ZScheduler-Worker-11] [trace_id="7826dd28-7e37-4b54-b84d-05399bb6cbeb" user="7b6a1af5-bad2-4846-aa49-138d31b40a24"] INFO  zio.logging.example.UserOperation Starting user operation
12:13:17.601 [ZScheduler-Worker-10] [trace_id="7826dd28-7e37-4b54-b84d-05399bb6cbeb" user="4df9cdbc-e770-4bc9-b884-756e671a6435"] INFO  zio.logging.example.UserOperation Starting user operation
12:13:18.167 [ZScheduler-Worker-13] [trace_id="7826dd28-7e37-4b54-b84d-05399bb6cbeb" user="7b6a1af5-bad2-4846-aa49-138d31b40a24"] INFO  zio.logging.example.UserOperation Stopping user operation
12:13:18.167 [ZScheduler-Worker-15] [trace_id="7826dd28-7e37-4b54-b84d-05399bb6cbeb" user="4df9cdbc-e770-4bc9-b884-756e671a6435"] INFO  zio.logging.example.UserOperation Stopping user operation
12:13:18.173 [ZScheduler-Worker-13] [] INFO  zio.logging.example.Slf4j2SimpleApp Done
```
