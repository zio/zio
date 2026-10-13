# Labeled Instruments

> Metric instruments with pre-declared label names. Record values positionally; runtime arity validation prevents label mismatches.

A labeled instrument is an ordinary counter, histogram, or gauge that already knows its label *names*. You declare them once — `"method"`, `"status"` — and then pass only values: `add(1, "GET", "200")`.

It exists to keep call sites short and consistent: the names live in one place, so every recording site is guaranteed to use the same label keys in the same order, and reading `add(1, "GET", "200")` is quicker than reading a line of repeated pairs.

Do not reach for it as an optimization. The pair form (`add(1, "method" -> "GET", …)`) builds its `Attributes` through a pooled, thread-local builder, while the labeled form allocates a fresh builder and a fresh `Attributes` on every call — so labeled recording allocates more, not less. What you give up is safety: passing the wrong *number* of values throws right at the recording call, but passing them in the wrong *order* records silently — swap two and you get a plausible-looking series that's simply wrong. When a label combination really is hot, `bind` is the answer: it resolves the attribute set once and hands back a `Bound*` that writes straight to the accumulator. A [`Meter`](./meter.md) creates labeled instruments, through `labeledCounter`, `labeledHistogram`, and `labeledGauge`.

```scala
final class LabeledCounter private[telemetry] (...) {
  val labelNames: Array[String]
  def add(value: Long, labelValues: Any*): Unit
  def bind(labelValues: Any*): BoundCounter
  def collect(): MetricData
}

final class LabeledHistogram private[telemetry] (...) {
  val labelNames: Array[String]
  def record(value: Double, labelValues: Any*): Unit
  def bind(labelValues: Any*): BoundHistogram
  def collect(): MetricData
}

final class LabeledGauge private[telemetry] (...) {
  val labelNames: Array[String]
  def record(value: Double, labelValues: Any*): Unit
  def bind(labelValues: Any*): BoundGauge
  def collect(): MetricData
}
```

## Usage

Declare the label names once, then supply values in that same order on each call. The count of `labelValues` must match the declared `labelNames`; a mismatch throws `IllegalArgumentException` at the recording call site (not at construction), so a wrong arity surfaces immediately in tests.

```scala
import zio.blocks.telemetry._

val meter = MeterProvider.builder.build().get("com.example")

// "GET" → "method", "200" → "status"
val reqs = meter.labeledCounter("http.requests", "method", "status")
reqs.add(1L, "GET", "200")
reqs.add(1L, "POST", "500")

val lat = meter.labeledHistogram("request.latency", "route")
lat.record(15.0, "/orders")

val depth = meter.labeledGauge("queue.depth", "queue")
depth.record(7.0, "orders")
```

For a set of label values recorded over and over, `bind(labelValues*)` builds the `Attributes` once and returns a bound instrument that skips that work on each later call:

```scala
import zio.blocks.telemetry._

val reqs  = MeterProvider.builder.build().get("com.example").labeledCounter("rpc.calls", "service", "method")
val bound = reqs.bind("OrderService", "place")
bound.add(1L)
bound.add(1L)
```

Values flow through the wrapped `Counter`, `Histogram`, or `Gauge` and are collected by `MetricReader.collectAllMetrics()` like any other instrument — see [MetricData](./metric-data.md).
