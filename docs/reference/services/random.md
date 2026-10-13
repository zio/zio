---
id: "random"
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

Random service provides utilities to generate random numbers. It's a functional wrapper of `scala.util.Random`. It follows the shared [service pattern](./anatomy.md) common to all of ZIO's built-in services: a `Random` trait, a `Live` implementation, and a synchronous `UnsafeAPI` for interop. Every method returns `UIO[A]` and cannot fail.

| Function            | Input Type                                     | Output Type            |
|---------------------|-------------------------------------------------|-------------------------|
| `nextBoolean`       |                                                  | `UIO[Boolean]`           |
| `nextBytes`         | `length: Int`                                   | `UIO[Chunk[Byte]]`       |
| `nextDouble`        |                                                  | `UIO[Double]`            |
| `nextDoubleBetween` | `minInclusive: Double, maxExclusive: Double`    | `UIO[Double]`            |
| `nextFloat`         |                                                  | `UIO[Float]`             |
| `nextFloatBetween`  | `minInclusive: Float, maxExclusive: Float`      | `UIO[Float]`             |
| `nextGaussian`      |                                                  | `UIO[Double]`            |
| `nextInt`           |                                                  | `UIO[Int]`               |
| `nextIntBetween`    | `minInclusive: Int, maxExclusive: Int`          | `UIO[Int]`               |
| `nextIntBounded`    | `n: Int`                                        | `UIO[Int]`               |
| `nextLong`          |                                                  | `UIO[Long]`              |
| `nextLongBetween`   | `minInclusive: Long, maxExclusive: Long`        | `UIO[Long]`              |
| `nextLongBounded`   | `n: Long`                                       | `UIO[Long]`              |
| `nextPrintableChar` |                                                  | `UIO[Char]`              |
| `nextString`        | `length: Int`                                   | `UIO[String]`            |
| `nextUUID`          |                                                  | `UIO[UUID]`              |
| `setSeed`           | `seed: Long`                                    | `UIO[Unit]`              |
| `shuffle`           | `collection: Collection[A]`                     | `UIO[Collection[A]]`     |

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

The bounded and "between" variants (`nextIntBounded`, `nextIntBetween`, and their `Long`/`Float`/`Double` counterparts) exclude their upper bound — `nextIntBetween(0, 6)` never returns `6`, the same convention as `nextInt(6)` on `scala.util.Random`. `nextBytes` returns a `Chunk[Byte]` rather than an `Array[Byte]`, keeping the result immutable and consistent with the rest of ZIO's collection types.

On the `Random` trait, `shuffle` is generic over any `Collection[A] <: Iterable[A]`, rebuilding the same collection type using an implicit `BuildFrom`, and runs a Fisher-Yates shuffle internally; the top-level `Random.shuffle` accessor specializes this to `List[A]`. `nextGaussian` returns the next pseudorandom, Gaussian ("normally") distributed double value with mean 0.0 and standard deviation 1.0:

```scala mdoc:compile-only
import zio._

val shuffledList: UIO[List[Int]] = Random.shuffle(List(1, 2, 3, 4, 5))
```

> **Note**:
>
> Random numbers that are generated via Random service are not cryptographically strong. Therefore it's not safe to use the ZIO Random service for security domains where a high level of security and randomness is required, such as password generation.

## `RandomScala`

Besides the default `RandomLive` implementation, which delegates to Scala's global `scala.util.Random`, `Random.RandomScala` wraps a caller-supplied `scala.util.Random` instance instead. It implements the same `Random` trait, so it's useful when you need a specific, seeded `scala.util.Random` instance — for example to obtain a reproducible sequence outside of tests, without going through `TestRandom`:

```scala mdoc:compile-only
import zio._

val customRandom: Random = Random.RandomScala(new scala.util.Random(42L))
```

## Synchronous Access (`unsafe`)

`Random` also exposes a synchronous `UnsafeAPI`, following the pattern described in [Anatomy of a Built-in Service](./anatomy.md#synchronous-access-the-unsafeapi). `RandomLive`'s `unsafe` implementation delegates directly to the corresponding method on `scala.util.Random` (`nextBoolean()`, `nextInt()`, `nextGaussian()`, and so on), without running an effect:

```scala mdoc:compile-only
import zio._

Unsafe.unsafe { implicit unsafe =>
  val n: Int = Random.RandomLive.unsafe.nextInt()
}
```

Prefer the ordinary `Random.nextInt`-style accessors everywhere else.

## Testing Randomness

`TestRandom` lets tests either feed predetermined sequences of values or use a deterministic pseudo-random generator instead of the live, unseeded generator. See [Testing Random](../test/services/random.md) for its `feed*`/`clear*` API.

## See Also

- [Built-in Services](index.md) — Overview of ZIO's built-in services including Console, Clock, Random, and System that provide common functionality without explicit environment setup.
