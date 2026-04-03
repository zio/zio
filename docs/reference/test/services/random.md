---
id: random
title: "TestRandom"
---

When working with randomness, testing might be hard because the inputs to the tested function change on every invocation. So our code behaves in an indeterministic way.

Precisely because of this reason `ZIO` exposes `TestRandom` module which allows for fully deterministic testing of code that deals with Randomness. `TestRandom` can operate in two modes based on the needed use-case. It can generate a sequence of psudeo-random values using an initial seed with series of internal state transition or by feeding predefined random values.

## Initial Seed with Series of Internal State Transition

In the first mode, the `TestRandom` is a purely functional pseudo-random number generator. It will generate pseudo-random values just like `scala.util.Random`. While the `scala.util.Random` doesn't have internal state, the `TestRandom` has an internal state. Instead, methods like `nextInt` describe state transitions from one random state to another that are automatically composed together through methods like `flatMap`. 

The random seed can be set using `setSeed` and `TestRandom` is guaranteed to return the same sequence of values for any given seed. This is useful for deterministically generating a sequence of pseudo-random values and powers the property based testing functionality in ZIO Test:

```scala mdoc
import zio._
import zio.test.{test, _}
import zio.test.Assertion._

test("Use setSeed to generate stable values") {
  for {
    _ <- TestRandom.setSeed(27)
    r1 <- Random.nextLong
    r2 <- Random.nextLong
    r3 <- Random.nextLong
  } yield
    assertTrue(
      List(r1, r2, r3) == List[Long](
        -4947896108136290151L,
        -5264020926839611059L,
        -9135922664019402287L
      )
    )
}
```

## Feeding Predefined Random Values

In the second mode, `TestRandom` maintains an internal buffer of values that can be _fed_ with methods such as `feedInts` and then when random values of that type are generated they will first be taken from the buffer. This is useful for verifying that functions produce the expected output for a given sequence of _random_ inputs.

`TestRandom` will automatically take values from the buffer if a value of the appropriate type is available and otherwise generate a pseudo-random value, so there is nothing we need to do to switch between the two modes. Just generate random values as we normally would to get pseudo-random values, or feed in values of our own to get those values back.

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.Assertion._

test("One can provide its own list of ints") {
  for {
    _ <- TestRandom.feedInts(1, 9, 2, 8, 3, 7, 4, 6, 5)
    r1 <- Random.nextInt
    r2 <- Random.nextInt
    r3 <- Random.nextInt
    r4 <- Random.nextInt
    r5 <- Random.nextInt
    r6 <- Random.nextInt
    r7 <- Random.nextInt
    r8 <- Random.nextInt
    r9 <- Random.nextInt
  } yield assertTrue(
    List(1, 9, 2, 8, 3, 7, 4, 6, 5) == List(r1, r2, r3, r4, r5, r6, r7, r8, r9)
  )
}
```

We can also use methods like `clearInts` to clear the buffer of values of a given type, so we can fill the buffer with new values or go back to pseudo-random number generation.
