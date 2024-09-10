---
id: how-generators-work
title: "How Generators Work?"
---

A `Gen[R, A]` represents a generator of values of type `A`, which requires an environment `R`. The `Gen` data type is the base functionality for generating test data for property-based testing. We use them to produce deterministic and non-deterministic (PRNG) random values.

It is encoded as a stream of optional samples:

```scala
case class Gen[-R, +A](sample: ZStream[R, Nothing, Option[Sample[R, A]]])
```

Before deep into the generators, let's see what is property-based testing and what problem it solves in the testing world.

## How Generators Work?

We can think of `Gen[R, A]` as a `ZStream[R, Nothing, A]`. For example the `Gen.int` is a stream of random integers `ZStream.fromZIO(Random.nextInt)`.

To find out how a generator works, Let's take a look at the following snippet. It shows how the `Gen` data type is implemented.

:::caution
Although it doesn't provide the exact implementation, this condensed edition of the "Gen" data type is sufficient to grasp how generators operate.

For instance, we don't use a [pseudo-random generator](#random-generators-are-deterministic-by-default) throughout the following implementation. We haven't encoded the [shrinking algorithm](shrinking.md), either.
:::

```scala mdoc:compile-only
import zio._
import zio.test._
import zio.stream._

case class Gen[R, A](sample: ZStream[R, Nothing, A]) {
  def map[B](f: A => B): Gen[R, B] = Gen(sample.map(f))

  def flatMap[R1 <: R, B](f: A => Gen[R1, B]): Gen[R1, B] = ???

  def runCollect: ZIO[R, Nothing, List[A]] = sample.runCollect.map(_.toList)
}

object Gen {
  // A constant generator of the specified value.
  def const[A](a: => A): Gen[Any, A] =
    Gen(ZStream.succeed(a))

  // A random generator of integers.
  def int: Gen[Any, Int] =
    Gen(ZStream.fromZIO(Random.nextInt))
  def int(min: Int, max: Int): Gen[Any, Int] =
    ???

  // A random generator of specified values.
  def elements[A](as: A*): Gen[Any, A] =
    if (as.isEmpty) Gen(ZStream.empty) else int(0, as.length - 1).map(as)

  // A constant generator of fixed values.
  def fromIterable[A](xs: Iterable[A]): Gen[Any, A] =
    Gen(ZStream.fromIterable(xs))
}

Gen.const(42).runCollect.debug
// Output: List(42)

Gen.int.runCollect.debug
// Output: List(82) or List(3423) or List(-352) or ...

Gen.elements(1, 2, 3).runCollect.debug
// Output: List(1) or List(2) or List(3)

Gen.fromIterable(List(1, 2, 3))
// Output: List(1, 2, 3)
```

So we can see that the `Gen` data type is nothing more than the stream of random/constant values.

## Two Types of Generators

We have two types of generators:

1. **Deterministic Generators**— Generators that produce constant fixed values, such as `Gen.empty`, `Gen.const(42)` and `Gen.fromIterable(List(1, 2, 3))`.
2. **Random Generators**— Generators that produce random values, such as `Gen.boolean`, `Gen.int`, and `Gen.elements(1, 2, 3)`.

## Random Generators Are Deterministic by Default

The important fact about random generators is that they produce deterministic values by default. This means that if we run the same random generator multiple times, it will always produce the same sequence of values to achieve reproducibility.

So the let's add some debugging print lines inside a test and see what values are produced:

```scala mdoc:compile-only
import zio.test._
import zio.test.TestAspect._

object ExampleSpec extends ZIOSpecDefault {
  def spec =
    test("example test") {
      check(Gen.int(0, 10)) { n =>
        println(n)
        assertTrue(n + n == 2 * n)
      }
    } @@ samples(5)
}
```

We can see, even though the `Gen.int` is a non-deterministic generator, every time we run the test, the generator will produce the same sequence of values:

```scala
runSpec
9
3
0
9
6
+ example test
```

This is due to the fact that the generator uses a pseudo-random number generator which uses a deterministic algorithm.

The generator provides a fixed seed number to its underlying deterministic algorithm to generate random numbers. As the seed number is fixed, the generator will always produce the same sequence of values.

For more information, there is a separate page about this on [TestRandom](../services/random.md) which is the underlying service for generating test values.

This behavior helps us to have reproducible tests. However, if we need non-deterministic tests values, we can use the `TestAspect.nondeterministic` to change the default behavior:

```scala mdoc:compile-only
import zio.test._
import zio.test.TestAspect._

object ExampleSpec extends ZIOSpecDefault {
  def spec =
    test("example test") {
      check(Gen.int(0, 10)) { n =>
        println(n)
        assertTrue(n + n == 2 * n)
      }
    } @@ samples(5) @@ nondeterministic
}
```

## Combining Generators in ZIO

When working with generators in for comprehensions, it is essential to ensure the correct order of random generators. In some cases, combining deterministic and non-deterministic generators produce the same values repeatedly due to shared random state.

To solve this, ZIO provides the `forked` method, which runs generators in separate fibers, ensuring their random state and side effects are isolated from each other. This is particularly useful when combining random generators, like `Gen.uuid`, with deterministic ones such as `Gen.fromIterable`.

```scala
test("uuid before fromIterable generates distinct UUIDs") {
  check(
    for {
      id <- Gen.uuid.forked
      _  <- Gen.fromIterable(List(1, 2, 3)).forked
    } yield id
  ) { id =>
    assertCompletes
  }
}
```

In the above example, the `Gen.uuid.forked` and `Gen.fromIterable.forked` ensures that the UUID generator runs independently of the fromIterable generator. This ensures distinct random outputs in tests where random and deterministic generators are combined. It is particularly useful for preventing shared state across generators.

## How Samples Are Generated?

When we run the `check` function, the `check` function repeatedly run the underlying stream of generators (using the `forever` combinator), and then it takes `n` samples from the stream, where `n` is by default 200.

We can modify the default sample size by using the `samples` test aspect. So if we the `check` function, with `TestAspect.samples(5)`. Let's see how the samples are produced for each of the following generators:

- `check(Gen.const(42))(n => ???)` it will repeatedly run the `Zstream.succeed(42)` stream, and then take `n` samples from it: 42, 42, 42, 42, 42.
- `check(Gen.int)(n => ???)` it will repeatedly run the `ZStream.fromZIO(Random.nextInt)` stream, and then take `n` samples from it: e.g. 2, -3422, 33, 3991334, 98138.
- `check(Gen.elements(1, 2, 3))(n => ???)` it will repeatedly run the `ZStream.fromZIO(Random.nextInt(2).flatMap(Chunk(1, 2, 3)))` stream, and then take `n` samples from it: e.g. 3, 1, 1, 3, 2.
- `check(Gen.fromIterable(List(1, 2, 3)))(n => ???)` it will repeatedly run the `ZStream.fromIterable(List(1, 2, 3))` stream, and then take `n` samples from it: 1, 2, 3, 1, 2.

When we run the `check` function with multiple generators, the samples will be the cartesian product of their streams. Let's try some examples:

```scala mdoc:compile-only
import zio.test._

test("two deterministic generators") {
  check(Gen.const(1), Gen.fromIterable(List("a", "b", "c"))) { (a, b) =>
    println((a, b))
    assertTrue(true)
  }
} @@ TestAspect.samples(5)
```

The output will be:

```scala
(1,a)
(1,b)
(1,c)
(1,a)
(1,b)
+ two deterministic generators
1 tests passed. 0 tests failed. 0 tests ignored.
```

So the example above is something like this:

```scala mdoc:compile-only
import zio.stream._

{
  for {
    a <- ZStream.succeed(1)
    b <- ZStream.fromIterable(List("a", "b", "c"))
  } yield (a, b)
}.forever.take(5).runCollect.debug
```

Now let's try use one non-deterministic generator and one deterministic generator:

```scala mdoc:compile-only
import zio.test._

test("one non-deterministic generator and one deterministic generator") {
  check(Gen.int(1, 3), Gen.fromIterable(List("a", "b", "c"))) { (a, b) =>
    println((a, b))
    assertTrue(true)
  }
} @@ TestAspect.samples(5)
```

Here is one example output:

```scala
(3,a)
(3,b)
(3,c)
(2,a)
(2,b)
+ one non-deterministic generator and one deterministic generator
1 tests passed. 0 tests failed. 0 tests ignored.
```

This is the same as the previous example, it is like we have the following stream:

```scala mdoc:compile-only
import zio._
import zio.stream._

{
  for {
    a <- ZStream.fromZIO(Random.nextIntBetween(1, 3))
    b <- ZStream.fromIterable(List("a", "b", "c"))
  } yield (a, b)
}.forever.take(5).runCollect.debug
```

## Running a Generator For Debugging Purpose

To run a generator, we can call `runCollect` operation:

```scala mdoc:silent:nest
import zio._
import zio.test._

val ints: ZIO[Any, Nothing, List[Int]] = Gen.int.runCollect.debug
// Output: List(-2090696713)
```

This will return a `ZIO` effect containing all its values in a list, which in this example it contains only one element.

To create more samples, we can use `Gen#runCollectN`, which repeatedly runs the generator as much as we need. In this example, it will generate a list of containing 5 integer elements:

```scala mdoc:compile-only
Gen.int.runCollectN(5).debug
// Output: List(281023690, -1852531706, -21674662, 187993034, -868811035)
```

In addition, there is an operator called `Gen#runHead`, which returns the first value generated by the generator.
