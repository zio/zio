---
id: built-in-generators
title: "Built-in Generators"
---

In the companion object of the `Gen` data type, there are tons of generators for various data types.

## Primitive Types Generators

ZIO Test provides generators for primitive types such as `Gen.int`, `Gen.string`, `Gen.boolean`, `Gen.float`, `Gen.double`, `Gen.bigInt`, `Gen.byte`, `Gen.bigdecimal`, `Gen.long`, `Gen.char`, and `Gen.short`.

Let's create an `Int` generator:

```scala mdoc:silent:nest
import zio._
import zio.test._

val intGen: Gen[Any, Int] = Gen.int
```

## Character Generators

In addition to `Gen.char`, ZIO Test offers a variety of specialized character generators:

* `Gen.alphaChar` — e.g. `Z, z, A, t, o, e, K, E, y, N`
* `Gen.alphaNumericChar` — e.g. `b, O, X, B, 4, M, k, 9, a, p`
* `Gen.asciiChar` — e.g. `, >, , , , 2, k, , , `
* `Gen.unicodeChar` — e.g. `了, , 옷, 嗀, , 뮲, ﹓, 癮, , ᜣ)`
* `Gen.numericChar` — e.g. `1, 0, 1, 5, 6, 9, 4, 4, 5, 2`
* `Gen.printableChar` — e.g. `H, J, (, Q, n, g, 4, G, 9, l`
* `Gen.whitespaceChars` — e.g. `, ,  , , ,  ,  ,  ,  , `
* `Gen.hexChar` — e.g. `3, F, b, 5, 9, e, 2, 8, b, e`
* `Gen.hexCharLower` — e.g. `f, c, 4, 4, c, 2, 5, 4, f, 3`
* `Gen.hexCharUpper` — e.g. `4, 8, 9, 8, C, 9, F, A, E, C`

## String Generators

Besides the primitive string generator, `Gen.string`, ZIO Test also provides the following specialized generators:

1. `Gen.stringBounded` — A generator of strings whose size falls within the specified bounds:

  ```scala mdoc:compile-only
  Gen.stringBounded(1, 5)(Gen.alphaChar)
    .runCollectN(10)
    .debug
  // Sample Output: List(b, YJXzY, Aro, y, WMPbj, Abxt, kJep, LKN, kUtr, xJ)
  ```

2. `Gen.stringN` — A generator of strings of fixed size:

  ```scala mdoc:compile-only
  Gen.stringN(5)(Gen.alphaChar)
    .runCollectN(10)
    .debug
  // Sample Output: List(BuywQ, tXCEy, twZli, ffLwI, BPEbz, OKYTi, xeDJW, iDUVn, cuMCr, keQAA)
  ```

3. `Gen.string1` — A generator of strings of at least one character.
4. `Gen.alphaNumericString` — A generator of alphanumeric characters.
5. `Gen.alphaNumericStringBounded` — A generator of alphanumeric strings whose size falls within the specified bounds.
6. `Gen.iso_8859_1` — A generator of strings that can be encoded in the ISO-8859-1 character set.
7. `Gen.asciiString` — A generator of US-ASCII characters.

## Generating Fixed Values

1. `Gen.const` — A constant generator of the specified value.

  ```scala mdoc:compile-only
  Gen.const(true).runCollectN(5)
  // Output: List(true, true, true, true, true)
  ```

2. `Gen.constSample` — A constant generator of the specified sample:

  ```scala mdoc:compile-only
   Gen.constSample(Sample.noShrink(false)).runCollectN(5)
  // Output: List(true, true, true, true, true)
  ```

3. `Gen.unit` — A constant generator of the unit value.

4. `Gen.throwable` — A generator of throwables.

Note that there is an empty generator called `Gen.empty`, which generates no values and returns nothing. We can think of that as a generator of empty stream, `Gen(Stream.empty)`.

## Generating from Fixed Values

1. `Gen.elements` — Constructs a non-deterministic generator that only generates randomly from the fixed values:

```scala mdoc:compile-only
import java.time._

Gen.elements(
  DayOfWeek.MONDAY,
  DayOfWeek.TUESDAY,
  DayOfWeek.WEDNESDAY,
  DayOfWeek.THURSDAY,
  DayOfWeek.FRIDAY,
  DayOfWeek.SATURDAY,
  DayOfWeek.SUNDAY
).runCollectN(3).debug
// Sample Output: List(WEDNESDAY, THURSDAY, SUNDAY)
```

2. `Gen.fromIterable` — Constructs a deterministic generator that only generates the specified fixed values:

```scala mdoc:compile-only
Gen.fromIterable(List("red", "green", "blue"))
  .runCollectN(10)
  .debug
// Output: List(red, green, blue, red, green, blue, red, green, blue, red)
```

## Collection Generators

ZIO Test has generators for collection data types such as _sets_, _lists_, _vectors_, _chunks_, and _maps_. These data types share similar APIs. The following example illustrates how the generator of sets works:

```scala mdoc:compile-only
// A sized generator of sets
Gen.setOf(Gen.alphaChar)
// Sample Output: Set(Y, M, c), Set(), Set(g, x, Q), Set(s), Set(f, J, b, R)

// A sized generator of non-empty sets
Gen.setOf1(Gen.alphaChar)  
// Sample Output: Set(Y), Set(L, S), Set(i), Set(H), Set(r, Z, z)

// A generator of sets whose size falls within the specified bounds.
Gen.setOfBounded(1, 3)(Gen.alphaChar)
// Sample Output: Set(Q), Set(q, J), Set(V, t, h), Set(c), Set(X, O)

// A generator of sets of the specified size.
Gen.setOfN(2)(Gen.alphaChar)
// Sample Output: Set(J, u), Set(u, p), Set(i, m), Set(b, N), Set(B, Z)
```

## Bounded Generator

The `Gen.bounded` constructor is a generator whose size falls within the specified bounds:

```scala mdoc:compile-only
Gen.bounded(2, 5)(Gen.stringN(_)(Gen.alphaChar))
  .runCollectN(5)
// Sample Output: List(jU, tqwQ, VBTlQ, lH, JAH)
```

## Suspended Generator

The `Gen.suspend` constructs a generator lazily. This is useful to avoid infinite recursion when creating generators that refer to themselves.

## Unfold Generator

The `unfoldGen` takes the initial state and depending on the previous state, it determines what will be the next generated value:

```scala
def unfoldGen[R, S, A](s: S)(f: S => Gen[R, (S, A)]): Gen[R, List[A]]
```

Assume we want to test the built-in scala stack (`scala.collection.mutable.Stack`). One way to do that is to create an acceptable series of push and pop commands, and then check that the stack doesn't throw any exception by executing these commands:

```scala mdoc:silent
sealed trait Command
case object Pop                    extends Command
final case class Push(value: Char) extends Command

val genPop:  Gen[Any, Command]    = Gen.const(Pop)
def genPush: Gen[Any, Command] = Gen.alphaChar.map(Push)

val genCommands: Gen[Any, List[Command]] =
  Gen.unfoldGen(0) { n =>
    if (n <= 0)
      genPush.map(command => (n + 1, command))
    else
      Gen.oneOf(
        genPop.map(command => (n - 1, command)),
        genPush.map(command => (n + 1, command))
      )
  }
```

We are now ready to test the generated list of commands:

```scala mdoc:compile-only
import zio.test.{ test, _ }

test("unfoldGen") {
  check(genCommands) { commands =>
    val stack = scala.collection.mutable.Stack.empty[Int]
    commands.foreach {
      case Pop => stack.pop()
      case Push(value) => stack.push(value)
    }
    assertCompletes
  }
}
```

## From a ZIO Effect

1. `Gen.fromZIO`

  ```scala mdoc:compile-only
  val gen: Gen[Any, Int] = Gen.fromZIO(Random.nextInt) 
  ```

2. `Gen.fromZIOSample`

  ```scala mdoc:compile-only
  val gen: Gen[Any, Int] =
    Gen.fromZIOSample(
      Random.nextInt.map(Sample.shrinkIntegral(0))
    )
  ```

## From a Random Effect

3. `Gen.fromRandom` — Constructs a generator from a function that uses randomness:

  ```scala mdoc:compile-only
  val gen: Gen[Any, Int] = Gen.fromRandom(_.nextInt) 
  ```

4. `Gen.fromRandomSample` — Constructs a generator from a function that uses randomness to produce a sample:

  ```scala mdoc:compile-only
  val gen: Gen[Any, Int] =
    Gen.fromRandomSample(
      _.nextIntBounded(20).map(Sample.shrinkIntegral(0))
    )
  ```

## Uniform and Non-uniform Generators

1. `Gen.uniform` — A generator of uniformly distributed doubles between [0, 1].

2. `Gen.weighted` — A generator which chooses one of the given generators according to their weights. For example, the following generator will generate 90% true and 10% false values:

  ```scala mdoc:compile-only
  val trueFalse = Gen.weighted((Gen.const(true), 9), (Gen.const(false), 1))
  trueFalse.runCollectN(10).debug
  // Sample Output: List(false, false, false, false, false, false, false, false, true, false)
  ```

3. `Gen.exponential` — A generator of exponentially distributed doubles with mean `1`:

  ```scala mdoc:compile-only
  Gen.exponential.map(x => math.round(x * 100) / 100.0)
    .runCollectN(10)
    .debug
  // Sample Output: List(0.22, 3.02, 1.96, 1.13, 0.81, 0.92, 1.7, 1.47, 1.55, 0.46)
  ```

## Generating Date/Time Types

| Date/Time Types            | Generators           |
|----------------------------|----------------------|
| `java.time.DayOfWeek`      | `Gen.dayOfWeek`      |
| `java.time.Month`          | `Gen.month`          |
| `java.time.Year`           | `Gen.year`           |
| `java.time.Instant`        | `Gen.instant`        |
| `java.time.MonthDay`       | `Gen.monthDay`       |
| `java.time.YearMonth`      | `Gen.yearMonth`      |
| `java.time.ZoneId`         | `Gen.zoneId`         |
| `java.time.ZoneOffset`     | `Gen.zoneOffset`     |
| `java.time.ZonedDateTime`  | `Gen.zonedDateTime`  |
| `java.time.OffsetTime`     | `Gen.offsetTime`     |
| `java.time.OffsetDateTime` | `Gen.offsetDateTime` |
| `java.time.Period`         | `Gen.period`         |
| `java.time.LocalDate`      | `Gen.localDate`      |
| `java.time.LocalDateTime`  | `Gen.localDateTime`  |
| `java.time.LocalTime`      | `Gen.localTime`      |
| `zio.duration.Duration`    | `Gen.finiteDuration` |

## Function Generators

To test some properties, we need to generate functions. There are two types of function generators:

1. `Gen.function` — It takes a generator of type `B` and produces a generator of functions from `A` to `B`:

  ```scala
  def function[R, A, B](gen: Gen[R, B]): Gen[R, A => B]
  ```

Two `A` values will be considered to be equal, and thus will be guaranteed to generate the same `B` value, if they have the same
`hashCode`.

2. `Gen.functionWith` — It takes a generator of type `B` and also a hash function for `A` values, and produces a generator of functions from `A` to `B`:

  ```scala
  def functionWith[R, A, B](gen: Gen[R, B])(hash: A => Int): Gen[R, A => B]
  ```

Two `A` values will be considered to be equal, and thus will be guaranteed to generate the same `B` value, if they have the same hash. This is useful when `A` does not implement `hashCode` in a way that is consistent with equality.

Accordingly, ZIO Test provides a variety of function generators for `Function2`, `Function3`, ..., and also the `PartialFunction`:

* `Gen.function2` —  Gen[R, C] => Gen[R, (A, B) => C]
* `Gen.functionWith2` — Gen[R, B] => ((A, B) => Int) => Gen[R, (A, B) => C]
* `Gen.partialFunction` — Gen[R, B] => Gen[R, PartialFunction[A, B]]
* `Gen.partialFunctionWith` — Gen[R, B] => (A => Int) => Gen[R, PartialFunction[A, B]]

Let's write a test for `ZIO.foldLeft` operator. This operator has the following signature:

```scala
def foldLeft[R, E, S, A](in: => Iterable[A])(zero: => S)(f: (S, A) => ZIO[R, E, S]): ZIO[R, E, S]
```

We want to test the following property:

```scala
∀ (in, zero, f) => ZIO.foldLeft(in)(zero)(f) == ZIO(List.foldLeft(in)(zero)(f))
```

To test this property, we have an input of type `(Int, Int) => Int`. So we need a Function2 generator of integers:

```scala mdoc:silent
val func2: Gen[Any, (Int, Int) => Int] = Gen.function2(Gen.int)
```

Now we can test this property:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("ZIO.foldLeft should have the same result with List.foldLeft") {
  check(Gen.listOf(Gen.int), Gen.int, func2) { case (in, zero, f) =>
    assertZIO(
      ZIO.foldLeft(in)(zero)((s, a) => ZIO.attempt(f(s, a)))
    )(Assertion.equalTo(
      in.foldLeft(zero)((s, a) => f(s, a)))
    )
  }
}
```

## Generating ZIO Values

1. Successful effects (`Gen.successes`):

  ```scala mdoc:compile-only
  val gen: Gen[Any, UIO[Int]] = Gen.successes(Gen.int(-10, 10))
  ```

2. Failed effects (`Gen.failures`):

  ```scala mdoc:compile-only
  val gen: Gen[Any, IO[String, Nothing]] = Gen.failures(Gen.string)
  ```

3. Died effects (`Gen.died`):

  ```scala mdoc:compile-only
  val gen: Gen[Any, UIO[Nothing]] = Gen.died(Gen.throwable)
  ```

4. Cause values (`Gen.causes`):

  ```scala mdoc:compile-only
  val causes: Gen[Any, Cause[String]] = 
    Gen.causes(Gen.string, Gen.throwable)
  ```

5. Chained effects (`Gen.chined`, `Gen.chainedN`): A generator of effects that are the result of chaining the specified effect with itself a random number of times.

Let's see some example of chained ZIO effects:

  ```scala mdoc:compile-only
  import zio._
  val effect1 = ZIO(2).flatMap(x => ZIO(x * 2))
  val effect2 = ZIO(1) *> ZIO(2)
  ```

By using `Gen.chaned` or `Gen.chanedN` generator, we can create generators of chained effects:

  ```scala mdoc:compile-only
  val chained : Gen[Any, ZIO[Any, Nothing, Int]] = 
    Gen.chained(Gen.successes(Gen.int))
    
  val chainedN: Gen[Any, ZIO[Any, Nothing, Int]] = 
    Gen.chainedN(5)(Gen.successes(Gen.int))
  ```

6. Concurrent effects (`Gen.concurrent`): A generator of effects that are the result of applying concurrency combinators to the specified effect that are guaranteed not to change its value.

  ```scala mdoc:compile-only
  val random  : Gen[Any, UIO[Int]] = Gen.successes(Gen.int).flatMap(Gen.concurrent)
  val constant: Gen[Any, UIO[Int]]    = Gen.concurrent(ZIO(3))
  ```

7. Parallel effects (`Gen.parallel`): A generator of effects that are the result of applying parallelism combinators to the specified effect that are guaranteed not to change its value.

  ```scala mdoc:compile-only
  val random: Gen[Any, UIO[String]] =
    Gen.successes(Gen.string).flatMap(Gen.parallel)
    
  val constant: Gen[Any, UIO[String]] =
    Gen.parallel(ZIO("Hello"))
  ```

## Generating Compound Types

1. tuples — We can combine generators using for-comprehension syntax and tuples:

  ```scala mdoc:compile-only
  val tuples: Gen[Any, (Int, Double)] =
    for {
      a <- Gen.int
      b <- Gen.double
    } yield (a, b)
  ```

2. `Gen.oneOf` — It takes variable number of generators and select one of them:

  ```scala mdoc:compile-only
  sealed trait Color
  case object Red extends Color
  case object Blue extends Color
  case object Green extends Color
  
  Gen.oneOf(Gen.const(Red), Gen.const(Blue), Gen.const(Green))
  // Sample Output: Green, Green, Red, Green, Red
  ```

4. `Gen.option` — A generator of _optional_ values:

  ```scala mdoc:compile-only
  val intOptions: Gen[Any, Option[Int]] = Gen.option(Gen.int)
  val someInts:   Gen[Any, Option[Int]] = Gen.some(Gen.int)
  val nons:       Gen[Any, Option[Nothing]]     = Gen.none
  ```

3. `Gen.either` — A generator of _either_ values:

  ```scala mdoc:compile-only
  val char: Gen[Any, Either[Char, Char]] =
    Gen.either(Gen.numericChar, Gen.alphaChar)
  ```

4. `Gen.collectAll` — Composes the specified generators to create a _cartesian product of elements_ with the specified function:

  ```scala mdoc:compile-only
  val gen: ZIO[Any, Nothing, List[List[Int]]] =
    Gen.collectAll(
      List(
        Gen.fromIterable(List(1, 2)),
        Gen.fromIterable(List(3)),
        Gen.fromIterable(List(4, 5))
      )
    ).runCollect
  // Output:
  // List(
  //  List(1, 3, 4),
  //  List(1, 3, 5),
  //  List(2, 3, 4),
  //  List(2, 3, 5)
  //)
  ```

5. `Gen.concatAll` — Combines the specified deterministic generators to return a new deterministic generator that generates all the values generated by the specified generators:

  ```scala mdoc:compile-only
  val gen: ZIO[Any, Nothing, List[Int]] =
    Gen.concatAll(
      List(
        Gen.fromIterable(List(1, 2)),
        Gen.fromIterable(List(3)),
        Gen.fromIterable(List(4, 5))
      )
    ).runCollect
  // Output: List(1, 2, 3, 4, 5)
  ```

## Sized Generators

1. `Gen.sized` — A sized generator takes a function from `Int` to `Gen[R, A]` and creates a generator by applying a size to that function:

  ```scala mdoc:compile-only
  Gen.sized(Gen.int(0, _))
    .runCollectN(10)
    .provideCustomLayer(Sized.live(5))
    .debug
  // Sample Output: List(5, 4, 1, 2, 0, 4, 2, 0, 1, 2)
  ```

2. `Gen.size` — A generator which accesses the _size_ from the environment and generates that:

  ```scala mdoc:compile-only
  Gen.size
    .runCollectN(5)
    .provideCustomLayer(Sized.live(100))
    .debug
  // Output: List(100, 100, 100, 100, 100)
  ```

There are also three sized generators, named _small_, _medium_ and _large_, that use an exponential distribution of size values:

1. `Gen.small` — The values generated will be strongly concentrated towards the lower end of the range but a few larger values will still be generated:

  ```scala mdoc:compile-only
  Gen.small(Gen.const(_))
    .runCollectN(10)
    .provideCustomLayer(Sized.live(1000))
    .debug
  // Output: List(6, 39, 73, 3, 57, 51, 40, 12, 110, 46)
  ```

4. `Gen.medium` — The majority of sizes will be towards the lower end of the range but some larger sizes will be generated as well:

  ```scala mdoc:compile-only
  Gen.medium(Gen.const(_))
    .runCollectN(10)
    .provideCustomLayer(Sized.live(1000))
    .debug
  // Output: List(93, 42, 58, 228, 42, 5, 12, 214, 106, 79)
  ```

5. `Gen.large` — Uses a uniform distribution of size values. A large number of larger sizes will be generated:

  ```scala mdoc:compile-only
  Gen.large(Gen.const(_))
    .runCollectN(10)
    .provideCustomLayer(Sized.live(1000))
    .debug
  // Output: List(797, 218, 596, 278, 301, 779, 165, 486, 695, 788)
  ```
