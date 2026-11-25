# Tutorial: Instrument an App and Scrape Metrics with Prometheus

> In this tutorial, we instrument a small ZIO application with two metrics, a counter and a gauge. We serve the metrics
> over HTTP at `/metrics`, check the output with `curl`, and point a Prometheus server at the endpoint. By the end, you
> will have a working setup that you can adapt to your own application.

The tutorial uses sbt and assumes you know how to write a basic `ZIOAppDefault`.

## Add the Dependencies

We need the Prometheus connector to encode the metrics, and zio-http to serve them. Add both to your `build.sbt`:

```sbt
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-metrics-connectors-prometheus" % "2.6.1",
  "dev.zio" %% "zio-http"                          % "3.4.0",
)
```

The connector brings in the core ZIO Metrics library, so no other dependency is needed for metrics.

## Define the Metrics

We define a counter that counts processed orders and a gauge that shows how many orders are waiting. A metric is a value
that you apply to an effect with `@@`. The effect keeps its type, so applying a metric does not change how the code runs. The `OrderService` object defines both metrics:

```scala
import zio._
import zio.metrics.Metric

object OrderService {

  val ordersProcessed: Metric.Counter[Any] =
    Metric.counter("orders_processed", "Number of processed orders").fromConst(1L)

  val queueDepth: Metric.Gauge[Double] =
    Metric.gauge("queue_depth", "Number of orders waiting to be processed")

  def processOrder(id: Int): UIO[Unit] =
    (ZIO.sleep(50.millis) *> ZIO.unit) @@ ordersProcessed

}
```

The counter uses `fromConst(1L)`, so each call to `OrderService.processOrder` adds 1 to the count. The `Metric.Counter[Any]` type
means that the counter can be applied to any effect. The gauge takes a `Double`, and we set its value directly in the
next steps.

## Serve the Metrics Endpoint

The connector stores the encoded metrics in a `PrometheusPublisher`. The HTTP route reads that text with
`PrometheusPublisher#get` and returns it with the content type that Prometheus expects:

```scala
import java.nio.charset.StandardCharsets

import zio._
import zio.http._
import zio.metrics.connectors.prometheus.PrometheusPublisher

val metricsRoute: Route[PrometheusPublisher, Nothing] =
  Method.GET / "metrics" -> handler {
    ZIO.serviceWithZIO[PrometheusPublisher](_.get).map { body =>
      Response(
        status = Status.Ok,
        headers = Headers(Header.Custom(Header.ContentType.name, "text/plain; version=0.0.4")),
        body = Body.fromString(body, StandardCharsets.UTF_8),
      )
    }
  }

val routes: Routes[PrometheusPublisher, Nothing] = Routes(metricsRoute)
```

The `text/plain; version=0.0.4` content type identifies the Prometheus text format that the encoder produces.

## Run the Workload

We run the workload in a background fiber. Each second it processes five orders, then sets the queue depth to 3:

```scala
import zio._
import zio.metrics.connectors.prometheus.PrometheusPublisher

val workload: UIO[Unit] =
  (for {
    _ <- ZIO.foreachDiscard(1 to 5)(OrderService.processOrder)
    _ <- OrderService.queueDepth.set(3.0)
    _ <- ZIO.sleep(1.second)
  } yield ()).forever
```

## Wire the Layers and Start the Server

The application provides the server, the metrics layers, and the publisher. The `MetricsConfig` sets how often the connector
copies the metrics into the publisher. Five seconds is enough for this tutorial:

```scala
import zio._
import zio.http._
import zio.metrics.connectors.{prometheus, MetricsConfig}

object TutorialApp extends ZIOAppDefault {

  override def run =
    (workload.forkDaemon *> Server.serve(routes))
      .provide(
        ZLayer.succeed(Server.Config.default.port(8080)),
        Server.live,
        ZLayer.succeed(MetricsConfig(5.seconds)),
        prometheus.publisherLayer,
        prometheus.prometheusLayer,
      )
}
```

The `publisherLayer` creates the `PrometheusPublisher`, and `prometheusLayer` starts the background task that refreshes
it. The `MetricsConfig` interval controls that refresh. For more on the interval, see
[Configuration](../reference/configuration.md).

## Check the Output

We start the application with `sbt run` and request the endpoint from another terminal:

```
curl http://localhost:8080/metrics
```

After a few seconds, the response looks similar to this. The timestamps and counts depend on when you request the page:

```
# TYPE queue_depth gauge
# HELP queue_depth Number of orders waiting to be processed
queue_depth 3.0 1794253675282
# TYPE orders_processed counter
# HELP orders_processed Number of processed orders
orders_processed 20.0 1794253675282
```

Each metric appears with a `# TYPE` line and a `# HELP` line, followed by its current value and a timestamp in
milliseconds. The counter name is unchanged, with no `_total` suffix added.

## Scrape the Endpoint with Prometheus

Prometheus pulls metrics from the endpoint on a schedule that you configure. This `prometheus.yml` entry scrapes the
application every 15 seconds:

```yaml
scrape_configs:
  - job_name: zio-orders
    scrape_interval: 15s
    static_configs:
      - targets: ["localhost:8080"]
```

Start Prometheus with this file, and the two metrics appear in its expression browser under the names `orders_processed`
and `queue_depth`.

## Next Steps

You now have a complete pipeline from a ZIO effect to a Prometheus scrape. To go further:

- [Serving Prometheus Metrics](../how-to/serve-prometheus-metrics.md) explains the endpoint in more detail.
- [Adding Labels to Metrics](../how-to/add-labels.md) shows how to split one metric into several series.
- [Choosing a Connector](../how-to/choose-a-connector.md) compares Prometheus with the push-based connectors.
- [Metric Type Mapping](../reference/metric-type-mapping.md) lists how each metric type appears in every backend.
