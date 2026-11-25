# How to Test Log Output

> Some log lines are part of what your code promises: an alert fires on a warning, an operator searches for a trace id, an audit reads an error message. In this guide, we write ZIO Test specs that check those promises. We start with `ZTestLogger`, which records every log call of a test, and assert on the level, the message, the annotations and the logger name. Then we render a recorded entry with a `LogFormat` to check the formatted line, capture what a configured console logger really prints, and test a `LogFilter` on its own.

The sections are independent recipes:

- Read what was logged with `ZTestLogger`
- Assert on string annotations and the logger name
- Assert on typed annotations
- Check the formatted line
- Capture what the console logger prints
- Test a log filter directly

## The Problem

A log call returns `Unit`, so a normal test of the effect's result says nothing about it. A refactoring can drop a warning, log it at the wrong level, or lose its trace id, and every test still passes.

Without help from the test framework, you would install your own logger that collects the calls, and then read the collected values back:

```scala
import java.util.concurrent.atomic.AtomicReference
import zio._

val captured = new AtomicReference[List[String]](Nil)

val capturingLogger: ZLogger[String, Unit] = new ZLogger[String, Unit] {
  def apply(
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => String,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ): Unit = {
    captured.updateAndGet(lines => lines :+ s"${logLevel.label} ${message()}")
    ()
  }
}

val withCapture: ZLayer[Any, Nothing, Unit] = Runtime.addLogger(capturingLogger)
```

This keeps only the text you chose to keep, so a later test that needs the annotations or the cause has to change the logger. The `AtomicReference` is shared, so concurrent tests see each other's lines unless every test creates its own. ZIO Test already has a logger that records the complete call for each test, and ZIO Logging adds the pieces to read typed annotations and render lines from it.

## Prerequisites

Add ZIO Test to your build, at the same version as your ZIO dependency. Use `%%%` instead of `%%` for Scala.js and Scala Native projects:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-logging"  % "2.5.5",
  "dev.zio" %% "zio-test"     % "2.1.27" % Test,
  "dev.zio" %% "zio-test-sbt" % "2.1.27" % Test
)
```

The specs in this guide test a small order service. `Orders.processOrder` logs an `Info` line, a `Warning` line for orders over 500, and a `Debug` line. It sets its logger name with `loggerName` and adds an `order_id` annotation with ZIO's `ZIOAspect.annotated`:

```scala
import zio._
import zio.logging.loggerName

case class Order(id: String, userId: String, amount: BigDecimal)

object Orders {
  def processOrder(order: Order): UIO[Unit] =
    (for {
      _ <- ZIO.logInfo(s"Processing order ${order.id}")
      _ <- ZIO.logWarning(s"Large order ${order.id}: ${order.amount}").when(order.amount > 500)
      _ <- ZIO.logDebug(s"Order ${order.id} stored")
    } yield ()) @@ loggerName("shop.orders") @@ ZIOAspect.annotated("order_id", order.id)
}
```

The guide assumes you know how to write a `ZIOSpecDefault` spec. To learn what a logger name and an annotation are, see [Log Annotations](reference-log-annotations.md).

## Read What Was Logged With ZTestLogger

The ZIO Test runner adds a fresh `ZTestLogger` around every test. It records each log call as a `ZTestLogger.LogEntry`, and `ZTestLogger.logOutput` returns the entries of the current test as a `Chunk[LogEntry]`, in the order they were logged. You don't need to provide `ZTestLogger.default` yourself.

A `LogEntry` keeps every argument of the log call:

| Field         | Type                  | Holds                                                                                    |
| ------------- | --------------------- | ---------------------------------------------------------------------------------------- |
| `logLevel`    | `LogLevel`            | The level, such as `LogLevel.Warning`.                                                   |
| `message`     | `() => String`        | The message. Call `message()` to get the text.                                           |
| `cause`       | `Cause[Any]`          | The cause passed to `ZIO.logErrorCause` and similar calls, or `Cause.empty`.             |
| `annotations` | `Map[String, String]` | ZIO's string annotations, including the `logger_name` set by `loggerName`.               |
| `context`     | `FiberRefs`           | The fiber's references, where ZIO Logging keeps typed annotations in `logContext`.       |
| `spans`       | `List[LogSpan]`       | The active log spans.                                                                    |
| `trace`       | `Trace`               | The code location of the log call.                                                       |
| `fiberId`     | `FiberId`             | The fiber that logged.                                                                   |

The spec below checks the levels of all three calls and the text of the warning:

```scala
import zio.test._

object OrdersLogSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Orders logging")(
      test("a large order logs a warning") {
        for {
          _      <- Orders.processOrder(Order("o-1", "u-42", BigDecimal(750)))
          output <- ZTestLogger.logOutput
        } yield assertTrue(
          output.map(_.logLevel) == Chunk(LogLevel.Info, LogLevel.Warning, LogLevel.Debug),
          output(1).message() == "Large order o-1: 750"
        )
      }
    )
}
```

The `Debug` entry is in the output. No logger of the application is installed in this test, and even if one were, its level would not matter here.

:::caution
`ZTestLogger` records every log call, at every level. The level filters of ZIO Logging live inside each ZIO Logging logger, such as the one that `consoleLogger` installs, so they never reach `ZTestLogger`. A test on `ZTestLogger.logOutput` checks what your code logs, not what a configured logger prints. To check the printed output, see [Capture What the Console Logger Prints](#capture-what-the-console-logger-prints) and [Test a Log Filter Directly](#test-a-log-filter-directly).
:::

## Assert on String Annotations and the Logger Name

String annotations are the ones ZIO itself manages: `ZIO.logAnnotate`, `ZIOAspect.annotated`, and the `loggerName` aspect, which stores the name under the key `logger_name`. They are in `LogEntry#annotations`. The `zio.logging.loggerNameAnnotationKey` value holds the key, so the test does not repeat the string:

```scala
import zio.logging.loggerNameAnnotationKey
import zio.test._

object OrdersAnnotationsSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Orders annotations")(
      test("every line carries the logger name and the order id") {
        for {
          _      <- Orders.processOrder(Order("o-1", "u-42", BigDecimal(750)))
          output <- ZTestLogger.logOutput
        } yield assertTrue(
          output.forall(_.annotations.get(loggerNameAnnotationKey) == Some("shop.orders")),
          output.forall(_.annotations.get("order_id") == Some("o-1"))
        )
      }
    )
}
```

When code does not use `loggerName`, there is no `logger_name` annotation, and ZIO Logging takes the name from the code location instead. To check the name that a logger would print in both cases, ask the extractor that the formats and filters use: `LoggerNameExtractor.loggerNameAnnotationOrTrace(entry.trace, entry.context, entry.annotations)` returns the annotation if it is set, and otherwise the name of the class or object that contains the log call, taken from `trace`.

## Assert on Typed Annotations

Typed annotations, such as `LogAnnotation.TraceId` and `LogAnnotation.UserId`, are not in `LogEntry#annotations`. ZIO Logging stores them in a `LogContext`, kept in the fiber reference `zio.logging.logContext`. `entry.context.get(logContext)` returns an `Option[LogContext]`, which is `None` when no typed annotation was set. From a `LogContext`, `LogContext#get` with an annotation returns the typed value, and `LogContext#apply` returns it rendered as text:

```scala
import java.util.UUID
import zio.logging.{ LogAnnotation, LogContext, logContext }
import zio.test._

object OrdersTraceSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Orders tracing")(
      test("every line carries the trace id and the user id") {
        val traceId = UUID.fromString("3f2c9d1e-6b0a-4c55-9a4e-0d1f2a3b4c5d")
        for {
          _       <- Orders.processOrder(Order("o-1", "u-42", BigDecimal(750))) @@
                       LogAnnotation.TraceId(traceId) @@
                       LogAnnotation.UserId("u-42")
          output  <- ZTestLogger.logOutput
          contexts = output.map(_.context.get(logContext).getOrElse(LogContext.empty))
        } yield assertTrue(
          contexts.map(_.get(LogAnnotation.TraceId)) == Chunk.fill(3)(Some(traceId)),
          contexts.map(_.apply(LogAnnotation.UserId)) == Chunk.fill(3)(Some("u-42"))
        )
      }
    )
}
```

The typed check compares `UUID` values, so it does not depend on how the UUID is written as text. `LogContext#get` also accepts an annotation name, such as `get("trace_id")`, and `LogContext#asMap` returns every typed annotation as a `Map[String, String]` of rendered values. Both look up by name only.

## Check the Formatted Line

The checks so far look at the raw call. When the formatted line matters, for example because a log parser expects a field, render the entry with the same `LogFormat` that the application uses. `LogEntry#call` passes all fields of the entry to a `ZLogger`, and `LogFormat#toLogger` turns a format into a `ZLogger[String, String]` that returns the formatted text. `LogFormat#toJsonLogger` returns the JSON line instead:

```scala
import zio.logging.{ LogFormat, loggerNameAnnotationKey }
import zio.test._

object OrdersFormatSpec extends ZIOSpecDefault {

  private val textFormat: LogFormat =
    LogFormat.level |-| LogFormat.annotation(loggerNameAnnotationKey) |-| LogFormat.quoted(LogFormat.line)

  private val jsonFormat: LogFormat =
    LogFormat.label("level", LogFormat.level) + LogFormat.label("message", LogFormat.line)

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Orders format")(
      test("the warning renders as text and as JSON") {
        for {
          _      <- Orders.processOrder(Order("o-1", "u-42", BigDecimal(750)))
          output <- ZTestLogger.logOutput
          warning = output(1)
        } yield assertTrue(
          warning.call(textFormat.toLogger) == "WARN logger_name=shop.orders \"Large order o-1: 750\"",
          warning.call(jsonFormat.toJsonLogger) == """{"level":"WARN","message":"Large order o-1: 750"}"""
        )
      }
    )
}
```

A format that contains a timestamp, such as `LogFormat.default`, gives a different line on every run. For such a format, assert on a part of the line, for example `line.contains("message=\"Large order o-1: 750\"")`, or build a test format without the timestamp, as above.

## Capture What the Console Logger Prints

A rendered entry shows how a line would look, but not whether the logger's filter lets it through. To test both at once, build the same logger that `consoleLogger(config)` installs, but point it at a buffer instead of standard output. `consoleLogger(config)` is built from `makeConsoleLogger(config)`, which is `makeSystemOutLogger(config.format.toLogger).filter(config.toFilter)`. `zio.logging.makePrintStreamLogger` takes the `java.io.PrintStream` to write to, so we swap in a stream over a `ByteArrayOutputStream`. The `filter` and `install` methods on the resulting effect come from the `ZLoggerZIOLayerOps` syntax, which we import by name:

```scala
import java.io.{ ByteArrayOutputStream, PrintStream }
import java.nio.charset.StandardCharsets
import zio.logging.{ ConsoleLoggerConfig, LogFilter, LogFormat, ZLoggerZIOLayerOps, makePrintStreamLogger }
import zio.test._

object ConsoleOutputSpec extends ZIOSpecDefault {

  private val config: ConsoleLoggerConfig =
    ConsoleLoggerConfig(LogFormat.level |-| LogFormat.line, LogFilter.LogLevelByNameConfig(LogLevel.Info))

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("console output")(
      test("prints the INFO and WARN lines and hides the DEBUG line") {
        val buffer = new ByteArrayOutputStream()
        val stream = new PrintStream(buffer, true, StandardCharsets.UTF_8.name)

        val captureLogger: ZLayer[Any, Nothing, Unit] =
          makePrintStreamLogger(config.format.toLogger, stream).filter(config.toFilter).install

        for {
          _    <- Orders.processOrder(Order("o-1", "u-42", BigDecimal(750))).provideLayer(captureLogger)
          lines = buffer.toString(StandardCharsets.UTF_8.name).split(java.lang.System.lineSeparator()).toList
        } yield assertTrue(lines == List("INFO Processing order o-1", "WARN Large order o-1: 750"))
      }
    )
}
```

The code above builds the logger from the same `ConsoleLoggerConfig` type that the application passes to `consoleLogger`, installs it only for the effect under test with `provideLayer`, and then reads the buffer. The `Debug` line is missing from the buffer because the filter drops it, while `ZTestLogger.logOutput` in the same test would still contain it. For a JSON logger, use `config.format.toJsonLogger` in place of `config.format.toLogger`.

## Test a Log Filter Directly

When the level rules are the part you want to pin down, test the `LogFilter` without running any code that logs. `LogFilter#apply` takes the same eight values as a log call and returns `true` when the line passes. The logger-name filters read the name from the `logger_name` annotation first, so a test can pass the name in the annotations map and empty values everywhere else. This is how ZIO Logging's own tests check filters:

```scala
import zio.logging.{ LogFilter, loggerNameAnnotationKey }
import zio.test._

object LevelRulesSpec extends ZIOSpecDefault {

  private val filter: LogFilter[String] =
    LogFilter.LogLevelByNameConfig(LogLevel.Info, "shop.payments" -> LogLevel.Debug).toFilter

  private def passes(loggerName: String, level: LogLevel): Boolean =
    filter(
      Trace.empty,
      FiberId.None,
      level,
      () => "",
      Cause.empty,
      FiberRefs.empty,
      Nil,
      Map(loggerNameAnnotationKey -> loggerName)
    )

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("level rules")(
      test("DEBUG passes for payments and is dropped elsewhere") {
        assertTrue(
          passes("shop.payments", LogLevel.Debug),
          passes("shop.payments.cards", LogLevel.Debug),
          !passes("shop.orders", LogLevel.Debug),
          passes("shop.orders", LogLevel.Info)
        )
      }
    )
}
```

The same `LogFilter#apply` call works on a recorded entry. `filter(e.trace, e.fiberId, e.logLevel, e.message, e.cause, e.context, e.spans, e.annotations)` tells you whether a configured logger would print the entry `e` that `ZTestLogger` recorded.

## Going Further

- [Testing](testing.md) is the short overview of ZIO Test support in ZIO Logging.
- [Tutorial: Logging for a Small Service](tutorial-service-logging.md) builds the order service and ends with a test like the ones above.
- [Log Annotations](reference-log-annotations.md) describes `LogAnnotation`, `LogContext`, `logContext` and the `loggerName` aspects.
- [Logger Layers](reference-logger-layers.md) lists `makePrintStreamLogger`, the other `make*` constructors and the `install` syntax.
- [Log Format Reference](reference-log-format.md) and [Log Filter Reference](reference-log-filter.md) cover every `LogFormat` and `LogFilter` constructor.
