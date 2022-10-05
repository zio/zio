---
id: trandom
title: "TRandom"
---

`TRandom` is a random service like [Random](../services/random.md) that provides utilities to generate random numbers, but they can participate in STM transactions.

The `TRandom` service is the same as the `Random` service. There are no differences in operations, but all return types are in the `STM` world rather than the `ZIO` world:

| Function      | Input Type    | Output Type                   |
| --------------| ------------- | ----------------------------- |
| `nextBoolean` |               | `URSTM[TRandom, Boolean]`     |
| `nextBytes`   | `length: Int` | `URSTM[TRandom, Chunk[Byte]]` |
| `nextDouble`  |               | `URSTM[TRandom, Double]`      |
| `nextInt`     |               | `URSTM[TRandom, Int]`         |
| ...           | ...           | ...                           |

When we use operations of the `TRandom` service, they add `TRandom` dependency on our `STM` data type. After committing all the transactions, we can `inject`/`provide` a `TRandom` implementation into our effect:

```scala mdoc:invisible
import zio._
import zio.stm._

val myApp = TRandom.nextInt.commit
```

```scala mdoc:silent:nest
myApp.provide(TRandom.live)
```
