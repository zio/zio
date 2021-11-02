---
id: random
title: "TestRandom"
---

`TestRandom` allows for deterministically testing effects involving randomness.

The `TestRandom` service operates in two modes:

1. In the first mode, `TestRandom` is a purely functional pseudo-random number generator. It will generate pseudo-random values just like `scala.util.Random` except that no internal state is mutated. Instead, methods like `nextInt` describe state transitions from one random state to another that are automatically composed together through methods like `flatMap`. 

  The random seed can be set using `setSeed` and `TestRandom` is guaranteed to return the same sequence of values for any given seed. This is useful for deterministically generating a sequence of pseudo-random values and powers the property based testing functionality in ZIO Test.
  
2. In the second mode, `TestRandom` maintains an internal buffer of values that can be _fed_ with methods such as `feedInts` and then when random values of that type are generated they will first be taken from the buffer. This is useful for verifying that functions produce the expected output for a given sequence of _random_ inputs. 

```scala mdoc:compile-only
import zio.Random
import zio.test.environment.TestRandom

for {
  _ <- TestRandom.feedInts(4, 5, 2)
  x <- Random.nextIntBounded(6)
  y <- Random.nextIntBounded(6)
  z <- Random.nextIntBounded(6)
} yield x + y + z == 11
```

`TestRandom` will automatically take values from the buffer if a value of the appropriate type is available and otherwise generate a pseudo-random value, so there is nothing we need to do to switch between the two modes. Just generate random values as we normally would to get pseudo-random values, or feed in values of our own to get those values back.

We can also use methods like `clearInts` to clear the buffer of values of a given type, so we can fill the buffer with new values or go back to pseudo-random number generation.
