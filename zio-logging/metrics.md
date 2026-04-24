# Log Metrics

> Log metrics collecting metrics related to ZIO logging (all `ZIO.log*` functions).
As ZIO core supporting multiple loggers, this logging metrics collector is implemented as specific `ZLogger`
which is responsible just for collecting metrics of all logs - `ZIO.log*` functions.

Log metrics collecting metrics related to ZIO logging (all `ZIO.log*` functions).
As ZIO core supporting multiple loggers, this logging metrics collector is implemented as specific `ZLogger`
which is responsible just for collecting metrics of all logs - `ZIO.log*` functions.

The Metrics layer

```scala
val layer = zio.logging.logMetrics
```

will add a default metric named `zio_log_total` with the label `level` which will be
incremented for each log message with the value of `level` being the corresponding log level label in lower case.

Metrics:

* `LogLevel.All` -> `all`
* `LogLevel.Fatal` -> `fatal`
* `LogLevel.Error` -> `error`
* `LogLevel.Warning` -> `warn`
* `LogLevel.Info` -> `info`
* `LogLevel.Debug` -> `debug`
* `LogLevel.Trace` -> `trace`
* `LogLevel.None` -> `off`

Custom names for the metric and label can be set via:

```scala
val layer = zio.logging.logMetricsWith("log_counter", "log_level")
```

## Examples

You can find the source
code [here](https://github.com/zio/zio-logging/tree/master/examples)

### Console logger with metrics

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

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

Expected Console Output:

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
