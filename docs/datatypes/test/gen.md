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

```scala mdoc:invisible
import zio._
import zio.test._
```

## Creating a Generator

In the companion object of the `Gen` data type, there are tons of generators for various data types.

### Generating Primitive Types

* `Gen.int` — e.g:  -1, 2, 59, 123, 0, -11323, 4, -425084233, ...
* `Gen.string` — e.g: "3r%~9", "", "d", "3A34", ...
* `Gen.string1`
* `Gen.boolean` — true, false, false, true, true, true, ...
* `Gen.float` 
* `Gen.double`
* `Gen.bigInt`
* `Gen.byte`
* `Gen.bigdecimal`
* `Gen.long`
* `Gen.char`
* `Gen.short`

Let's create an `Int` generator:

```scala mdoc:silent:nest
import zio._
import zio.test._

val intGen: Gen[Has[Random], Int] = Gen.int
```

### Character Generators

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

### String Generators

1. `Gen.stringBounded` — A generator of strings whose size falls within the specified bounds:

```scala mdoc:compile-only
Gen.stringBounded(1, 5)(Gen.alphaChar)
  .runCollectN(10)
  .debug
// Sample Ouput: List(b, YJXzY, Aro, y, WMPbj, Abxt, kJep, LKN, kUtr, xJ)
```

2. `Gen.stringN` — A generator of strings of fixed size:

```scala mdoc:compile-only
Gen.stringN(5)(Gen.alphaChar)
  .runCollectN(10)
  .debug
// Sample Output: List(BuywQ, tXCEy, twZli, ffLwI, BPEbz, OKYTi, xeDJW, iDUVn, cuMCr, keQAA)
```

Here are some other string generators:

* `Gen.string1` — A generator of strings of at least one character.
* `Gen.iso_8859_1` — A generator of strings that can be encoded in the ISO-8859-1 character set.
* `Gen.asciiString` — A generator of US-ASCII characters.
* `Gen.alphaNumericString` — A generator of alphanumeric characters.
* `Gen.alphaNumericStringBounded` — A generator of alphanumeric strings whose size falls within the specified bounds.

### Collection Types

* `Gen.setOf`
* `Gen.setOf1`
* `Gen.setOfBounded`
* `Gen.setOfN`
* `Gen.vectorOf`
* `Gen.vectorOf1`
* `Gen.vectorOfBounded`
* `Gen.vectorOfN`
* `Gen.mapOf`
* `Gen.mapOf1`
* `Gen.mapOfN`
* `Gen.mapOfBounded`

### PartialFunction Types

* `Gen.partialFunction`
* `Gen.partialFunctionWith`

### Deterministic Generators

* `Gen.empty`
* `Gen.elements`
* `Gen.fromIterable`

### Suspended Generator

* `Gen.suspend` — Lazily constructs a generator. This is useful to avoid infinite recursion when creating generators that refer to themselves.

### Unfold Generator

* `Gen.unfoldGen`
* `Gen.unfoldGenN`

### From Constant Values

1. `Gen.constSample`

  ```scala mdoc:compile-only
  val gen: Gen[Any, Int] = Gen.constSample(Sample.noShrink(5))
  ```
  
2. `Gen.fromZIOSample`

   ```scala mdoc:compile-only
   val gen: Gen[Has[Random], Int] =
     Gen.fromZIOSample(
       Random.nextInt.map(Sample.shrinkIntegral(0))
     )
   ```
   
3. `Gen.fromRandomSample`

  ```scala mdoc:compile-only
  val gen: Gen[Has[Random], Int] =
    Gen.fromRandomSample(
      _.nextIntBounded(20).map(Sample.shrinkIntegral(0))
    )
  ```

4. `Gen.fromRandom`

  ```scala mdoc:compile-only
  val gen: Gen[Has[Random], Int] = Gen.fromRandom(_.nextInt) 
  ```
  
5. `Gen.fromZIO`

   ```scala mdoc:compile-only
   val gen: Gen[Has[Random], Int] = Gen.fromZIO(Random.nextInt) 
   ```

### Uniform and Non-uniform Generators

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

### Specialized Types

* `Gen.oneOf`
* `Gen.throwable`
* `Gen.uniform`
* `Gen.unit`
* `Gen.uuid`
* `Gen.option`
* `Gen.some`
* `Gen.none`

### Others

* `Gen.bounded`
* `Gen.chunkOf`
* `Gen.chunkOf1`
* `Gen.chunkOfBounded`
* `Gen.chunkOfN`
* `Gen.collectAll`
* `Gen.concatAll`
* `Gen.const`
* `Gen.listOf`
* `Gen.listOf1`
* `Gen.listOfBounded`
* `Gen.listOfN` 

### Generating Date/Time Types

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

### Generating Functions

* `Gen.function` — Gen[R, B] => Gen[R, A => B]
* `Gen.function2` — Gen[R, C] => Gen[R, (A, B) => C]
* `Gen.functionWith` — Gen[R, B] => (A => Int) => Gen[R, A => B]
* `Gen.functionWith` — Gen[R, B] => ((A, B) => Int) => Gen[R, (A, B) => C]

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
val func2: Gen[Has[Random], (Int, Int) => Int] = Gen.function2(Gen.int)
```

Now we can test this property:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("ZIO.foldLeft should have the same result with List.foldLeft") {
  check(Gen.listOf(Gen.int), Gen.int, func2) { case (in, zero, f) =>
    assertM(
      ZIO.foldLeft(in)(zero)((s, a) => ZIO(f(s, a)))
    )(Assertion.equalTo(
      in.foldLeft(zero)((s, a) => f(s, a)))
    )
  }
}
```

### Generating ZIO Values

1. Successful effects (`Gen.successes`):

  ```scala mdoc:compile-only
  val gen: Gen[Has[Random], UIO[Int]] = Gen.successes(Gen.int(-10, 10))
  ```

2. Failed effects (`Gen.failures`):

  ```scala mdoc:compile-only
  val gen: Gen[Has[Random] with Has[Sized], IO[String, Nothing]] = Gen.failures(Gen.string)
  ```

3. Died effects (`Gen.died`):

  ```scala mdoc:compile-only
  val gen: Gen[Has[Random], UIO[Nothing]] = Gen.died(Gen.throwable)
  ```

4. Cause values (`Gen.causes`):

  ```scala mdoc:compile-only
  val causes: Gen[Has[Random] with Has[Sized], Cause[String]] = 
    Gen.causes(Gen.string, Gen.throwable)
  ```
5. Chained effects (`Gen.chined`, `Gen.chainedN`): A generator of effects that are the result of chaining the specified effect with itself a random number of times.

  Let's see some example of chained ZIO effects:

  ```scala
  val effect1 = ZIO(2).flatMap(x => ZIO(x * 2))
  val effect2 = ZIO(1) *> ZIO(2)
  ```

  By using `Gen.chaned` or `Gen.chanedN` generator, we can create generators of chained effects:

  ```scala mdoc:compile-only
  val chained : Gen[Has[Random] with Has[Sized], ZIO[Any, Nothing, Int]] = 
    Gen.chained(Gen.successes(Gen.int))
    
  val chainedN: Gen[Has[Random], ZIO[Any, Nothing, Int]] = 
    Gen.chainedN(5)(Gen.successes(Gen.int))
  ```

6. Concurrent effects (`Gen.concurrent`): A generator of effects that are the result of applying concurrency combinators to the specified effect that are guaranteed not to change its value.

  ```scala mdoc:compile-only
  val random : Gen[Has[Random], UIO[Int]] = Gen.successes(Gen.int).flatMap(Gen.concurrent)
  val constant: Gen[Any, UIO[Int]]         = Gen.concurrent(ZIO(3))
  ```
  
7. Parallel effects (`Gen.parallel`): A generator of effects that are the result of applying parallelism combinators to the specified effect that are guaranteed not to change its value.

  ```scala mdoc:compile-only
  val random: Gen[Has[Random] with Has[Sized], UIO[String]] =
    Gen.successes(Gen.string).flatMap(Gen.parallel)
    
  val constant: Gen[Any, UIO[String]] =
    Gen.parallel(ZIO("Hello"))
  ```
  
### Generating Compound Types

1. tuples

```scala mdoc:compile-only
val tuples: Gen[Has[Random], (Int, Int)] =
  for {
    a <- Gen.int
    b <- Gen.int
  } yield (a, b)
```

2. options

```scala mdoc:compile-only
val intOptions: Gen[Has[Random], Option[Int]] =
  Gen.option(Gen.int)
```

3. either

```scala mdoc:compile-only
val char: Gen[Has[Random], Either[Char, Char]] =
  Gen.either(Gen.numericChar, Gen.alphaChar)
```

5. lists

```scala mdoc:compile-only
val listOfInts: Gen[Has[Random] with Has[Sized], List[Int]] =
  Gen.listOf(Gen.int)
```

4. Gen.chunkOf

### Sized Generators

1. `Gen.sized`

```scala mdoc:compile-only
Gen.sized(Gen.int(0, _))
  .runCollectN(10)
  .provideCustomLayer(Sized.live(5))
  .debug
// Output: List(5, 4, 1, 2, 0, 4, 2, 0, 1, 2)
```

2. `Gen.size`

```scala mdoc:compile-only
Gen.size.flatMap(Gen.int(0, _))
  .runCollectN(10)
  .provideCustomLayer(Sized.live(5))
  .debug
// Output: List(3, 1, 4, 0, 4, 3, 1, 1, 5, 5)
```

3. `Gen.small`

```scala mdoc:compile-only
Gen.small(Gen.const(_))
        .runCollectN(10)
        .provideCustomLayer(Sized.live(1000))
        .debug
// Output: List(6, 39, 73, 3, 57, 51, 40, 12, 110, 46)
```

4. `Gen.medium`

```scala mdoc:compile-only
Gen.medium(Gen.const(_))
        .runCollectN(10)
        .provideCustomLayer(Sized.live(1000))
        .debug
// Output: List(93, 42, 58, 228, 42, 5, 12, 214, 106, 79)
```

5. `Gen.large`

```scala mdoc:compile-only
Gen.large(Gen.const(_))
  .runCollectN(10)
  .provideCustomLayer(Sized.live(1000))
  .debug
// Ouput: List(797, 218, 596, 278, 301, 779, 165, 486, 695, 788)
```

## Running a Generator

To run a generator, we can call `runCollect` operation:

```scala mdoc:nest
val ints: ZIO[Has[Random], Nothing, List[Int]] = intGen.runCollect.debug
// Output: List(-2090696713)
```

This will return a `ZIO` effect containing all its values in a list, which in this example it contains only one element.

To create more samples, we can use `Gen#runCollectN`, which repeatedly runs the generator as much as we need. In this example, it will generate a list of containing 5 integer elements:

```scala mdoc:compile-only
intGen.runCollectN(5).debug
// Output: List(281023690, -1852531706, -21674662, 187993034, -868811035)
```

In addition, there is an operator called `Gen#runHead`, which returns the first value generated by the generator.

## Shrinking

In Property-Based Testing, we specify certain properties of a program, then we ask the testing framework to generate random test data to discover counterexamples. The existence of counterexamples shows that our function, which is under the test, is not correct. Unfortunately, in almost all cases, the first counterexample is not the minimal one, and they are fairly large or complex. So it is not a pretty good sample to describe why our test is failing.

Shrinking is a mechanism that tries to find the smallest counterexample, which is the root cause of the test failure. So it helps a developer to find out why the test is failing.

Finding the smallest failing case is somehow cumbersome and requires many attempts. As a developer, we do not need to do shrinking ourselves. All generators in ZIO Test have built-in shrinkers, so when we test properties, in case of test failures, the ZIO Test attempts to reduce the counterexamples forward their own zero points.

Let's write a `reverse` function with an incorrect implementation:

```scala mdoc:silent
def reverse[T](list: List[T]): List[T] =
  if (list.length > 6) list.reverse.dropRight(1) else list.reverse
```

We know that if we reverse a list twice, it should give us the original list, so let's check this property:

```scala mdoc:compile-only
import zio.test._

suite("ReverseSpec"){
  // ∀ xs. reverse(reverse(xs)) == xs
  test("reversing a list twice must give the original list")(
    check(Gen.listOf(Gen.int)) { list =>
      assertTrue(reverse(reverse(list)) == list)
    }
  )
}
```

The following messages, is a sample output of the test renderer, after running the test:

```
- ReverseSpec
  - reversing a list twice must give the original list
    Test failed after 7 iterations with input: List(0, 0, 0, 0, 0, 0, 0)
    Original input before shrinking was: List(724856966, 1976458409, -940069360, -191508820, -291932258, 1296893186, 2010410723, 1134770522, 1260002835)
    ✗ List(0, 0, 0, 0, 0, 0) was not equal to List(0, 0, 0, 0, 0, 0, 0)
    reverse(reverse(list)) == list
    reverse(reverse(list)) = List(0, 0, 0, 0, 0, 0)
```

The initial failing input discovered by ZIO Test is `List(724856966, 1976458409, -940069360, -191508820, -291932258, 1296893186, 2010410723, 1134770522, 1260002835)`. The ZIO Test then tries to find the simplest counterexample which is `List(0, 0, 0, 0, 0, 0, 0)`. So the property still fails with the final shrunk value. The original input is a list of 9 somewhat useless numbers, while after shrinking, we have a list of 7 zero numbers, so we can find the bug faster.
