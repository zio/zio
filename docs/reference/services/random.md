---
id: "random "
title: "Random"
description: "Provides utilities to generate pseudo-random numbers with various generators including nextInt, nextBoolean, nextDouble, and Gaussian sampling."
keywords:
  - "Random Service"
  - "Pseudo-random Numbers"
  - "Number Generators"
  - "Gaussian Sampling"
  - "Random Seed"
  - "Shuffling"
---

Random service provides utilities to generate random numbers. It's a functional wrapper of `scala.util.Random`. This service contains various different pseudo-random generators like `nextInt`, `nextBoolean` and `nextDouble`. Each random number generator functions return a `[URIO](../core/zio/urio.md)[Random, T]` value.

```scala mdoc:compile-only
import zio._

for {
  randomInt    <- Random.nextInt
  _            <- Console.printLine(s"A random Int: $randomInt")
  randomChar   <- Random.nextPrintableChar
  _            <- Console.printLine(s"A random Char: $randomChar")
  randomDouble <- Random.nextDoubleBetween(1.0, 5.0)
  _            <- Console.printLine(s"A random double between 1.0 and 5.0: $randomDouble")
} yield ()
```

Random service has a `setSeed` which helps us to alter the state of the random generator. It is useful for setting up a test version of Random service when we need to reproduce always the same sequence of numbers.

```scala mdoc:compile-only
import zio._

for {
  _        <- Random.setSeed(0)
  nextInts <- (Random.nextInt zip Random.nextInt)
} yield assert(nextInts == (-1155484576,-723955400))
```

Also, it has a utility to shuffle a list and to generate random samples from Gaussian distribution:

* **shuffle** - Takes a list as an input and shuffles it.
* **nextGaussian** — Returns the next pseudorandom, Gaussian ("normally") distributed double value with mean 0.0 and standard deviation 1.0.

> **Note**:
>
> Random numbers that are generated via Random service are not cryptographically strong. Therefore it's not safe to use the ZIO Random service for security domains where a high level of security and randomness is required, such as password generation.

## See Also

- [Built-in Services](index.md) — Overview of ZIO's built-in services including Console, Clock, Random, and System that provide common functionality without explicit environment setup.
