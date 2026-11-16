# Log Metrics

> Log metrics collect metrics related to ZIO logging, counting every `ZIO.log*` call.
> ZIO core supports multiple loggers, so this metrics collector is implemented as a specific `ZLogger`
> that is responsible only for collecting metrics of `ZIO.log*` functions.

To add the metrics layer:

```scala
import zio.logging.logMetrics

val layer = logMetrics
```

This layer adds a default metric named `zio_log_total` with the label `level`. The metric increments for each log message,
and the label value is the corresponding log level in lower case.

Metrics:

* `LogLevel.All` -> `all`
* `LogLevel.Fatal` -> `fatal`
* `LogLevel.Error` -> `error`
* `LogLevel.Warning` -> `warn`
* `LogLevel.Info` -> `info`
* `LogLevel.Debug` -> `debug`
* `LogLevel.Trace` -> `trace`
* `LogLevel.None` -> `off`

To choose your own metric and label names, call `logMetricsWith`:

```scala
import zio.logging.logMetricsWith

val customLayer = logMetricsWith("log_counter", "log_level")
```

## Examples

You can find the source
code [here](https://github.com/zio/zio-logging/tree/master/examples)

### Console Logger with Metrics

To print log lines and count them in the same program, install `consoleLogger` and `logMetrics` together,
then read the Prometheus metrics from the publisher:

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

Running this program prints the log lines followed by the metric values scraped from the publisher:

```
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
