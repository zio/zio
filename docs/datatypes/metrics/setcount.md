---
id: setcount
title: "SetCount"
---

A `SetCount` represents the number of occurrences of specified values. We can think of a `SetCount` as a set of counters associated with each value except that new counters will automatically be created when new values are observed.

Essentially, a `SetCount` is a set of related counters sharing the same name and tags. The counters are set apart from each other by an additional configurable tag. The values of the tag represent the observed distinct values.

## API

To configure a set aspect, the name of the tag holding the distinct values must be configured.

**`occurrences`** — A metric aspect that counts the number of occurrences of each distinct value returned by the effect it is applied to:

```scala
def occurrences(name: String, setTag: String, tags: MetricLabel*): SetCount[String]
```

**`occurrencesWith`** — A metric aspect that counts the number of occurrences of each distinct value returned by the effect it is applied to, using the specified function to transform the value returned by the effect to the value to count the occurrences of:

```scala
def occurrencesWith[A](name: String, setTag: String, tags: MetricLabel*)(
  f: A => String
): SetCount[A]
```

## Use Cases

Sets are used to count the occurrences of distinct string values:
- Tracking number of invocations for each service, for an application that uses logical names for its services.
- Tracking frequency of different types of failures.

## Examples

Create a `SetCount` to observe the occurrences of unique `Strings`. It can be applied to effects yielding a `String`:

```scala mdoc:silent:nest
import zio._
val set = ZIOMetric.occurrences("MySet", "token")
```

Now we can generate some keys within an effect and start counting the occurrences for each value:

```scala mdoc:silent:nest
(Random.nextIntBounded(10).map(v => s"MyKey-$v") @@ set).repeatN(100)
```
