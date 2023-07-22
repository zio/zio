---
id: random
title: "Random"
---

Random service provides utilities to generate random numbers. It's a functional wrapper of `scala.util.Random`. This service contains various different pseudo-random generators like `nextInt`, `nextBoolean` and `nextDouble`. Each random number generator functions return a `URIO[Random, T]` value.

```scala
import zio.random._
import zio.console._
for {
  randomInt <- nextInt
  _ <- putStrLn(s"A random Int: $randomInt")
  randomChar <- nextPrintableChar
  _ <- putStrLn(s"A random Char: $randomChar")
  randomDouble <- nextDoubleBetween(1.0, 5.0)
  _ <- putStrLn(s"A random double between 1.0 and 5.0: $randomDouble")
} yield ()
```

Random service has a `setSeed` which helps us to alter the state of the random generator. It is useful when writing the test version of Random service when we need a generation of the same sequence of numbers.

```scala
for {
  _ <- setSeed(0)
  nextInts <- (nextInt zip nextInt)
} yield assert(nextInts == (-1155484576,-723955400))
```

Also, it has a utility to shuffle a list or generating random samples from Gaussian distribution:

* **shuffle** - Takes a list as an input and shuffles it.
* **nextGaussian** — Returns the next pseudorandom, Gaussian ("normally") distributed double value with mean 0.0 and standard deviation 1.0.

> _**Note**:_
>
> Random numbers that are generated via Random service are not cryptographically strong. Therefore it's not safe to use the ZIO Random service for security domains where a high level of security and randomness is required, such as password generation.
