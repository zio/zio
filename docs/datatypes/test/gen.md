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

In the companion object of the `Gen` data type, there are tons of generators for various data types such as `Gen.int`, `Gen.double`, `Gen.boolean`, and so forth.
