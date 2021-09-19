---
id: histogram
title: "Histogram"
---

A `Histogram` is a metric representing a collection of numerical with the distribution of the cumulative values over time. A typical use of this metric would be to track the time to serve requests.

Histograms allow visualizing not only the value of the quantity being measured but its distribution. **Histograms are constructed with user-specified boundaries which describe the buckets to aggregate values into**.

## Internals

A histogram observes _Double_ values and counts the observed values in buckets. Each bucket is defined by an upper boundary, and the count for a bucket with the upper boundary `b` increases by `1` if an observed value `v` is less or
equal to `b`.

As a consequence, all buckets that have a boundary `b1` with b1 > b will increase by `1` after observing `v`.

A histogram also keeps track of the overall count of observed values, and the sum of all observed values.

By definition, the last bucket is always defined as `Double.MaxValue`, so that the count of observed values in the last bucket is always equal to the overall count of observed values within the histogram.

The mental model for a ZMX histogram is inspired from [Prometheus](https://prometheus.io/docs/concepts/metric_types/#histogram).

## API

To define a histogram aspect, the API requires that the boundaries for the histogram are specified when creating the aspect.

* **`observeHistogram`** — Create a histogram that can be applied to effects producing `Double` values. The values will be counted as outlined above. 

```scala
def observeHistogram(name: String, boundaries: Chunk[Double], tags: MetricLabel*): Histogram[Double]
```

* **`observeHistogramWith`** — Create a histogram that can be applied to effects producing values `v` of `A`. The values `f(v)` will be counted as outlined above. 

```scala
def observeHistogramWith[A](name: String, boundaries: Chunk[Double], tags: MetricLabel*)(
  f: A => Double
): Histogram[A]
```
