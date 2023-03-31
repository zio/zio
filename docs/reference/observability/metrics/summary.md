---
id: summary
title: "Summary"
---

A `Summary` represents a sliding window of a time series along with metrics for certain percentiles of the time series, referred to as quantiles.

Quantiles describe specified percentiles of the sliding window that are of interest. For example, if we were using a summary to track the response time for requests over the last hour then we might be interested in the 50th percentile, 90th percentile, 95th percentile, and 99th percentile for response times.


## Internals

Similar to a [histogram](histogram.md) a summary also observes _Double_ values. While a histogram directly modifies the bucket counters and does not keep the individual samples, the summary keeps the observed samples in its internal state. To avoid the set of samples grow uncontrolled, the summary needs to be configured with a maximum age `t` and a maximum size `n`. To calculate the statistics, maximal `n` samples will be used, all of which are not older than `t`.

Essentially, the set of samples is a _sliding window_ over the last observed samples matching the conditions above.

A summary is used to calculate a set of quantiles over the current set of samples. A quantile is defined by a _Double_ value `q` with `0 <= q <= 1` and resolves to a `Double` as well.

The value of a given quantile `q` is the maximum value `v` out of the current sample buffer with size `n` where at most `q * n` values out of the sample buffer are less or equal to `v`.

Typical quantiles for observation are `0.5` (the median) and the `0.95`. Quantiles are very good for monitoring _Service Level Agreements_.

The ZIO Metrics API also allows summaries to be configured with an error margin `e`. The error margin is applied to the count of values, so that a quantile `q` for a set of size `s` resolves to value `v` if the number `n` of values less or equal to `v` is `(1 -e)q * s <= n <= (1+e)q`.

## API

```scala
object Metric {
  def summary(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double]
  ): Summary[Double] = ???
             
  def summaryInstant(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double]
  ): Summary[(Double, java.time.Instant)] =
}
```

## Use Cases

Like [histograms](histogram.md), summaries are used for _monitoring latencies_, but they don't require us to define buckets. So, summaries are the best choice in these situations:
- When histograms are not proper for us, in terms of accuracy
- When we can't use histograms, as we don't have a good estimation of the range of values
- When we don't need to perform aggregation or averages across multiple instances, as the calculations are done on the application side

## Examples

Create a summary that can hold `100` samples, the max age of the samples is `1 day` and the error margin is `3%`. The summary should report the `10%`, `50%` and `90%` Quantile. It can be applied to effects yielding an `Int`:

```scala mdoc:silent:nest
import zio._
import zio.metrics._
import zio.metrics.Metric.Summary

val summary: Summary[Double] =
  Metric.summary(
    name = "mySummary", 
    maxAge = 1.day,
    maxSize = 100,
    error = 0.03d, 
    quantiles = Chunk(0.1, 0.5, 0.9)
  )
``` 

Now we can apply this aspect to an effect producing an `Int`:

```scala mdoc:silent:nest
Random.nextDoubleBetween(100, 500) @@ summary
```
