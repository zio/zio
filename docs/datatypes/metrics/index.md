---
id: index
title: "Introduction"
---

In highly concurrent applications, things are interconnected, so maintaining such setup to run smoothly and without application downtimes is very challenging. 

Imagine we have a complex infrastructure with lots of services. Services are replicated and distributed across our servers. So we have no insight on what happening on the across our services at level of errors, response latency, service uptime, etc. With ZIO Metrics, we can capture these different metrics and provide them to a _metric service_ for later investigation.

ZIO supports 5 types of Metrics:

* **[Counter](counter.md)** — The Counter is used for any value that increases over time like _request counts_.
* **[Gauge](gauge.md)** — The gauge is a single numerical value that can arbitrary goes up or down over time like _memory usage_.
* **[Histogram](histogram.md)** — The Histogram is used to track the distribution of a set of observed values across a set of buckets like _request latencies_.
* **[Summary](summary.md)** — The Summary represents a sliding window of a time series along with metrics for certain percentiles of the time series, referred to as quantiles like _request latencies_.
* **[SetCount](setcount.md)** — The SetCount is a metric that counts the number of occurrences of distinct string values.

All ZIO Metrics are defined in the form of ZIO Aspects that can be applied to effects without changing the signature of the effect it is applied to:

```scala mdoc:silent:nest
import zio._
import zio.metrics._
import java.lang.Runtime._

def memoryUsage: UIO[Double] = 
  ZIO.succeed(getRuntime.totalMemory() - getRuntime.freeMemory()).map(_ / (1024.0 * 1024.0))

val myApp: ZIO[Has[Random], Nothing, Unit] = for {
  _ <- (Random.nextIntBounded(10) @@ ZIOMetric.count("request_counts")).repeatUntil(_ == 7)
  _ <- memoryUsage @@ ZIOMetric.setGauge("memory_usage")
} yield ()
```

After adding metrics into our application, whenever we want we can capture snapshot of all metrics recorded by our application:

```scala mdoc:silent:nest
for {
  _        <- myApp 
  snapshot <- ZIO.succeed(MetricClient.unsafeSnapshot)
  _        <- Console.printLine(s"Current state of application metrics: $snapshot")
} yield ()
```

Also, a _metric service_ can implement the `MetricListener` interface:

```scala
trait MetricListener { self =>
  def unsafeGaugeChanged(key: MetricKey.Gauge, value: Double, delta: Double): Unit
  def unsafeCounterChanged(key: MetricKey.Counter, absValue: Double, delta: Double): Unit
  def unsafeHistogramChanged(key: MetricKey.Histogram, value: MetricState): Unit
  def unsafeSummaryChanged(key: MetricKey.Summary, value: MetricState): Unit
  def unsafeSetChanged(key: MetricKey.SetCount, value: MetricState): Unit
}
```

And then we can install that to our application which will be notified every time a metric is updated:

```scala
MetricClient.unsafeInstallListener(StatsDListener)
```
