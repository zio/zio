# Testing

> Log lines are often part of what a program promises: an audit line for every payment, a warning when a retry gives up, a user id on every line of a request. ZIO Test records every log call a test makes, so a test can check what was logged as data, without reading the console. This page describes the parts involved: the recorded entries, the two kinds of annotations, rendering an entry with a format, testing a filter, and capturing the real console output. For a task-oriented walk-through, see [How to Test Log Output](how-to-test-log-output.md).

## Adding ZIO Test

The test logger is part of ZIO Test. Add `zio-test` and its sbt runner to the test scope of your build, with the same version as your ZIO dependency (`%%%` in a Scala.js or Scala Native project):

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-test"     % "2.1.27" % Test,
  "dev.zio" %% "zio-test-sbt" % "2.1.27" % Test
)
```

## Reading the Recorded Log Entries

ZIO Test installs a test logger, `zio.test.ZTestLogger`, around every test. It records each log call of the test, and `ZTestLogger.logOutput` returns the records so far, in the order they were made. No extra layer is needed: `ZTestLogger.logOutput` works in any test of a `ZIOSpecDefault`.

The test logger only adds itself to the loggers; it does not replace them. ZIO's default logger, and any logger the test installs, such as a ZIO Logging console logger, still run. Provide `Runtime.removeDefaultLoggers` to the test if you want to keep ZIO's default logger from printing to the console. The `ZTestLogger.default` layer adds one more test logger; a test that uses the logger ZIO Test installs does not need it.

`ZTestLogger.logOutput` returns a `UIO[Chunk[ZTestLogger.LogEntry]]`. Each `LogEntry` holds the same arguments that every `ZLogger` receives:

| Field         | Type                  | Content                                                                                                         |
| ------------- | --------------------- | --------------------------------------------------------------------------------------------------------------- |
| `trace`       | `Trace`               | The source location of the log call. ZIO Logging derives the default logger name from it.                       |
| `fiberId`     | `FiberId`             | The fiber that logged.                                                                                          |
| `logLevel`    | `LogLevel`            | The level of the call, such as `LogLevel.Info`.                                                                 |
| `message`     | `() => String`        | The message. It is a function, so call it: `entry.message()`.                                                   |
| `cause`       | `Cause[Any]`          | The cause passed to the log call, or `Cause.empty`.                                                             |
| `context`     | `FiberRefs`           | The fiber refs of the logging fiber, including ZIO Logging's `logContext` with the typed log annotations.       |
| `spans`       | `List[LogSpan]`       | The active log spans.                                                                                           |
| `annotations` | `Map[String, String]` | ZIO's string annotations, set with `ZIO.logAnnotate`, `ZIOAspect.annotated` or ZIO Logging's `loggerName`.       |

`LogEntry#call(logger)` passes the entry's fields to any `ZLogger[String, A]` and returns its result, which is how an entry is [rendered with a log format](#rendering-an-entry-with-a-log-format).

The following spec runs an operation for two users and checks the message, the level, the trace id and the user annotation of each entry. Each `ZIO.logInfo` call produces one entry, `ZIOAspect.annotated` adds a string annotation, and `LogAnnotation.TraceId` sets a typed trace id for the wrapped effect:

```scala
import zio.logging.{ LogAnnotation, logContext }
import zio.test.Assertion._
import zio.test._
import zio.{ Chunk, LogLevel, Runtime, ZIO, ZIOAspect, _ }

import java.util.UUID

object LoggingSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("LoggingSpec")(
    test("start stop log output") {
      val users = Chunk.fill(2)(UUID.randomUUID())
      for {
        traceId      <- ZIO.succeed(UUID.randomUUID())
        _            <- ZIO.foreach(users) { uId =>
                          {
                            ZIO.logInfo("Starting operation") *> ZIO.sleep(500.millis) *> ZIO.logInfo("Stopping operation")
                          } @@ ZIOAspect.annotated("user", uId.toString)
                        } @@ LogAnnotation.TraceId(traceId)
        _            <- ZIO.logInfo("Done")
        loggerOutput <- ZTestLogger.logOutput
      } yield assertTrue(loggerOutput.size == 5) && assertTrue(
        loggerOutput.forall(_.logLevel == LogLevel.Info)
      ) && assert(loggerOutput.map(_.message()))(
        equalTo(
          Chunk(
            "Starting operation",
            "Stopping operation",
            "Starting operation",
            "Stopping operation",
            "Done"
          )
        )
      ) && assert(loggerOutput.map(_.context.get(logContext).flatMap(_.asMap.get(LogAnnotation.TraceId.name))))(
        equalTo(
          Chunk.fill(4)(Some(traceId.toString)) :+ None
        )
      ) && assert(loggerOutput.map(_.annotations.get("user")))(
        equalTo(users.flatMap(u => Chunk.fill(2)(Some(u.toString))) :+ None)
      )
    }
  ).provideLayer(Runtime.removeDefaultLoggers) @@ TestAspect.withLiveClock
}
```

A version of this spec is in the [example project](https://github.com/zio/zio-logging/tree/master/examples/core/shared/src/test/scala/zio/logging/example). It also provides `ZTestLogger.default`, which works but is not needed.

## String Annotations and Typed Log Annotations

A log entry carries annotations in two places, and a test has to look in the right one. ZIO's own string annotations, set with `ZIO.logAnnotate` or `ZIOAspect.annotated`, are in `entry.annotations`, a `Map[String, String]`. ZIO Logging's `loggerName` aspect also sets one of these, under the key `logger_name`.

ZIO Logging's typed annotations, the `LogAnnotation` values such as `LogAnnotation.UserId` and `LogAnnotation.TraceId`, are not in that map. They are stored in the `logContext` fiber ref, which the entry keeps in `entry.context`. `entry.context.get(logContext)` returns an `Option[LogContext]`; `LogContext#get(annotation)` returns the typed value, and `LogContext#asMap` returns every typed annotation rendered as a string. The [Log Annotations and LogContext Reference](reference-log-annotations.md) describes both kinds.

The following test sets one annotation of each kind and reads each one from its place:

```scala
import zio._
import zio.logging.{ LogAnnotation, logContext }
import zio.test._

object AnnotationsSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    test("reads a string annotation and a typed annotation") {
      for {
        _       <- ZIO.logInfo("Payment accepted") @@
                     ZIOAspect.annotated("request_id", "req-7") @@
                     LogAnnotation.UserId("alice")
        entries <- ZTestLogger.logOutput
        entry    = entries.head
      } yield assertTrue(
        entry.annotations.get("request_id") == Some("req-7"),
        entry.annotations.get("user_id") == None,
        entry.context.get(logContext).flatMap(_.get(LogAnnotation.UserId)) == Some("alice")
      )
    }
}
```

The typed `user_id` annotation does not appear in `entry.annotations`, even though a format such as `LogFormat.allAnnotations` prints both kinds side by side.

## Rendering an Entry With a Log Format

Checking the fields of an entry tells you what was logged, but not what the line looks like. To test the line your application writes, render the entry with the same `LogFormat`: `LogFormat#toLogger` turns the format into a `ZLogger[String, String]`, and `LogEntry#call` runs it on the entry. This tests the format without a console or a file. See [Formatting Log Records](formatting-log-records.md) for the formats themselves:

```scala
import zio._
import zio.logging.LogFormat
import zio.test._

object CheckoutFormatSpec extends ZIOSpecDefault {

  val checkoutFormat: LogFormat =
    LogFormat.level |-| LogFormat.line |-| LogFormat.annotation("user")

  override def spec: Spec[TestEnvironment with Scope, Any] =
    test("the checkout line shows the level, the message and the user") {
      for {
        _       <- ZIO.logInfo("Payment accepted") @@ ZIOAspect.annotated("user", "alice")
        entries <- ZTestLogger.logOutput
      } yield assertTrue(entries.map(_.call(checkoutFormat.toLogger)) == Chunk("INFO Payment accepted user=alice"))
    }
}
```

Formats that write the time or the fiber id, such as `LogFormat.default`, produce a different line on each run. Compare such lines with a pattern, or test a format without those parts.

## Testing a Log Filter

The test logger records every log call, whatever the level. ZIO Logging filters, such as the `filter` settings of a console logger, belong to the logger they are attached to, and the test logger has none. A debug line that your console logger drops is therefore still in `ZTestLogger.logOutput`.

To test which lines a `LogFilter` keeps, apply the filter to the recorded entries. `LogFilter#apply` takes the same eight arguments as a logger and returns `true` when the line passes. The following spec checks a filter that keeps only warnings and errors from the database code:

```scala
import zio._
import zio.logging.{ LogFilter, loggerName }
import zio.test._

object DbLogFilterSpec extends ZIOSpecDefault {

  val dbFilter: LogFilter[String] =
    LogFilter.logLevelByName(LogLevel.Info, "com.example.db" -> LogLevel.Warning)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    test("the db logger keeps only warnings and errors") {
      for {
        _       <- ZIO.logInfo("Query took 3 ms") @@ loggerName("com.example.db")
        _       <- ZIO.logWarning("Query took 2 s") @@ loggerName("com.example.db")
        _       <- ZIO.logInfo("Order placed") @@ loggerName("com.example.orders")
        entries <- ZTestLogger.logOutput
        kept     = entries.filter { e =>
                     dbFilter(e.trace, e.fiberId, e.logLevel, e.message, e.cause, e.context, e.spans, e.annotations)
                   }
      } yield assertTrue(
        entries.size == 3,
        kept.map(_.message()) == Chunk("Query took 2 s", "Order placed")
      )
    }
}
```

The `loggerName` aspect fixes the logger name, so the test does not depend on the file and package the test lives in. Without it, the filter would use the name derived from each entry's `trace`. The filter rules are described in [Log Filter](log-filter.md).

## Capturing Console Output

Sometimes the test must see exactly what the console logger prints, filter and format together. The console layers write to `System.out` or `System.err`, which a test cannot read easily. `zio.logging.makePrintStreamLogger(logger, stream)` builds the same kind of logger but writes to the `java.io.PrintStream` you pass. `makeConsoleLogger(config)` is `makePrintStreamLogger(config.format.toLogger, System.out)` with the config's filter added, so the following layer behaves like `consoleLogger` but writes into a buffer:

```scala
import zio._
import zio.logging.{ ConsoleLoggerConfig, ZLoggerZIOLayerOps, makePrintStreamLogger }
import zio.test._

import java.io.{ ByteArrayOutputStream, PrintStream }

object ConsoleOutputSpec extends ZIOSpecDefault {

  def capturingConsoleLogger(stream: PrintStream): ZLayer[Any, Config.Error, Unit] =
    ConsoleLoggerConfig
      .load()
      .flatMap(config => makePrintStreamLogger(config.format.toLogger, stream).filter(config.toFilter))
      .install

  val testConfig: ConfigProvider =
    ConfigProvider.fromMap(
      Map(
        "logger/format"           -> "%level %message",
        "logger/filter/rootLevel" -> "INFO"
      ),
      "/"
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    test("the console logger drops debug lines and prints the rest") {
      val buffer = new ByteArrayOutputStream()
      val stream = new PrintStream(buffer, true, "UTF-8")

      for {
        _     <- (ZIO.logDebug("Cache miss") *> ZIO.logInfo("Order placed"))
                   .provideLayer(Runtime.setConfigProvider(testConfig) >>> capturingConsoleLogger(stream))
        lines <- ZIO.succeed(buffer.toString("UTF-8").linesIterator.toList)
      } yield assertTrue(lines == List("INFO Order placed"))
    }
}
```

The `filter` and `install` syntax on the `ZIO` value comes from `ZLoggerZIOLayerOps`, which the test imports; see [Logger Layers and Constructors Reference](reference-logger-layers.md).

The test also shows how to drive a logger from configuration. `ConfigProvider.fromMap` holds the keys the test needs, with `/` as the path separator, and `Runtime.setConfigProvider` makes them the source that `ConsoleLoggerConfig.load` reads. The keys are listed in [Configuration Keys Reference](reference-configuration.md). For a test that changes a value while it runs, such as a test of a [reconfigurable logger](reconfigurable-logger.md), set the values with `TestSystem.putProperty` and read them with `ConfigProvider.fromProps("/")`. That provider reads the properties through ZIO's `System` service, which ZIO Test replaces with `TestSystem`, and it reads them again on every load, so each reload sees the latest values.
