---
id: gauge
title: "Gauge"
---

A `Gauge` is a metric representing a single numerical value that may be _set_ or _adjusted_. A typical use of this metric would be to track the current memory usage.

With a gauge, the quantity of interest is the current value, as opposed to a counter where the quantity of interest is the cumulative values over time.

A gauge is a named variable of type _Double_ that can change over time. It can either be set to an absolute value or relative to the current value.

## API

TODO

## Use Case

The gauge metric type is the best choice for things that their values can go down as well as up, such as queue size, and we don't want to query their rates. Thus, they are used to measuring things that have a particular value at a certain point in time:

- Memory Usage
- Queue Size
- In-Progress Request Counts
- Temperature

## Examples

Create a gauge that can be set to absolute values, it can be applied to effects yielding a `Double`:

```scala mdoc:silent:nest
import zio._
import zio.metrics._
val absoluteGauge = Metric.gauge("setGauge")
```

Now we can apply these gauges to effects having an output type `Double`. Note that we can instrument an effect with any number of aspects if the type constraints are satisfied:

```scala mdoc:invisible
val countAll = Metric.counter("countAll").fromConst(1)
```

```scala mdoc:silent:nest
for {
  _ <- Random.nextDoubleBetween(0.0d, 100.0d) @@ absoluteGauge @@ countAll
} yield ()
```
