---
id: index
title: "Introduction to ZIO Metrics"
---

In highly concurrent applications, things are interconnected, so maintaining such setup to run smoothly and without application downtimes is very challenging. 

Imagine we have a complex infrastructure with lots of services. Services are replicated and distributed across our servers. So we have no insight on what happening on the across our services at level of errors, response latency, service uptime, etc. With ZIO Metrics, we can capture these different metrics and provide them to a _metric service_ for later investigation.

ZIO supports 5 types of Metrics:

* **[Counter](counter.md)** — The Counter is used for any value that increases over time like _request counts_.
* **[Gauge](gauge.md)** — The gauge is a single numerical value that can arbitrary goes up or down over time like _memory usage_.
* **[Histogram](histogram.md)** — The Histogram is used to track the distribution of a set of observed values across a set of buckets like _request latencies_.
* **[Summary](summary.md)** — The Summary represents a sliding window of a time series along with metrics for certain percentiles of the time series, referred to as quantiles like _request latencies_.
* **[Frequency](frequency.md)** — The Frequency is a metric that counts the number of occurrences of distinct string values.

All ZIO Metrics are defined in the form of ZIO Aspects that can be applied to effects without changing the signature of the effect it is applied to:

```scala mdoc:silent:nest
import zio._
import zio.metrics._

def memoryUsage: ZIO[Any, Nothing, Double] = {
  import java.lang.Runtime._
  ZIO
    .succeed(getRuntime.totalMemory() - getRuntime.freeMemory())
    .map(_ / (1024.0 * 1024.0)) @@ Metric.gauge("memory_usage")
}
```

After adding metrics into our application, whenever we want we can capture snapshot of all metrics recorded by our application, by any of metric backends supported by [ZIO Metrics Connectors](https://github.com/zio/zio-metrics-connectors) project.

Here is an example of adding prometheus connector to our application:

```scala mdoc:compile-only
import zio._
import zio.metrics.Metric

import zio.http._
import zio.http.model.Method
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.{MetricsConfig, prometheus}

object SampleMetricApp extends ZIOAppDefault {
  
  def memoryUsage: ZIO[Any, Nothing, Double] = {
    import java.lang.Runtime._
    ZIO
      .succeed(getRuntime.totalMemory() - getRuntime.freeMemory())
      .map(_ / (1024.0 * 1024.0)) @@ Metric.gauge("memory_usage")
  }

  private val httpApp =
    Http
      .collectZIO[Request] {
        case Method.GET -> !! / "metrics" =>
          ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
        case Method.GET -> !! / "foo" =>
          for {
            _ <- memoryUsage
            time <- Clock.currentDateTime
          } yield Response.text(s"$time\t/foo API called")
      }

  override def run = Server
    .serve(httpApp)
    .provide(
      // ZIO Http default server layer, default port: 8080
      Server.default,
      // The prometheus reporting layer
      prometheus.prometheusLayer,
      prometheus.publisherLayer,
      // Interval for polling metrics
      ZLayer.succeed(MetricsConfig(5.seconds))
    )
}
```

ZIO Metrics Connectors currently supports the following backends:

  -[Prometheus](https://prometheus.io/)
  -[Datadog](https://www.datadoghq.com/)
  -[New Relic](https://newrelic.com/)
  -[StatsD](https://github.com/statsd/statsd)
