# How to Report ZIO Runtime and JVM Metrics

> Besides the metrics that you define, ZIO can record metrics about its own runtime, such as fiber counts and execution
> times, and about the JVM, such as memory and garbage collection. A connector reports them together with your own
> metrics. This guide shows how to turn both on.

## Enable the Metrics

Provide `Runtime.enableRuntimeMetrics` to record the ZIO runtime metrics. Provide `DefaultJvmMetrics.liveV2` to record
the JVM metrics. Both layers go in the same `provide` call as the connector layers:

```scala
import zio._
import zio.metrics.connectors.{prometheus, MetricsConfig}
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.jvm.DefaultJvmMetrics

object RuntimeMetricsApp extends ZIOAppDefault {

  val program: ZIO[PrometheusPublisher, Nothing, Unit] =
    for {
      _    <- ZIO.sleep(1.second)
      body <- ZIO.serviceWithZIO[PrometheusPublisher](_.get)
      _    <- ZIO.succeed(println(body))
    } yield ()

  override def run =
    program.provide(
      ZLayer.succeed(MetricsConfig(500.millis)),
      prometheus.publisherLayer,
      prometheus.prometheusLayer,
      Runtime.enableRuntimeMetrics,
      DefaultJvmMetrics.liveV2.unit,
    )
}
```

Add `.unit` to `DefaultJvmMetrics.liveV2`. The layer produces a value that the application does not use, and `.unit`
turns it into a layer that only starts the metrics.

The same two layers work with every connector, because they only record metrics. The connector decides where the
metrics go. The program prints the Prometheus text once, so that you can see the runtime and JVM metrics next to your own.

## Choose a JVM Metrics Layer

`DefaultJvmMetrics.live` is deprecated. It uses metric names from an older Prometheus client. `DefaultJvmMetrics.liveV2`
uses names that follow the OpenMetrics convention, so use it in new code. A dashboard that was built on the old names
must be updated when you switch.
