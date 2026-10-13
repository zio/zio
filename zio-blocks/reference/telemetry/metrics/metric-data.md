# MetricData

> Aggregated metric snapshot produced by MetricReader.collectAllMetrics(). Three variants: SumData, HistogramData, GaugeData.

`MetricData` is an aggregated snapshot of one instrument at one collect cycle — its accumulated points, keyed by label set. You rarely build it directly: it is what `MetricReader.collectAllMetrics()` returns, one value per registered instrument, for a test to assert on or an export pipeline to serialize. Each collection builds fresh points, though a histogram point exposes its `boundaries` and `bucketCounts` arrays as-is, so read them and don't write to them.

One thing it does *not* carry is the instrument's name, description, or unit — a snapshot is only points and their label sets. So `collectAllMetrics()` hands back a `Seq[MetricData]` with no way to tell which instrument each entry came from, which is why an export pipeline pairs them with names of its own before serializing. In a test, collect from a single instrument with its own `collect()` when you need to know the source.

Its three variants line up with the instrument that produced them:

- `SumData` — from a [`Counter`](./instruments.md) or an `UpDownCounter`
- `HistogramData` — from a `Histogram`
- `GaugeData` — from a `Gauge`

So a `match` on `MetricData` tells you both the shape of the numbers and the kind of instrument they came from.

```scala
sealed trait MetricData

object MetricData {
  final case class SumData(points: List[SumDataPoint])             extends MetricData
  final case class HistogramData(points: List[HistogramDataPoint]) extends MetricData
  final case class GaugeData(points: List[GaugeDataPoint])         extends MetricData
}

final case class SumDataPoint(
  attributes:     Attributes,
  startTimeNanos: Long,
  timeNanos:      Long,
  value:          Long          // cumulative sum
)

final case class HistogramDataPoint(
  attributes:     Attributes,
  startTimeNanos: Long,
  timeNanos:      Long,
  count:          Long,         // total number of observations
  sum:            Double,       // sum of all observations
  min:            Double,       // smallest observation
  max:            Double,       // largest observation
  bucketCounts:   Array[Long],  // count per bucket (length == boundaries.length + 1)
  boundaries:     Array[Double] // the bucket upper boundaries
)

final case class GaugeDataPoint(
  attributes: Attributes,
  timeNanos:  Long,
  value:      Double            // most recently recorded value
)
```
