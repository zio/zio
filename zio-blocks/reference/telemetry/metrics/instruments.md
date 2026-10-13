# Metric Instruments

> The four synchronous metric instruments: monotonic Counter, bidirectional UpDownCounter, bucketed Histogram, and last-value Gauge.

The four synchronous instruments are what application code actually calls to record measurements. They differ by what they measure: a `Counter` sums a monotonic total, an `UpDownCounter` tracks a value that rises and falls, a `Histogram` builds a distribution of observations, and a `Gauge` holds the latest reading. Reach for them through [`metric`](./index.md) (`metric.counter("…")`) or a [`Meter`](./meter.md) builder — either path registers the instrument so `MetricReader.collectAllMetrics()` collects it into a [`MetricData`](./metric-data.md) snapshot.

All four share the same recording shape: `add` (counters) or `record` (histogram, gauge) takes a value plus optional dimension labels, either as an [`Attributes`](../common/attributes.md) set or as `(String, Any)*` tuples — a label value may be a `String`, `Long`, `Int` (widened to `Long`), `Double`, or `Boolean`, and anything else is recorded as its `toString`; `bind` pre-attaches a label set for hot-path reuse; and `collect` snapshots the accumulated data into a [`MetricData`](./metric-data.md) variant.

## Counter

A `Counter` records monotonically increasing values — negative deltas are ignored, so it only ever climbs. Use it for totals like requests served, errors, or bytes sent.

```scala
final class Counter private[telemetry] (
  val name: String, val description: String, val unit: String
) {
  def add(value: Long, attributes: Attributes): Unit
  def add(value: Long, attrs: (String, Any)*): Unit  // convenience vararg overload
  def bind(attributes: Attributes): BoundCounter      // pre-attributed for hot-path reuse
  def collect(): MetricData                           // snapshot → MetricData.SumData
}
```

Record with labels, then read the per-label totals from the collected `SumData`:

```scala
import zio.blocks.telemetry._

val calls = metric.counter("db.calls")
calls.add(1L, "table" -> "orders")
calls.add(2L, "table" -> "items")

val tableKey = AttributeKey.string("table")
metric.reader.collectAllMetrics().foreach {
  case MetricData.SumData(points) =>
    points.foreach(p => println(s"${p.attributes.get(tableKey)}: ${p.value}"))
  case _ => ()
}
```

## UpDownCounter

An `UpDownCounter` records bidirectional deltas — the same API as `Counter`, but negative values count. Use it for a running total that both rises and falls, such as active connections or queue depth.

```scala
final class UpDownCounter private[telemetry] (
  val name: String, val description: String, val unit: String
) {
  def add(value: Long, attributes: Attributes): Unit
  def add(value: Long, attrs: (String, Any)*): Unit
  def bind(attributes: Attributes): BoundUpDownCounter
  def collect(): MetricData
}
```

Add positive and negative deltas; the running total nets out:

```scala
import zio.blocks.telemetry._

val active = metric.upDownCounter("active.connections")
active.add(1L)   // new connection
active.add(-1L)  // connection closed

metric.reader.collectAllMetrics().foreach {
  case MetricData.SumData(points) => println(points.head.value)  // 0
  case _ => ()
}
```

## Histogram

A `Histogram` distributes `Double` observations into buckets and accumulates their count, sum, min, and max per label set. Use it for value distributions like request latency or payload size. Observations fall into a fixed set of bucket boundaries, defaulting to `[0, 5, 10, 25, 50, 75, 100, 250, 500, 750, 1000, 2500, 5000, 7500, 10000]`.

Each boundary is the **inclusive** upper bound of its bucket, so with boundaries `[5, 10]` a value of exactly `5` lands in the first bucket, not the second, and anything above the last boundary falls into one final overflow bucket. Note that the default set starts at `0`, so its first bucket holds only values at or below zero.

For different boundaries, construct the histogram directly — the builder has no setter for them:

```scala
import zio.blocks.telemetry._

val latency = Histogram("http.latency.ms", "Request latency", "ms", Array(10.0, 50.0, 100.0, 500.0))
latency.record(42.0, "route" -> "/orders")

val snapshot = latency.collect()
```

That path trades away registration: an instrument you build yourself belongs to no meter, so `collectAllMetrics()` never returns it and you have to call `collect()` on the instrument yourself.

```scala
final class Histogram private[telemetry] (
  val name: String, val description: String, val unit: String,
  val boundaries: Array[Double]
) {
  def record(value: Double, attributes: Attributes): Unit
  def record(value: Double, attrs: (String, Any)*): Unit
  def bind(attributes: Attributes): BoundHistogram
  def collect(): MetricData                            // snapshot → MetricData.HistogramData
}
```

Record observations, then read `count` and `sum` from the collected `HistogramData`:

```scala
import zio.blocks.telemetry._

val latency = metric.histogram("request.latency")
latency.record(42.5, "endpoint" -> "/api/orders")
latency.record(1500.0, "endpoint" -> "/api/reports")

metric.reader.collectAllMetrics().foreach {
  case MetricData.HistogramData(points) =>
    points.foreach(p => println(s"count=${p.count} sum=${p.sum}"))
  case _ => ()
}
```

## Gauge

A `Gauge` holds the most recent `Double` value per label set — each `record` overwrites the previous one. Use it for an instantaneous reading like CPU temperature or a current queue-depth snapshot.

```scala
final class Gauge private[telemetry] (
  val name: String, val description: String, val unit: String
) {
  def record(value: Double, attributes: Attributes): Unit
  def record(value: Double, attrs: (String, Any)*): Unit
  def bind(attributes: Attributes): BoundGauge
  def collect(): MetricData                            // snapshot → MetricData.GaugeData
}
```

Each `record` overwrites the last; the snapshot holds the latest value:

```scala
import zio.blocks.telemetry._

val temp = metric.gauge("cpu.temperature")
temp.record(72.5)
temp.record(74.1)  // overwrites the previous value

metric.reader.collectAllMetrics().foreach {
  case MetricData.GaugeData(points) => println(points.head.value)  // 74.1
  case _ => ()
}
```

## Choosing an Instrument

| Instrument      | Delta constraint | Use when                                |
|-----------------|------------------|-----------------------------------------|
| `Counter`       | Non-negative     | Counting requests, errors, events       |
| `UpDownCounter` | Any              | Active connections, queue-depth changes |
| `Histogram`     | Any `Double`     | Latency, payload-size distributions     |
| `Gauge`         | Any `Double`     | CPU temperature, queue-depth snapshot   |

## Bound Instruments

When one label combination is recorded repeatedly on a hot path, `bind(attrs)` returns a `Bound*` instrument pre-associated with that label set, so recording skips rebuilding `Attributes` on every call:

```scala
import zio.blocks.telemetry._

val bound = metric.counter("rpc.calls").bind(Attributes.of(AttributeKey.string("method"), "OrderService.place"))
bound.add(1L)
bound.add(1L)  // no Attributes construction per call
```

One caveat on a histogram: `bind` creates the per-label state immediately, so a label set you bind but never record collects as `count = 0` with `min` and `max` at their sentinel extremes (`Double.MaxValue` and `Double.MinValue`). Skip zero-count points when plotting, or bind only where you will record.

For a name-based label API — declare label names once, then pass values positionally — see [Labeled Instruments](./labeled-instruments.md).

## Collection

`MetricReader.collectAllMetrics()` calls each registered instrument's `collect()`, producing one `MetricData` per instrument: `SumData` for `Counter` and `UpDownCounter`, `HistogramData` for `Histogram`, and `GaugeData` for `Gauge`. Pattern-match to read the data points — see [MetricData](./metric-data.md) for the point structure. Only instruments obtained from `metric.*` or a `Meter` builder are registered; do not construct an instrument directly, as an unregistered one never reaches `collectAllMetrics()`.
