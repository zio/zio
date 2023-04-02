---
id: counter
title: "Counter"
---

A `Counter` is a metric representing a single numerical value that may be incremented over time. A typical use of this metric would be to track the number of a certain type of request received.

With a counter, the quantity of interest is the cumulative value over time, as opposed to a [gauge](gauge.md) where the quantity of interest is the value as of a specific point in time.

## API

With one of the following constructors, we can create a counter of `Long`, `Double` or `Int` type:

```scala
object Metric {
  def counter(name: String): Counter[Long] = ???
  def counterDouble(name: String): Counter[Double] = ???
  def counterInt(name: String): Counter[Int] = ???
}
```

## Use Cases

We use the counter metric type for any value that increases, such as request counts. Note that we should never use the counter for a value that can decrease.

So when we should use counters?

- When we want to track a value over time, that only goes up
- When we want to measure the increasing rate of something, how fast something is growing, such as request rates.

Here are some of the use cases:

- Request Counts
- Completed Tasks
- Error Counts

## Examples

Create a counter named `countAll` which is incremented by `1` every time it is invoked:

```scala mdoc:silent:nest
import zio._
import zio.metrics._
val countAll = Metric.counter("countAll").fromConst(1)
```

Now the counter can be applied to any effect. Note, that the same aspect can be applied to more than one effect. In the example we would count the sum of executions of both effects in the for comprehension:

```scala
val myApp = for {
  _ <- ZIO.unit @@ countAll
  _ <- ZIO.unit @@ countAll
} yield ()
```

Or we can apply them in recurrence situations:

```scala mdoc:silent:nest
(zio.Random.nextLongBounded(10) @@ Metric.counter("request_counts")).repeatUntil(_ == 7)
```

Create a counter named `countBytes` that can be applied to effects having the output type `Double`:

```scala mdoc:silent:nest
val countBytes = Metric.counter("countBytes")
```

Now we can apply it to effects producing `Double` (in a real application the value might be the number of bytes read from a stream or something similar):

```scala mdoc:silent:nest
val myApp = Random.nextLongBetween(0, 100) @@ countBytes
```
