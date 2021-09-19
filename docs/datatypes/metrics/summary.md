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

**`observeSummary`** — A metric aspect that adds a value to a summary each time the effect it is applied to succeeds. This aspect can be applied to effects producing a `Double`.

```scala
def observeSummary(
  name: String,
  maxAge: Duration,
  maxSize: Int,
  error: Double,
  quantiles: Chunk[Double],
  tags: MetricLabel*
): Summary[Double]
```

**`observeSummaryWith`** — A metric aspect that adds a value to a summary each time the effect it is applied to succeeds, using the specified function to transform the value returned by the effect to the value to add to the summary.

```scala
def observeSummaryWith[A](
  name: String,
  maxAge: Duration,
  maxSize: Int,
  error: Double,
  quantiles: Chunk[Double],
  tags: MetricLabel*
)(f: A => Double): Summary[A]
```
