# Tutorial: Logging for a Small Service

> Welcome to the second ZIO Logging tutorial! In this tutorial, we add logging to a small order service, one step at a time, the way you would in a real application. This tutorial continues from the first one, [ZIO Logging Basics](tutorial.md). You should already know how to install a console logger in the `bootstrap` layer, how log levels work, how to attach an annotation with `@@`, and how to switch to `consoleJsonLogger`. If any of that is new, start with the first tutorial.

## Learning Objectives

By the end of this tutorial, you will understand:

- How to give each part of a service its own logger name, and why a name lets you set a level for that part only
- How to attach a trace id and a user id to every log line of one request
- How to move the log format and the levels out of the code into a HOCON config file
- How to send the same JSON log lines to the console and to a file, and how to count log lines with a metric
- How to write a test that checks what the service logged

We learn these concepts through the following steps:

1. Name the Components
2. Annotate Each Request
3. Move the Format and Levels to a Config File
4. Write JSON to a File as Well
5. Count Log Lines
6. Test the Log Output

We recommend reading from top to bottom. Each step adds one thing to the same service, and the service code from step 1 stays the same until the end.

## Background

A service handles many requests at once, and its log lines arrive mixed together. To make sense of them later, each line has to answer three questions: which part of the service wrote it, which request it belongs to, and how serious it is. ZIO Logging answers the first question with a **logger name**, the second with **log annotations** that you attach once per request, and the third with the log level. Once a line carries all three, you can filter it, search for it, ship it to a log store as JSON, count it, and assert on it in a test. The six steps below add these pieces in that order.

## 1. Name the Components

Our service has two components: `Orders`, which processes an order, and `Payments`, which charges for it. When a log line has no explicit name, ZIO Logging takes the name from the code location of the log call, which is the class or package. That name changes when you move or rename code. The `zio.logging.loggerName` aspect sets a stable name for every log call inside the effect it wraps.

We define the order model and the two components:

```scala
import zio._
import zio.logging.loggerName

case class Order(id: String, userId: String, amount: BigDecimal)

object Payments {
  def charge(order: Order): UIO[Unit] =
    (for {
      _ <- ZIO.logDebug(s"Charging ${order.amount} for order ${order.id}")
      _ <- ZIO.logInfo(s"Payment accepted for order ${order.id}")
    } yield ()) @@ loggerName("shop.payments")
}

object Orders {
  def processOrder(order: Order): UIO[Unit] =
    (for {
      _ <- ZIO.logInfo(s"Processing order ${order.id}")
      _ <- ZIO.logWarning(s"Large order ${order.id}: ${order.amount}").when(order.amount > 500)
      _ <- Payments.charge(order)
      _ <- ZIO.logInfo(s"Order ${order.id} completed")
    } yield ()) @@ loggerName("shop.orders")
}
```

The code above does the following:

- `Order` is a plain case class with an order id, a user id, and an amount.
- `Payments.charge` writes a `Debug` line and an `Info` line. The `@@ loggerName("shop.payments")` at the end names every log call inside the `for` block.
- `Orders.processOrder` writes an `Info` line, a `Warning` line when the amount is over 500, calls `Payments.charge`, and writes a final `Info` line. Its log calls are named `shop.orders`.
- `Payments.charge` runs inside `Orders.processOrder`, but its own `loggerName` replaces `shop.orders` while it runs. After it returns, the name is `shop.orders` again.

The `loggerName` aspect stores the name as a log annotation with the key `logger_name`. Next, we install a console logger that prints the annotations and sets a lower level for one name only:

```scala
import zio.logging.{ consoleLogger, ConsoleLoggerConfig, LogFilter, LogFormat }

object ShopApp extends ZIOAppDefault {

  private val config: ConsoleLoggerConfig =
    ConsoleLoggerConfig(
      LogFormat.default |-| LogFormat.allAnnotations(Set.empty[String]),
      LogFilter.LogLevelByNameConfig(LogLevel.Info, "shop.payments" -> LogLevel.Debug)
    )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger(config)

  override def run: ZIO[Scope, Any, ExitCode] =
    Orders.processOrder(Order("o-1", "u-42", BigDecimal(750))).as(ExitCode.success)
}
```

The code above does the following:

- `LogFormat.allAnnotations(Set.empty[String])` adds every annotation to the end of the line, so we can see the `logger_name` annotation.
- `LogFilter.LogLevelByNameConfig(LogLevel.Info, "shop.payments" -> LogLevel.Debug)` keeps `Info` as the root level, and lowers the level to `Debug` for `shop.payments` and every name below it.
- `run` processes one order of 750, which is large enough to trigger the warning.

When you run the program, you see five lines. The timestamp and the fiber change on every run, so this is example output:

```text
timestamp=<time> level=INFO thread=<fiber> message="Processing order o-1" logger_name=shop.orders
timestamp=<time> level=WARN thread=<fiber> message="Large order o-1: 750" logger_name=shop.orders
timestamp=<time> level=DEBUG thread=<fiber> message="Charging 750 for order o-1" logger_name=shop.payments
timestamp=<time> level=INFO thread=<fiber> message="Payment accepted for order o-1" logger_name=shop.payments
timestamp=<time> level=INFO thread=<fiber> message="Order o-1 completed" logger_name=shop.orders
```

Notice that the `DEBUG` line from `shop.payments` is printed, while the root level is still `Info`. Try adding a `ZIO.logDebug` call to `Orders.processOrder`: it stays hidden, because `shop.orders` uses the root level. A name is what lets you turn up the detail for one component while the rest of the service stays quiet.

## 2. Annotate Each Request

The lines from step 1 tell us which component wrote them, but not which request. When two orders run at the same time, their lines interleave. We fix this by attaching a trace id and a user id to every line of one request. A **trace id** is a unique id for one request, so all its lines can be found with one search.

ZIO Logging has two predefined typed annotations for this. `LogAnnotation.TraceId` holds a `java.util.UUID`, and `LogAnnotation.UserId` holds a `String`. They are typed because the value keeps its Scala type until a logger renders it as text. We wrap the call to `Orders.processOrder` in a request handler:

```scala
import zio.logging.LogAnnotation

def handleRequest(order: Order): UIO[Unit] =
  for {
    traceId <- Random.nextUUID
    _       <- Orders.processOrder(order) @@
                 LogAnnotation.TraceId(traceId) @@
                 LogAnnotation.UserId(order.userId)
  } yield ()
```

The code above does the following:

- `Random.nextUUID` creates a new UUID for this request. In a real HTTP service, you would usually read the trace id from a request header instead.
- `LogAnnotation.TraceId(traceId)` and `LogAnnotation.UserId(order.userId)` are aspects. With `@@`, they apply to every log call that runs inside `Orders.processOrder`, including the calls in `Payments.charge`.
- `Orders` and `Payments` do not change. The handler adds the request context from the outside.

To see the result, we change `run` in the step 1 program to call `handleRequest` instead of `Orders.processOrder`. The rest of the program stays the same:

```scala
import zio.logging.{ consoleLogger, ConsoleLoggerConfig, LogFilter, LogFormat }

object ShopApp extends ZIOAppDefault {

  private val config: ConsoleLoggerConfig =
    ConsoleLoggerConfig(
      LogFormat.default |-| LogFormat.allAnnotations(Set.empty[String]),
      LogFilter.LogLevelByNameConfig(LogLevel.Info, "shop.payments" -> LogLevel.Debug)
    )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger(config)

  override def run: ZIO[Scope, Any, ExitCode] =
    handleRequest(Order("o-1", "u-42", BigDecimal(750))).as(ExitCode.success)
}
```

Each line now ends with the logger name, the trace id, and the user id. `LogFormat.allAnnotations` writes the `logger_name` annotation first, followed by the typed annotations, whose order is not fixed. The first two lines look like this example output:

```text
timestamp=<time> level=INFO thread=<fiber> message="Processing order o-1" logger_name=shop.orders trace_id=<uuid> user_id=u-42
timestamp=<time> level=WARN thread=<fiber> message="Large order o-1: 750" logger_name=shop.orders trace_id=<uuid> user_id=u-42
```

All five lines of the request carry the same `trace_id`. A second request gets a new UUID, so the lines of the two requests can be told apart.

## 3. Move the Format and Levels to a Config File

So far, the format and the levels are Scala values. To change them, we have to rebuild the service. A config file lets operations staff change the levels without touching the code. We use HOCON, the config format of the Typesafe Config library, and the `zio-config-typesafe` module, which turns a HOCON file into a ZIO `ConfigProvider`. A `ConfigProvider` is the source that ZIO reads configuration values from.

We add the module to `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-config-typesafe" % "4.1.2"
```

Then we create the file `src/main/resources/application.conf`. The logger layers read their settings from the `logger` key:

```hocon
logger {
  format = "%label{timestamp}{%timestamp} %label{level}{%level} %label{logger}{%name} %label{message}{%message} %label{cause}{%cause} %kvs"
  filter {
    rootLevel = INFO
    mappings {
      "shop.payments" = DEBUG
    }
  }
}
```

The file does the following:

- `format` is a format pattern. Each `%label{key}{...}` becomes one JSON field named `key`. `%timestamp` is the time, `%level` the level, `%name` the logger name, `%message` the message, and `%cause` the error, if any.
- `%label{cause}{%cause}` is left out of a line that has no error, so successful lines stay short.
- `%kvs` writes every annotation as its own field, except `logger_name`, which `%name` already covers.
- `filter` holds the same levels as the `LogLevelByNameConfig` value from step 1. The mapping key is quoted because it contains a dot, which HOCON would otherwise read as nesting.

Now we load the file in the `bootstrap` layer and use `consoleJsonLogger()` with empty parentheses, which reads its configuration from the `logger` key:

```scala
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.consoleJsonLogger

object ShopApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>>
      Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath()) >>>
      consoleJsonLogger()

  override def run: ZIO[Scope, Any, ExitCode] =
    handleRequest(Order("o-1", "u-42", BigDecimal(750))).as(ExitCode.success)
}
```

The code above does the following:

- `TypesafeConfigProvider.fromResourcePath()` loads `application.conf` from the classpath.
- `Runtime.setConfigProvider(...)` makes it the config provider for the layers that come after it with `>>>`.
- `consoleJsonLogger()` reads `logger.format` and `logger.filter` from that provider and installs a console logger that prints JSON. If the file has an invalid value, the layer fails with a `Config.Error` when the application starts.

The program prints one JSON object per line. With the trace id shortened to `<uuid>`, the first two lines look like this example output:

```text
{"timestamp":"2026-11-17T10:15:02.104123+01:00","level":"INFO","logger":"shop.orders","message":"Processing order o-1","trace_id":"<uuid>","user_id":"u-42"}
{"timestamp":"2026-11-17T10:15:02.109481+01:00","level":"WARN","logger":"shop.orders","message":"Large order o-1: 750","trace_id":"<uuid>","user_id":"u-42"}
```

Try changing `"shop.payments" = DEBUG` to `INFO` in the file and run the program again. The `Charging 750 for order o-1` line disappears, and you did not change a line of Scala.

## 4. Write JSON to a File as Well

A container platform usually collects the console output, but you may also want a log file on disk, for example for a log shipper that reads files. The `fileJsonLogger` layer writes the same JSON lines to a file. It reads its configuration from the same `logger` key, and needs one more setting: the `path` of the file.

We add one line to the `logger` block in `application.conf`:

```hocon
logger {
  path = "file:///tmp/shop.log"
  format = "%label{timestamp}{%timestamp} %label{level}{%level} %label{logger}{%name} %label{message}{%message} %label{cause}{%cause} %kvs"
  filter {
    rootLevel = INFO
    mappings {
      "shop.payments" = DEBUG
    }
  }
}
```

The `path` is a `file:///` URI, not a plain file path. The console logger ignores the `path` key, so both loggers can share the format and the filter. Next, we install both loggers with `++`, which combines two layers into one:

```scala
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.{ consoleJsonLogger, fileJsonLogger }

object ShopApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>>
      Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath()) >>>
      (consoleJsonLogger() ++ fileJsonLogger())

  override def run: ZIO[Scope, Any, ExitCode] =
    handleRequest(Order("o-1", "u-42", BigDecimal(750))).as(ExitCode.success)
}
```

The only change is `(consoleJsonLogger() ++ fileJsonLogger())` in the bootstrap. Every log call now goes to both loggers. Run the program, then open `/tmp/shop.log`: it holds the same JSON lines that the console shows. The file logger appends to the file, so each run adds five more lines.

:::caution
The directory of the file must exist before the application starts. If it is missing, the `fileJsonLogger()` layer fails at startup with a `FileNotFoundException` defect. The file logger is available on the JVM and Scala Native, not on Scala.js.
:::

## 5. Count Log Lines

A sudden rise in warnings or errors is often the first sign of trouble. The `logMetrics` layer counts every log call in a ZIO counter metric named `zio_log_total`, with a `level` label that holds the level in lower case, such as `warn`. A metrics backend such as Prometheus can then chart the count and alert on it.

The `logMetrics` layer installs a third logger next to the two from step 4:

```text
                         ┌──> console JSON logger (logger.filter) ──> standard output
ZIO.logInfo(...),        │
ZIO.logWarning(...), ... ┼──> file JSON logger    (logger.filter) ──> /tmp/shop.log
                         │
                         └──> metric logger       (no filter)     ──> zio_log_total{level="..."}
```

We add `logMetrics` to the bootstrap and read the counter at the end of `run`:

```scala
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.{ consoleJsonLogger, fileJsonLogger, logMetrics }
import zio.metrics.Metric

object ShopApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>>
      Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath()) >>>
      (consoleJsonLogger() ++ fileJsonLogger() ++ logMetrics)

  private val warnings = Metric.counter("zio_log_total").tagged("level", "warn")

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _     <- handleRequest(Order("o-1", "u-42", BigDecimal(750)))
      _     <- handleRequest(Order("o-2", "u-7", BigDecimal(20)))
      state <- warnings.value
      _     <- Console.printLine(s"Warnings logged: ${state.count.toLong}")
    } yield ExitCode.success
}
```

The code above does the following:

- `logMetrics` installs a logger that adds one to `zio_log_total` for each log call. It writes nothing to the console or the file.
- `Metric.counter("zio_log_total").tagged("level", "warn")` refers to the same counter with the label `level` set to `warn`. ZIO identifies a metric by its name and labels, so this value reads the count that `logMetrics` updates.
- `warnings.value` returns the current state of the counter, and `state.count` is the number of calls as a `Double`.
- The first order is large and logs a warning. The second order is small and does not.

After the JSON lines of the two requests, the program prints this line:

```text
Warnings logged: 1
```

The metric logger has no filter, so it counts every log call, including calls that the console and file filters drop. To publish the counter to Prometheus, see [Log Metrics](metrics.md).

## 6. Test the Log Output

The warning for a large order is part of the service's behaviour: an alert depends on it, and the trace id is how an operator finds the rest of the request. We write a test so that a change to `Orders.processOrder` cannot drop the warning or its trace id without anyone noticing.

ZIO Test records every log call of a test in a test logger, `ZTestLogger`, and `ZTestLogger.logOutput` returns the recorded entries. We add ZIO Test to `build.sbt`, at the same version as your ZIO dependency:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-test"     % "2.1.27" % Test,
  "dev.zio" %% "zio-test-sbt" % "2.1.27" % Test
)
```

Then we write the spec in `src/test/scala`:

```scala
import zio.logging.{ LogAnnotation, logContext }
import zio.test._

object OrdersSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Orders")(
      test("a large order logs a warning with the trace id") {
        val order = Order("o-1", "u-42", BigDecimal(750))
        for {
          traceId <- Random.nextUUID
          _       <- Orders.processOrder(order) @@ LogAnnotation.TraceId(traceId)
          output  <- ZTestLogger.logOutput
          warnings = output.filter(_.logLevel == LogLevel.Warning)
          traceIds = warnings.map(_.context.get(logContext).flatMap(_.get(LogAnnotation.TraceId)))
        } yield assertTrue(
          warnings.map(_.message()) == Chunk("Large order o-1: 750"),
          traceIds == Chunk(Some(traceId))
        )
      }
    )
}
```

The code above does the following:

- `Orders.processOrder(order) @@ LogAnnotation.TraceId(traceId)` runs the service with a trace id that the test knows, in the same way that `handleRequest` does.
- `ZTestLogger.logOutput` returns a `Chunk` of `LogEntry` values, one per log call in this test. We don't install anything: ZIO Test adds a fresh `ZTestLogger` around every test.
- `output.filter(_.logLevel == LogLevel.Warning)` keeps only the warnings. `message()` is a function call, because the message is computed only when a logger asks for it.
- `context.get(logContext)` reads the typed annotations of the entry as a `LogContext`, and `get(LogAnnotation.TraceId)` returns the trace id as a `UUID`, not as text.
- `assertTrue` checks that there is exactly one warning, with the expected message and the trace id of this test.

Run the test with `sbt test`. It passes. Now try changing the limit in `Orders.processOrder` from `500` to `1000`: the test fails, because the order of 750 no longer logs a warning.

:::note
`ZTestLogger` records every log call, including `Debug` calls that the console filter would hide. The test checks what the service logs, not what a configured logger prints. [How to Test Log Output](how-to-test-log-output.md) shows how to check the printed line too.
:::

## What You've Learned

In this tutorial, you learned:

- How to give each component a stable logger name with `loggerName`, and set a lower level for one name only
- How to attach `LogAnnotation.TraceId` and `LogAnnotation.UserId` to every line of a request with `@@`
- How to read the format and the levels from `application.conf` with `TypesafeConfigProvider` and `consoleJsonLogger()`
- How to send the same JSON lines to a file with `fileJsonLogger()`, and count log calls with `logMetrics`
- How to check in a test that a warning was logged with the right trace id, using `ZTestLogger.logOutput`

You now have the logging setup of a small production service. The next step is to adapt each piece to your own project.

## Next Steps

- **Add a trace id from a request header:** Read [How to Add a Correlation ID to Every Log Line](how-to-correlation-id.md).
- **Write more log tests:** Read [How to Test Log Output](how-to-test-log-output.md) to assert on annotations, logger names and the formatted line.
- **Tune the levels:** Read [How to Set Log Levels Per Package From Config](how-to-log-levels-from-config.md), and [How to Change Log Levels at Runtime](how-to-change-levels-at-runtime.md) to change them without a restart.
- **Rotate the log file:** Read [How to Log to a File With Rotation](how-to-file-logging-rotation.md).
- **Shape the JSON fields:** Read [How to Log JSON](how-to-json-logs.md) and the [Log Format Reference](reference-log-format.md).
- **Look up the APIs:** Read the [Log Annotations](reference-log-annotations.md), [Logger Layers](reference-logger-layers.md), and [Configuration](reference-configuration.md) reference pages, and [Log Metrics](metrics.md) for publishing the counter.
