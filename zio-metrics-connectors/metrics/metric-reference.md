# ZIO Metric Reference

> All ZIO metrics are defined in the form of aspects that can be applied with `@@` to measure an effect. Applying an
aspect to an effect does not change the type of the effect. For example:

## Measuring

All ZIO metrics are defined in the form of aspects that can be applied with `@@` to measure an effect. Applying an
aspect to an effect does not change the type of the effect. For example:

```scala
val metric = Metric.counter("effect_counter")
val effect: ZIO[Any, Throwable, Double] = ???
val effectWithMeasurement: ZIO[Any, Throwable, Double] = effect @@ metric
```

### Name, description and labels

Every metric has a name and optionally a description. The name may be augmented by zero or more `Label`s (sometimes
called `tag`s). Labels are useful, because some reporting platforms support them and provide aggregation mechanisms
for them.

> For example, think of a counter that counts how often a particular service has been invoked. If the application is
> deployed across several hosts, we can model our counter with the name `my_service` and an additional label
> `(host, ${hostname})`. With this definition we can see the number of executions for each host, but we can also
> create a query in Grafana or DatadogHQ to visualize the aggregated value over all hosts. Using more than one label
> allows us to create visualizations across any combination of the labels.

Name and description are always given as part of the metric constructor, labels can be added via one of the `tagged`
methods. Here is an example:

```scala
val countRequests = Metric.counter("count_requests", "Description of the counter").tagged("hostname", hostname)
val effect: ZIO[Any, Throwable, Double] = ???
val effectWithTaggedMeasurement = effect @@ countRequests.tagged("path", path)
```

We recommend you follow the [Prometheus best practices](https://prometheus.io/docs/practices/naming/) guide for the metric name and labels.

### Supported type

Every `Metric` has the type parameter `In` which indicates what types of values can be measured by the metric. When
applying the metric as an aspect to an effect, the metric's `In` type must be compatible with the output type of the
effect (the `A` in `ZIO[R, E, A]`). For example, a `Metric.Counter[Any]` can be applied to any effect while a
`Metric.Counter[Double]` can only be applied to an effect that produces a `Double`.

In the underlying implementation, each metric type supports only one base type. _Counters_, _Gauges_, _Histograms_ and
_Summaries_ all support `Double` values, while a _Frequency_ supports `String` values. To support a different type, we
can use the `contramap` method. For example, a Gauge that supports `Long`s is written as:

```scala
val longGauge: Metrics.Gauge[Long] =
  Metric.Gauge("my_metric").contramap[Long](_.toDouble)
```

Method `fromConst` works like `contramap` but always returns the same value. For example a counter that always increases
with the same value is written as:

```scala
val occurrenceCounter: Metric.Counter[Any] =
  Metric.counter("my_metric").fromConst(1L)
```

### Measuring durations

To measure how long an effect takes, we can use methods `trackDuration` and `trackDurationWith`. See the histogram
section below for an example.

### Measuring failures

Normally, we only measure effects that succeed. To measure an effect that failed, we can use methods like `trackAll`,
`trackDefectWith`, `trackErrorWith` and `trackSuccessWith`. For example `trackError` lets you measure the error output
of an effect:

```scala
val countAllErrors =
  Metric.counter("all_errors").fromConst(1L).trackError

// counts the errors of `effect`
val effectWithMeasurement = effect @@ countAllErrors
```

# Metric types

## Counter

A counter is a metric of which the value can only increase over time. A counter can be created with one of these
methods:

```scala
val longCounter: Metric.Counter[Long] = Metric.counter(name = "my_metric")
val intCounter: Metric.Counter[Int] = Metric.counterInt(name = "my_metric")
val doubleCounter: Metric.Counter[Double] = Metric.counterDouble(name = "my_metric")
```

Besides using a counter as an aspect (with the `@@` operator), counter metrics can also measure values with their
`increment` and `incrementBy` methods. For example:

```scala
longCounter.incrementBy(10L)
```

### Examples

Create a counter named `count_all` which is incremented by `1` every time it is invoked. Since the metric type is `Any`
the aspect can be applied to effect of any type.

```scala 
val aspCountAll: Metric.Counter[Any] =
  Metric.counter("count_all").fromConst(1L)
```

The same aspect can be applied to more than one effect. In the following example we count the sum of executions of both
effects in the for comprehension:

```scala 
val countAll = for {
  _ <- ZIO.unit @@ aspCountAll
  _ <- ZIO.unit @@ aspCountAll
} yield ()
```  

Create a counter named `count_bytes` that can be applied to effects having the output type `Double`.

```scala
val aspCountBytes = Metric.counterDouble("count_bytes")
```

Now we can apply it to effects producing `Double` (in a real application the value might be the number of bytes read
from a stream or something similar):

```scala 
val countBytes = nextDoubleBetween(0.0d, 100.0d) @@ aspCountBytes
```

## Gauges

A gauge is a metric that can change over time. A gauge can be created as follows:

```scala
val doubleGauge: Metric.Gauge[Double] = Metric.gauge(name = "my_metric")
```

Besides using a gauge as an aspect (with the `@@` operator), gauge metrics can also measure values with their `set`,
`increment`, `incrementBy`, `decrement` and `decrementBy` methods. For example:

```scala
for {
  _ <- doubleGauge.set(10.0) // sets the gauge to 10.0
  _ <- doubleGauge.decrement // decreases the gauge with 1.0
} yield ()
```

### Examples

Create a gauge that can be applied to effects that produce a Double:

```scala
val aspGauge = Metric.gauge("set_gauge")
```

Now we can apply the metric to effects that produce `Double`s. Note that we can instrument an effect with any number of
aspects if the type constraints are satisfied.

```scala 
val measuredEffect = nextDoubleBetween(0.0d, 100.0d) @@ aspGauge @@ aspCountAll
```

## Histograms

A histogram is a metric that counts the measured values in buckets. Each bucket is defined by an upper boundary. The
count in a bucket with upper boundary `b` increases by `1` if an observed value `v` is less or equal to `b`. As a
consequence, all buckets that have a boundary `b1` with `b1 > b` will increase by `1` after observing `v`.

A histogram also keeps track of the overall count of observed values and the sum of all observed values.

The last bucket is always defined as `Double.MaxValue`, so that the count of observed values in the last bucket is
always equal to the overall count of observed values within the histogram.

The used histogram model is inspired by [Prometheus](https://prometheus.io/docs/concepts/metric_types/#histogram).

Boundaries can be created as follows:

```scala
// given boundaries: 100.0, 200.0, 300.0, 400.0, MaxValue
MetricKeyType.Histogram.Boundaries.fromChunk(Chunk(100.0, 200.0, 300.0, 400.0))
// linear boundaries: 100.0, 200.0, 300.0, 400.0, 500.0, 600.0, 700.0, 800.0, 900.0, 1000.0, MaxValue
MetricKeyType.Histogram.Boundaries.linear(100.0, 100.0, 10)
// exponential boundaries: 100.0, 200.0, 400.0, 800.0, 1600.0, 3200.0, 6400.0, 12800.0, 25600.0, 51200.0, MaxValue
MetricKeyType.Histogram.Boundaries.exponential(100.0, 2.0, 10)
```

A histogram can be created with the `Metric.histogram(name, boundaries)` method. For example:

```scala
val latencyHistogram: Metric.Histogram[Double] =
  Metric.histogram("queue_size", boundaries)
```

### Examples

Create a histogram with 10 buckets: `0..100` in steps of `10` and `Double.MaxValue`. It can be applied to effects
yielding a `Double`.

```scala 
val aspHistogram =
  Metric.histogram("my_histogram", Histogram.Boundaries.linear(0.0d, 10.0d, 10))
```

Now we can apply the histogram to effects producing `Double`:

```scala 
val measuredEffect = nextDoubleBetween(0.0d, 120.0d) @@ aspHistogram 
```

Create a histogram that observes `Duration`s in seconds. Use `trackDuration` to track the duration of an effect.
Note that using seconds as unit is the recommended convention in many metric platforms such as Prometheus and
OpenTelemetry.

```scala
val latencyHistogram: Metric.Histogram[Duration] =
  Metric.histogram(
      "my_latency_seconds",
      "The latency in seconds.",
      MetricKeyType.Histogram.Boundaries.exponential(0.01, 2.0, 10)
    )
    .contramap[Duration](_.toNanos.toDouble / 1e9) // convert to seconds

val effect: ZIO[Any, Throwable, Unit] = ???

effect @@ latencyHistogram.trackDuration
```

## Summaries

Similar to a histogram a summary also observes `Double` values. While a histogram directly modifies the bucket counters
and does not keep the individual samples, the summary keeps the observed samples in its internal state. To avoid the set
of samples grow uncontrolled, the summary need to be configured with a maximum age `t` and a maximum size `n`. To
calculate the statistics, maximal `n` samples will be used, all of which are not older than `t`.

Essentially the set of samples is a sliding window over the last observed samples matching the conditions above.

A summary is used to calculate a set of quantiles over the current set of samples. A quantile is defined by a `Double`
value `q` with `0 <= q <= 1` and resolves to a `Double` as well.

The value of a given quantile `q` is the maximum value `v` out of the current sample buffer with size `n` where at most
`q * n` values out of the sample buffer are less or equal to `v`.

Typical quantiles for observation are `0.5` (the median) and the `0.95`. Quantiles are very good for monitoring Service
Level Agreements.

The ZIO Metrics API also allows summaries to be configured with an error margin `e`. The error margin is applied to the
count of values, so that a quantile `q` for a set of size `s` resolves to value `v` if the number `n` of values less or
equal to `v` is `(1 -e)q * s <= n <= (1+e)q`.

A summary can be created with the `summary` method:

```scala
Metric.summary(
  name: String,
  maxAge: Duration,
  maxSize: Int,
  error: Double,
  quantiles: Chunk[Double]
): Metric.Summary[Double]
```

### Examples

Create a summary that can hold 100 samples, the max age of the samples is `1 day` and the error margin is `3%`. The
summary should report the `10%`, `50%` and `90%` quantiles. It can be applied to effects producing an `Int`.

```scala
val aspSummary =
  Metric.summary("my_summary", 1.day, 100, 0.03d, Chunk(0.1, 0.5, 0.9)).contramap[Int](_.toDouble)
```

Now we can apply this aspect to an effect producing an `Int`:

```scala
val summary = nextIntBetween(100, 500) @@ aspSummary
```

## Frequencies

Frequencies are used to count the occurrences of distinct string values. For example an application that uses logical
names for its services, the number of invocations for each service can be tracked.

Essentially, a Frequency is a set of related counters sharing the same name and tags. The counters are set apart from
each other by an additional configurable tag. The values of the tag represent the observed distinct values.

To configure a frequency aspect, the name of the tag holding the distinct values must be configured.

A frequency can be created with the `frequency` method:

```scala
Metric.frequency(name: String): Metric.Frequency[String]
```

### Examples

Create a Frequency to observe the occurrences of unique Strings. It can be applied to effects producing a String.

```scala
val aspSet = Metric.frequency("my_set")
```  

Now we can generate some keys within an effect and start counting the occurrences
for each value.

```scala 
val set = nextIntBetween(10, 20).map(v => s"my_key_$v") @@ aspSet
```
