# How to Use ZIO Logging With Logback or Log4j 2

> In this guide we send the logs of a ZIO application to Logback or Log4j 2, so that ZIO log lines go through the same appenders, patterns and level settings as the rest of a JVM service. We do it with the SLF4J backend of ZIO Logging: SLF4J is the common Java logging facade, and both Logback and Log4j 2 can serve as its provider. By the end, `ZIO.logInfo` calls appear in your `logback.xml` or `log4j2.xml` output with their annotations, logger names and markers.

The guide covers these steps:

1. Pick the SLF4J module that matches your logging backend.
2. Add the dependencies.
3. Replace the ZIO default logger with the SLF4J logger.
4. Print annotations in `logback.xml` or `log4j2.xml`.
5. Set logger names and markers from ZIO code.
6. Control levels in the backend configuration.

## The Problem

A service that already runs on Logback or Log4j 2 has its log setup in one place: an XML file that defines appenders (console, file, a log shipper), the line pattern, and the level per package. ZIO's default logger ignores that file. It writes its own format to standard output, so a ZIO service ends up with two formats in one stream, with levels that the XML file cannot change, and with ZIO lines that the file or network appenders never see.

ZIO Logging solves this with the `SLF4J` backend: a ZIO logger that hands every record to SLF4J, which passes it to Logback or Log4j 2. Annotations become MDC entries or SLF4J key-value pairs, so the XML pattern can print them.

## Pick the SLF4J Module

ZIO Logging ships two SLF4J backends with the same API: `zio-logging-slf4j2` for the SLF4J 2.x API and `zio-logging-slf4j` for the SLF4J 1.7 API. Pick the one that matches the SLF4J version your logging backend is built for:

| Logging backend                | SLF4J API | ZIO Logging module   | Provider artifacts                                            |
| ------------------------------ | --------- | -------------------- | ------------------------------------------------------------- |
| Logback 1.3, 1.4, 1.5 or later | 2.x       | `zio-logging-slf4j2` | `ch.qos.logback:logback-classic`                              |
| Log4j 2                        | 2.x       | `zio-logging-slf4j2` | `org.apache.logging.log4j:log4j-slf4j2-impl` and `log4j-core` |
| Logback 1.2                    | 1.7       | `zio-logging-slf4j`  | `ch.qos.logback:logback-classic`                              |

Mixing the versions fails silently. SLF4J 2.x does not load the binding that Logback 1.2 provides, so Logback 1.2 with `zio-logging-slf4j2` falls back to SLF4J's no-operation logger and prints nothing but a warning at startup. Prefer the SLF4J 2.x setup unless you are tied to Logback 1.2.

## Add the Dependencies

Each setup needs the ZIO Logging module and the provider. The versions below are the ones the ZIO Logging examples build with; any release of the same line works.

For Logback 1.3 or later:

```scala
libraryDependencies ++= Seq(
  "dev.zio"        %% "zio-logging-slf4j2" % "2.5.5",
  "ch.qos.logback"  % "logback-classic"    % "1.6.7"
)
```

For Log4j 2, the `log4j-slf4j2-impl` adapter is the SLF4J 2 provider and `log4j-core` does the logging:

```scala
libraryDependencies ++= Seq(
  "dev.zio"                 %% "zio-logging-slf4j2" % "2.5.5",
  "org.apache.logging.log4j" % "log4j-slf4j2-impl"  % "2.26.1",
  "org.apache.logging.log4j" % "log4j-core"         % "2.26.1"
)
```

For Logback 1.2:

```scala
libraryDependencies ++= Seq(
  "dev.zio"        %% "zio-logging-slf4j" % "2.5.5",
  "ch.qos.logback"  % "logback-classic"   % "1.2.13"
)
```

All ZIO Logging integration modules require Java 17 or later.

## Replace the ZIO Default Logger

We install the backend in the application's `bootstrap` layer. `Runtime.removeDefaultLoggers` removes ZIO's built-in console logger, and `SLF4J.slf4j` adds the SLF4J logger in its place. Without the first part, ZIO's own console logger prints the same records a second time, in its own format. The following order-handling program also adds an annotation, a `LogAnnotation` and a logger name, which the next sections print:

```scala
import zio._
import zio.logging.LogAnnotation
import zio.logging.backend.SLF4J

object OrderApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private def placeOrder(orderId: String): UIO[Unit] =
    (ZIO.logInfo("Order received") *> ZIO.logDebug("Reserving stock")) @@
      ZIOAspect.annotated("order_id", orderId)

  override def run: ZIO[Any, Nothing, Unit] =
    placeOrder("A-17") @@ LogAnnotation.UserId("alice") @@ zio.logging.loggerName("com.example.orders")
}
```

Install `SLF4J.slf4j` once. Each extra copy of the layer adds another logger, and each record is written once per copy.

With `SLF4J.logFormatDefault`, the SLF4J message is the log message itself, the cause becomes the logged exception, and every annotation becomes structured data next to the message. Both kinds of annotation take part: the string annotations set with `ZIOAspect.annotated` or `ZIO.logAnnotate`, and typed `LogAnnotation` values such as `LogAnnotation.UserId`. For the full list of what reaches the provider, see [Backends and Bridges Reference](reference-integrations.md#slf4j-backend-kept-and-dropped).

## Print Annotations With Logback 1.3 or Later

The SLF4J 2.x backend passes annotations as SLF4J key-value pairs. Logback 1.3 and later print them with the `%kvp` conversion word. This `logback.xml` comes from the `slf4j2-logback` example in the ZIO Logging repository; put it in `src/main/resources`:

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

`%kvp` writes each pair as `key="value"`, separated by spaces, in no fixed order. A line from `OrderApp` therefore has this shape (time, thread name and pair order vary):

```
10:41:07.215 [ZScheduler-Worker-3] [order_id="A-17" user_id="alice"] INFO  com.example.orders Order received
```

## Print Annotations With Logback 1.2

The SLF4J 1.7 API has no key-value pairs, so the `zio-logging-slf4j` backend puts annotations into the MDC (Mapped Diagnostic Context, SLF4J's per-thread map of extra values). Logback prints the whole MDC with `%mdc` and one entry with `%X{key}`. This `logback.xml` comes from the `slf4j-logback` example:

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

To print only selected annotations, name them in the pattern, for example `[%X{user_id}] [%X{order_id}]`.

The backend sets the MDC, logs and restores the previous MDC in one step on the same thread, so fibers running in parallel do not see each other's values. While a record with annotations is logged, the MDC holds only that record's annotations; values that Java code put into the MDC are not printed with it.

## Print Annotations With Log4j 2

With Log4j 2, the `log4j-slf4j2-impl` adapter puts SLF4J key-value pairs into Log4j's thread context map for the duration of the log call, so the pattern prints them with `%X` (the whole map) or `%X{key}` (one entry). This `log4j2.xml` comes from the `slf4j2-log4j` example; put it in `src/main/resources`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="INFO">
    <Appenders>
        <Console name="console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %X %-5level %logger{36} - %msg%n"/>
            <MarkerFilter marker="CONFIDENTIAL" onMatch="DENY" onMismatch="ACCEPT"/>
        </Console>
    </Appenders>
    <Loggers>
        <!-- avoid duplicated logs with additivity=false -->
        <Logger name="zio" level="debug" additivity="false">
            <AppenderRef ref="console"/>
        </Logger>
        <Root level="error">
            <AppenderRef ref="console"/>
        </Root>
    </Loggers>
</Configuration>
```

This configuration writes loggers under the `zio` prefix from `DEBUG` up, and everything else only from `ERROR` up. For `OrderApp`, whose logger name is `com.example.orders`, change `name="zio"` to `name="com.example"`, or the `INFO` and `DEBUG` lines are not written.

## Set Logger Names and Markers

By default the backend names the SLF4J logger after the class that made the log call: a call in `com.example.OrderService` logs to the logger `com.example.OrderService`, and a call whose location is unknown logs to `zio-slf4j-logger`. The `zio.logging.loggerName` aspect overrides the name for every log call inside an effect, which lets the XML file address a whole feature by one name:

```scala
import zio._

val payment: UIO[Unit] =
  (ZIO.logInfo("Charging card") *> ZIO.logInfo("Card charged")) @@ zio.logging.loggerName("com.example.payment")
```

SLF4J markers tag single records so the backend configuration can route or drop them. `SLF4J.logMarkerName` attaches a marker by name. Both example configurations above drop records with the `CONFIDENTIAL` marker, Logback with a `MarkerFilter` turbo filter and Log4j 2 with a `MarkerFilter` on the appender:

```scala
import zio._
import zio.logging.backend.SLF4J

val confidential: UIO[Unit] =
  ZIO.logInfo("Card number: 4111 1111 1111 1111") @@ SLF4J.logMarkerName("CONFIDENTIAL")
```

The logger name and the marker are taken out of the annotations, so they do not appear in `%kvp`, `%mdc` or `%X`.

## Control Levels in the Backend Configuration

With the SLF4J backend, Logback or Log4j 2 decides which levels are written. Before it formats a record, the backend asks the SLF4J logger whether the level is enabled, so the `level` attributes in `logback.xml` or `log4j2.xml` apply to ZIO logs exactly as they do to Java logs. ZIO's `Fatal` level becomes `ERROR`, `All` becomes `TRACE`, and records at `LogLevel.None` are not logged.

To quiet one package in Logback while keeping `DEBUG` for the rest, add a `logger` element to the `logback.xml` from above:

```xml
<logger name="com.example.payment" level="warn" />
```

The ZIO `LogFilter` settings and the `logger.filter` config keys do not apply to the SLF4J backend; they configure ZIO's own console and file loggers and the bridges. If you also want a ZIO-side filter in front of the provider, wrap the logger from `SLF4J.slf4jLogger` with `LogFilter#filter`, as shown in [Backends and Bridges Reference](reference-integrations.md#slf4j-level-mapping).

## Keep the SLF4J Bridge Out of This Setup

`zio-logging-slf4j2-bridge` and `zio-logging-slf4j-bridge` work in the opposite direction: they make ZIO Logging the SLF4J provider, so Java libraries log into ZIO loggers. Do not add a bridge to a setup that uses Logback or Log4j 2 through the SLF4J backend.

:::caution
A bridge next to `logback-classic` or `log4j-slf4j2-impl` means two SLF4J providers on the classpath. SLF4J then uses only one of them, and if it picks the real provider, the bridge layer fails with a `ClassCastException` when it is built. If it picks the bridge, `SLF4J.slf4j` sends each ZIO record into SLF4J and the bridge sends it back to ZIO, in a loop. With the SLF4J backend, Java libraries that log through SLF4J already reach Logback or Log4j 2 directly, so no bridge is needed.
:::

## Going Further

- [Backends and Bridges Reference](reference-integrations.md) lists every overload of `SLF4J.slf4j`, the full level mapping, and what the backend keeps and drops.
- [SLF4J v2](slf4j2.md) and [SLF4J v1](slf4j1.md) show complete example applications with their console output.
- [How to Send Java Library Logs Through ZIO Logging](how-to-bridge-java-logs.md) covers the opposite setup, where ZIO's own loggers write everything and the bridges collect Java logs.
- [Log Annotations and LogContext Reference](reference-log-annotations.md) describes the annotation types that become MDC entries or key-value pairs.
