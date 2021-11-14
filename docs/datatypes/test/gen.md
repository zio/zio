---
id: gen
title: "Gen"
---

A `Gen[R, A]` represents a generator of values of type `A`, which requires an environment `R`. Generators may be random or deterministic.

We encoded it as a stream of optional samples:

```scala
case class Gen[-R, +A](sample: ZStream[R, Nothing, Option[Sample[R, A]]]) {

}
```

## Creating a Generator

In the companion object of the `Gen` data type, there are tons of generators for various data types such as `Gen.int`, `Gen.double`, `Gen.boolean`, and so forth.

Let's create an `Int` generator:

```scala mdoc:silent:nest
import zio._
import zio.test._

val intGen: Gen[Has[Random], Int] = Gen.int
```

## Running a Generator

To run a generator, we can call `runCollect` operation:

```scala mdoc:nest
val ints: ZIO[Has[Random], Nothing, List[Int]] = intGen.runCollect.debug
// Output: List(-2090696713)
```

This will return a `ZIO` effect containing all its values in a list, which in this example it contains only one element.

To create more samples, we can use `Gen#runCollectN`, which repeatedly runs the generator as much as we need. In this example, it will generate a list of containing 5 integer elements:

```scala mdoc:compile-only
intGen.runCollectN(5).debug
// Output: List(281023690, -1852531706, -21674662, 187993034, -868811035)
```
