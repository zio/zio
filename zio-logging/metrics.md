# Log Metrics

> Log metrics count how many log lines an application writes, per log level. A sudden rise in `error` lines, or `warn` lines that never stop, is often the first sign that something is wrong, and a counter is cheap to scrape and to alert on, unlike the log text itself.

ZIO supports several loggers at the same time, so the counting is done by its own `ZLogger`, a `zio.logging.MetricLogger`. It writes nothing; for every `ZIO.log*` call it increments a counter. Because it is a separate logger, it works next to any other logger, such as the [console logger](console-logger.md) or the [file logger](file-logger.md).

## Installing the Log Metrics Logger

The `zio.logging.logMetrics` layer installs a `MetricLogger` with the default counter. Add it next to your output logger, usually in the `bootstrap` layer:

```scala
import zio.logging.{ consoleLogger, logMetrics }
import zio.{ Runtime, ZIOAppArgs, ZLayer }

val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
  Runtime.removeDefaultLoggers >>> (consoleLogger() ++ logMetrics)
```

## The `zio_log_total` Counter

`logMetrics` counts into a ZIO counter metric (`Metric.Counter[Long]`) named `zio_log_total`. Each increment carries the label `level`, whose value is the log level of the call in lower case:

| Log level          | `level` label value |
| ------------------ | ------------------- |
| `LogLevel.All`     | `all`               |
| `LogLevel.Fatal`   | `fatal`             |
| `LogLevel.Error`   | `error`             |
| `LogLevel.Warning` | `warn`              |
| `LogLevel.Info`    | `info`              |
| `LogLevel.Debug`   | `debug`             |
| `LogLevel.Trace`   | `trace`             |
| `LogLevel.None`    | `off`               |

The counter counts every log call, including the calls that the output logger drops. The level filter of a console or file logger belongs to that logger only, so a `ZIO.logDebug` call increments `zio_log_total{level="debug"}` even when the console logger is set to `INFO` and prints nothing. To count only some calls, add a filter to the metric logger itself, as shown in [Custom Counters](#custom-counters).

Every metric tag of the logging fiber also becomes a label of the count. `MetricLogger` reads the fiber's current metric tags, the ones set with `ZIOAspect.tagged` or `ZIO.tagged`, and adds the `level` label to them. Tagging a request handler with its service name therefore splits the counts per service:

```scala
import zio.{ UIO, ZIO, ZIOAspect }

def checkout(orderId: String): UIO[Unit] =
  (ZIO.logInfo(s"Order $orderId placed") *> ZIO.logWarning("Stock is low")) @@
    ZIOAspect.tagged("service" -> "checkout")
```

These two calls increment `zio_log_total` with the labels `level="info", service="checkout"` and `level="warn", service="checkout"`. Each distinct set of labels is a separate time series in a metrics backend, so tag with values that have a small, fixed set of possible values, such as a service or endpoint name, and never with ids such as an order id.

## Custom Metric and Label Names

To use a different metric name or a different name for the level label, for example to follow the naming rules of an existing dashboard, call `logMetricsWith(name, logLevelLabel)` instead of `logMetrics`:

```scala
import zio.logging.logMetricsWith

val customLayer = logMetricsWith("log_counter", "log_level")
```

This layer counts into the counter `log_counter`, with the level in the label `log_level`. Everything else works as for `logMetrics`.

## Custom Counters

`logMetrics` and `logMetricsWith` are built on `makeMetricLogger(counter, logLevelLabel)`, which returns the `MetricLogger` as an effect instead of a layer. Use it when you need something the layers do not offer, such as a counter that only counts some calls. The constructor and the logger are defined as follows:

```scala
// members of the zio.logging package
def makeMetricLogger(counter: Metric.Counter[Long], logLevelLabel: String): ZIO[Any, Nothing, MetricLogger]

final case class MetricLogger(counter: Metric.Counter[Long], logLevelLabel: String) extends ZLogger[String, Unit]
```

The following layer installs a second metric logger that counts only `ERROR` and `FATAL` calls, into a counter named `app_errors_total`. It can run next to `logMetrics`:

```scala
import zio.logging.{ LogFilter, ZLoggerZIOLayerOps, makeMetricLogger }
import zio.metrics.Metric
import zio.{ LogLevel, ZLayer }

val errorMetrics: ZLayer[Any, Nothing, Unit] =
  makeMetricLogger(Metric.counter("app_errors_total"), "level")
    .filter(LogFilter.logLevel(LogLevel.Error))
    .install
```

The `filter` and `install` syntax comes from `ZLoggerZIOLayerOps`, which the example imports, and is described in [Logger Layers and Constructors Reference](reference-logger-layers.md).

## Reading the Counter in a Test

A counter can be read back with `Metric#value`, which returns its current state. To check the count in a test, create a `Metric` with the same name and the same labels, and read its `count`. ZIO keeps metrics in one global registry for the whole process, so other tests that log with the same counter add to it; give the test its own counter name with `logMetricsWith`, or compare the value before and after the code under test, as this spec does:

```scala
import zio._
import zio.logging.logMetricsWith
import zio.metrics.Metric
import zio.test._

object CheckoutLogMetricsSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    test("counts one error line for the checkout service") {
      val checkoutErrors =
        Metric.counter("checkout_log_total").tagged("level", "error").tagged("service", "checkout")

      (for {
        before <- checkoutErrors.value
        _      <- (ZIO.logError("Payment declined") *> ZIO.logInfo("Order cancelled")) @@
                    ZIOAspect.tagged("service" -> "checkout")
        after  <- checkoutErrors.value
      } yield assertTrue(after.count - before.count == 1d))
        .provideLayer(logMetricsWith("checkout_log_total", "level"))
    }
}
```

The `info` call increments a different series, the one with `level="info"`, so it does not change the count that the test reads. The [Testing](testing.md) page shows how to check the log lines themselves.

## Exporting to Prometheus

ZIO metrics stay inside the application until a connector publishes them. To expose the counter to Prometheus, add the ZIO Metrics Connectors Prometheus module, which has its own version numbers:

```scala
libraryDependencies += "dev.zio" %% "zio-metrics-connectors-prometheus" % "2.6.0"
```

The following application prints log lines with the console logger, counts them with `logMetrics`, and then prints the metrics in the Prometheus text format from the `PrometheusPublisher`. `MetricsConfig(200.millis)` sets how often the connector collects the metrics:

```scala
import zio.logging.{ consoleLogger, logMetrics }
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.prometheus.{ PrometheusPublisher, prometheusLayer, publisherLayer }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer, _ }

object MetricsApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> (consoleLogger() ++ logMetrics)

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _            <- ZIO.logInfo("Start")
      _            <- ZIO.logWarning("Some warning")
      _            <- ZIO.logError("Some error")
      _            <- ZIO.logError("Another error")
      _            <- ZIO.sleep(1.second)
      metricValues <- ZIO.serviceWithZIO[PrometheusPublisher](_.get)
      _            <- Console.printLine(metricValues)
      _            <- ZIO.logInfo("Done")
    } yield ExitCode.success)
      .provideLayer((ZLayer.succeed(MetricsConfig(200.millis)) ++ publisherLayer) >+> prometheusLayer)

}
```

The application is also in the example project, at `examples/core/jvm/src/main/scala/zio/logging/example/MetricsApp.scala`. In a real service, an HTTP endpoint returns the publisher's text for Prometheus to scrape instead of printing it.

The output below is illustrative. It was recorded with an earlier version of the connector, and the exact layout of the Prometheus text, such as the comment lines and the timestamps, depends on the connector version. What stays the same is one `zio_log_total` sample per `level` label value:

```text
timestamp=2023-03-15T08:44:39.93193+01:00  level=INFO thread=zio-fiber-6 message="Start"
timestamp=2023-03-15T08:44:39.951764+01:00 level=WARN thread=zio-fiber-6 message="Some warning"
timestamp=2023-03-15T08:44:39.95388+01:00  level=ERROR thread=zio-fiber-6 message="Some error"
timestamp=2023-03-15T08:44:39.954738+01:00 level=ERROR thread=zio-fiber-6 message="Another error"
# TYPE zio_log_total counter
# HELP zio_log_total
zio_log_total{level="error",} 2.0 1678866280778
# TYPE zio_log_total counter
# HELP zio_log_total
zio_log_total{level="warn",} 1.0 1678866280778
# TYPE zio_log_total counter
# HELP zio_log_total
zio_log_total{level="info",} 1.0 1678866280778
timestamp=2023-03-15T08:44:40.972877+01:00 level=INFO thread=zio-fiber-6 message="Done"
```

You can find the source code of all examples [here](https://github.com/zio/zio-logging/tree/master/examples).
