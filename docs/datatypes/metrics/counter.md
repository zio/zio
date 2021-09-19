---
id: counter
title: "Counter"
---

A `Counter` is a metric representing a single numerical value that may be incremented over time. A typical use of this metric would be to track the number of a certain type of request received.

With a counter, the quantity of interest is the cumulative value over time, as opposed to a [gauge](gauge.md) where the quantity of interest is the value as of a specific point in time.

## API

* **`count`** — Create a counter which is incremented by 1 every time it is executed successfully. This can be applied to any effect.

```
def count(name: String, tags: MetricLabel*): Counter[Any]
```

* **`countErrors`** — A counter which counts the number of failed executions of the effect it is applied to. This can be applied to any effect.

```
def countErrors(name: String, tags: MetricLabel*): Counter[Any]
```

* **`countValue`** — This counter can be applied to effects having an output type of Double. The counter will be increased by the value the effect produces.

```
def countValue(name: String, tags: MetricLabel*): Counter[Double]
```

* **`countValueWith`** — A counter that can be applied to effects having the result type `A`. Given the effect produces `v: A`, the counter will be increased by `f(v)`.

```
def countValueWith[A](name: String, tags: MetricLabel*)(f: A => Double): Counter[A]
```
