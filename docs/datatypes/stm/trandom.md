---
id: trandom
title: "TRandom"
---

`TRandom` is a random service like [Random](../contextual/services/random.md) that provides utilities to generate random numbers, but they can participate in STM transactions.

The `TRandom` service is the same as the `Random` service. There are no differences in operations, but all return types are in the `STM` world rather than the `ZIO` world:

```scala
trait TRandom {
  def nextBoolean:            STM[Nothing, Boolean]
  def nextBytes(length: Int): STM[Nothing, Chunk[Byte]]
  def nextDouble:             STM[Nothing, Double]
  def nextInt:                STM[Nothing, Int]
  // ...
}
```