# Metrics

> Record how your application behaves over time — request rates, latencies, queue depths — as labeled counters, histograms, and gauges.

Metrics are the running numbers that tell you how your application is behaving over time — request rate, error count, latency, queue depth — the data you put on dashboards and alerts. They're **dimensional**: each measurement carries labels (like `method=GET`, `status=200`), and a metric splits into a separate series per label combination, so you can break "requests" down by endpoint or status. You record through four [instruments](./instruments.md), each for a different shape of number — a `Counter` for a total that only climbs, an `UpDownCounter` for one that rises and falls, a `Histogram` for a distribution of values, and a `Gauge` for the latest reading.

You record through the `metric` object: `metric.counter("http.requests").add(1)` works immediately, with no setup — it creates the named instrument and records the measurement. Pick the factory that matches what you're measuring — `metric.counter`, `metric.upDownCounter`, `metric.histogram`, or `metric.gauge` — and pass dimension labels as you record.

Measurements accumulate in memory, and reading them out is a pull: `metric.reader.collectAllMetrics()` returns [`MetricData`](./metric-data.md) snapshots — handy for tests and assertions, and the same call an exporter makes. Metrics have no push pipeline the way tracing and logging do: there is no processor hook on `MeterProvider`, so shipping them means scheduling that pull yourself and handing the snapshots to your backend. `metric.install(provider)` swaps in a provider configured with your own `Resource`.

Day to day you only need `metric.*`, but it helps to know what sits underneath, because each piece is where you go when you want more control. A [`MeterProvider`](./meter-provider.md) is what you build at startup: it holds your service identity as a `Resource` and vends everything else. (Unlike spans and log records, collected metric data does not carry that `Resource`, so an exporter has to attach the service identity itself.) A [`Meter`](./meter.md) is a named handle you take with `metric.get("com.example.orders")` when measurements should be attributed to one component, or when you want to declare an instrument's unit and description, or pre-declare label names for a hot path.

The other two are the recording and reading ends. The four [instruments](./instruments.md) are what you actually record through, however you obtained them. And the `MetricReader` behind `metric.reader` is what turns everything recorded so far into `MetricData` snapshots — the thing you call in tests, and the thing an exporter pulls from in production.

## Usage

Metrics' core job is to **record and export application metrics**. Create instruments from `metric`, record measurements with dimension labels as work happens, then read aggregated snapshots (or `metric.install(...)` a provider to export them):

```scala
import zio.blocks.telemetry._

val requests = metric.counter("http.requests")
requests.add(1, "method" -> "GET", "status" -> "200")

val latency = metric.histogram("http.latency.ms")
latency.record(12.5, "route" -> "/orders")

val snapshots = metric.reader.collectAllMetrics()
snapshots.foreach {
  case MetricData.SumData(points)       => points.foreach(p => println(p.value))
  case MetricData.HistogramData(points) => points.foreach(p => println(p.count))
  case MetricData.GaugeData(points)     => points.foreach(p => println(p.value))
}
```

## Record Measurements with the Four Instruments

Each factory creates a new instrument on the default meter. Nothing is cached by name, so build each one once and hold it — calling `metric.counter("http.requests")` twice gives you two independent counters and two entries in the collected snapshot.

```scala
object metric {
  def counter(name: String): Counter
  def upDownCounter(name: String): UpDownCounter
  def histogram(name: String): Histogram
  def gauge(name: String): Gauge
}
```

The four instruments differ by what they measure: a `Counter` sums a monotonic total, an `UpDownCounter` tracks a value that rises and falls, a `Histogram` builds a distribution of recorded values, and a `Gauge` holds the latest instantaneous reading. Every recording call accepts dimension labels as `(String, Any)*` pairs, which split the metric into separately-aggregated series.

```scala
import zio.blocks.telemetry._

metric.counter("http.requests").add(1, "method" -> "GET", "status" -> "200")
metric.upDownCounter("queue.depth").add(-1, "queue" -> "orders")
metric.histogram("http.latency.ms").record(12.5, "route" -> "/orders")
metric.gauge("cache.entries").record(4096.0, "cache" -> "sessions")
```

Note the two verbs. `add(delta: Long)` takes a *change* and the instrument keeps the running total — a `Counter` ignores a negative delta, while an `UpDownCounter` accepts one, so `add(1)`/`add(-1)` tracks queue depth. `record(value: Double)` takes the *measurement itself*: a `Histogram` files each value into buckets, and a `Gauge` keeps only the newest.

## Scope a Meter and Pre-Declare Labels

`metric.get(name)` returns a `Meter` bound to a named instrumentation scope.

```scala
object metric {
  def get(name: String): Meter
}
```

Its builders attach a description and unit, and its labeled variants pre-declare label names so a hot path records positionally instead of naming a key at every call.

```scala
import zio.blocks.telemetry._

val meter: Meter = metric.get("com.example.orders")

val payments: Counter =
  meter.counterBuilder("payments.total").setUnit("{payment}").setDescription("Payments processed").build()
payments.add(1, "currency" -> "USD")

val byRoute: LabeledCounter = meter.labeledCounter("http.requests", "method", "status")
byRoute.add(1, "GET", "200")
```

`setUnit` labels what the numbers mean, so a dashboard can axis-label `http.latency` as milliseconds rather than guessing. Use the UCUM codes OpenTelemetry expects — `"ms"`, `"s"`, `"By"` for bytes — and for a plain count of things, the thing in braces: `"{payment}"`, `"{request}"`. It's metadata attached to the instrument, not part of any measurement, and it stays on the instrument: the snapshots `metric.reader` returns carry only data points, so a unit reaches a backend through an exporter, not through `MetricData`.

## Read Aggregated Snapshots

Read every registered instrument as a `MetricData` snapshot — a fresh value each call, though a histogram point hands you its `boundaries` and `bucketCounts` arrays directly, so treat them as read-only.

```scala
object metric {
  def reader: MetricReader
}
```

`metric.reader.collectAllMetrics()` aggregates them — `SumData` for counters, `HistogramData` for histograms, `GaugeData` for gauges — for inspection in tests or manual export.

```scala
import zio.blocks.telemetry._

val snapshots: Seq[MetricData] = metric.reader.collectAllMetrics()
snapshots.foreach {
  case MetricData.SumData(points)       => points.foreach(p => println(p.value))
  case MetricData.HistogramData(points) => points.foreach(p => println(p.count))
  case MetricData.GaugeData(points)     => points.foreach(p => println(p.value))
}
```

## Install a Provider and Reset

`metric.install(provider)` swaps in a `MeterProvider` configured with your own [`Resource`](../common/resource.md). The builder takes nothing else — there is no exporter or reader hook — so instruments created after the swap register on the new provider's meters and are read back through `metric.reader`.

```scala
object metric {
  def install(provider: MeterProvider): Unit
  def removeAll(): Unit
}
```

Install before creating any instruments. Both calls replace the provider along with its registry, so an instrument built beforehand stays registered with the old one and won't appear in `metric.reader` snapshots. `metric.removeAll()` restores a fresh default provider.

```scala
import zio.blocks.telemetry._

metric.install(
  MeterProvider.builder
    .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "order-service")))
    .build()
)

// later, at shutdown
metric.removeAll()
```

## See Also

- [Telemetry Guide](../../../guides/telemetry-guide.md) — metrics data flow and labeled instrument patterns
- [Telemetry Reference](../index.md) — module overview and all three pillars
- [Common Types](../common/index.md) — `Attributes` used as metric dimension labels
