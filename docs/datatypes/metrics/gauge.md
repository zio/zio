---
id: gauge
title: "Gauge"
---

A `Gauge` is a metric representing a single numerical value that may be _set_ or _adjusted_. A typical use of this metric would be to track the current memory usage.

With a gauge, the quantity of interest is the current value, as opposed to a counter where the quantity of interest is the cumulative values over time.

A gauge is a named variable of type _Double_ that can change over time. It can either be set to an absolute value or relative to the current value.

## API

**`setGauge`** — Create a gauge that can be set to absolute values. It can be applied to effects yielding a Double

```scala
def setGauge(name: String, tags: MetricLabel*): Gauge[Double]
```

**`setGaugeWith`** — Create a gauge that can be set to absolute values. It can be applied to effects producing a value of type `A`. Given the effect produces `v: A` the gauge will be set to `f(v)` upon successful execution of the effect.

```scala
def setGaugeWith[A](name: String, tags: MetricLabel*)(f: A => Double): Gauge[A]
```

**`adjustGauge`** — Create a gauge that can be set relative to its previous value. It can be applied to effects yielding a _Double_.

```scala
def adjustGauge(name: String, tags: MetricLabel*): Gauge[Double]
```

**`adjustGaugeWith`** — Create a gauge that can be set relative to its previous value. It can be applied to effects producing a value of type `A`. Given the effect produces `v: A` the gauge will be modified by `_ + f(v)` upon successful execution of the effect.

```scala
def adjustGaugeWith[A](name: String, tags: MetricLabel*)(f: A => Double): Gauge[A]
```

## Use Case

The gauge metric type is the best choice for things that their values can go down as well as up, such as queue size, and we don't want to query their rates. Thus, they are used to measuring things that have a particular value at a certain point in time:

- Memory Usage
- Queue Size
- In-Progress Request Counts
- Temperature
