/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.test

import zio.Random._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.ZStream
import zio.{Chunk, NonEmptyChunk, Random, Trace, UIO, URIO, ZIO, Zippable}

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.collection.immutable.SortedMap
import scala.jdk.CollectionConverters._
import scala.math.Numeric.DoubleIsFractional

/**
 * A `Gen[R, A]` generates values of type `A` for property-based tests, using
 * an environment `R`. Each value comes as a [[Sample]] that also carries a
 * tree of simpler values, which `check` uses to shrink a failing input.
 *
 * A generator is a stream of samples. A random generator such as `Gen.int`
 * produces one sample each time it runs, and `check` runs it repeatedly. A
 * deterministic generator such as `Gen.fromIterable` produces a fixed sequence
 * of samples, and `checkAll` tests each of them.
 *
 * {{{
 * import zio._
 *
 * final case class User(id: Int, name: String)
 *
 * val users: Gen[Any, User] =
 *   for {
 *     id   <- Gen.int(1, 1000)
 *     name <- Gen.alphaNumericStringBounded(1, 8)
 *   } yield User(id, name)
 *
 * val threeUsers: UIO[List[User]] = users.runCollectN(3)
 * }}}
 *
 * @see [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 * @see [[https://zio.dev/reference/test/property-testing/built-in-generators/ Built-in Generators]]
 * @see [[https://zio.dev/reference/test/property-testing/how-generators-work/ How Generators Work]]
 * @see [[https://zio.dev/reference/test/property-testing/shrinking/ Shrinking]]
 */
final case class Gen[-R, +A](sample: ZStream[R, Nothing, Sample[R, A]]) { self =>

  /**
   * Concatenates this generator with `that` generator. A symbolic alias for
   * `concat`.
   */
  def ++[R1 <: R, A1 >: A](that: Gen[R1, A1])(implicit trace: Trace): Gen[R1, A1] =
    self.concat(that)

  /**
   * Combines this generator with `that` generator into a generator of pairs. A
   * symbolic alias for `zip`.
   */
  def <*>[R1 <: R, B](
    that: Gen[R1, B]
  )(implicit zippable: Zippable[A, B], trace: Trace): Gen[R1, zippable.Out] =
    self.zip(that)

  /**
   * Returns a generator that generates the values of this generator and then
   * the values of `that` generator. For two deterministic generators the
   * result is a deterministic generator of both sequences; two random
   * generators give one value from each.
   */
  def concat[R1 <: R, A1 >: A](that: Gen[R1, A1])(implicit trace: Trace): Gen[R1, A1] =
    Gen(self.sample ++ that.sample)

  /**
   * Maps the values produced by this generator with the specified partial
   * function, discarding any values the partial function is not defined at.
   */
  def collect[B](pf: PartialFunction[A, B])(implicit trace: Trace): Gen[R, B] =
    self.flatMap { a =>
      pf.andThen(Gen.const(_)).applyOrElse[A, Gen[Any, B]](a, _ => Gen.empty)
    }

  /**
   * Filters the values produced by this generator, discarding any values that
   * do not meet the specified predicate. Shrinks that do not meet the
   * predicate are discarded too. Using `filter` can reduce test performance,
   * especially if many values must be discarded. It is recommended to use
   * combinators such as `map` and `flatMap` to create generators of the
   * desired values instead, as in this generator of even numbers:
   *
   * {{{
   * val evens: Gen[Any, Int] = Gen.int.map(_ * 2)
   * }}}
   */
  def filter(f: A => Boolean)(implicit trace: Trace): Gen[R, A] =
    self.flatMap(a => if (f(a)) Gen.const(a) else Gen.empty)

  /**
   * Filters the values produced by this generator, discarding any values that
   * do not meet the specified effectual predicate. Using `filterZIO` can reduce
   * test performance, especially if many values must be discarded. It is
   * recommended to use combinators such as `map` and `flatMap` to create
   * generators of the desired values instead, as in this generator of even
   * numbers:
   *
   * {{{
   * val evens: Gen[Any, Int] = Gen.int.map(_ * 2)
   * }}}
   */
  def filterZIO[R1 <: R](f: A => ZIO[R1, Nothing, Boolean])(implicit trace: Trace): Gen[R1, A] =
    self.flatMap(a => Gen.fromZIO(f(a)).flatMap(p => if (p) Gen.const(a) else Gen.empty))

  /**
   * Filters the values produced by this generator, discarding any values that
   * meet the specified predicate.
   */
  def filterNot(f: A => Boolean)(implicit trace: Trace): Gen[R, A] =
    filter(a => !f(a))

  /**
   * The same as `filter`. A `for` comprehension over generators calls it for
   * an `if` guard.
   */
  def withFilter(f: A => Boolean)(implicit trace: Trace): Gen[R, A] = filter(f)

  /**
   * Returns a generator that uses each value of this generator to choose the
   * generator `f` returns, and generates its values. Shrinking first tries the
   * shrinks of the outer value, then the shrinks of the inner value.
   *
   * {{{
   * // A list whose length is between 1 and 5.
   * val lists: Gen[Any, List[Int]] =
   *   Gen.int(1, 5).flatMap(n => Gen.listOfN(n)(Gen.int(0, 9)))
   * }}}
   */
  def flatMap[R1 <: R, B](f: A => Gen[R1, B])(implicit trace: Trace): Gen[R1, B] =
    Gen {
      self.sample.flatMap { sample =>
        val values  = f(sample.value).sample
        val shrinks = Gen(sample.shrink).flatMap(f).sample
        values.map(_.flatMap(Sample(_, shrinks)))
      }
    }

  /** Flattens a generator of generators into a generator of their values. */
  def flatten[R1 <: R, B](implicit ev: A <:< Gen[R1, B], trace: Trace): Gen[R1, B] =
    flatMap(ev)

  /**
   * Applies `f` to each value of this generator. The shrinks are mapped with
   * `f` as well.
   *
   * {{{
   * val evens: Gen[Any, Int] = Gen.int(0, 50).map(_ * 2)
   * }}}
   */
  def map[B](f: A => B)(implicit trace: Trace): Gen[R, B] =
    Gen(sample.map(_.map(f)))

  /**
   * Maps an effectual function over a generator. The function is applied to
   * the shrinks as well.
   */
  def mapZIO[R1 <: R, B](f: A => ZIO[R1, Nothing, B])(implicit trace: Trace): Gen[R1, B] =
    Gen(sample.mapZIO(_.foreach(f)))

  /**
   * Discards the shrinker for this generator, so a failing value is reported
   * as it was generated.
   */
  def noShrink(implicit trace: Trace): Gen[R, A] =
    reshrink(Sample.noShrink)

  /**
   * Discards the shrinker for this generator and applies a new shrinker by
   * mapping each value to a sample using the specified function. This is useful
   * when the process to shrink a value is simpler than the process used to
   * generate it.
   */
  def reshrink[R1 <: R, B](f: A => Sample[R1, B])(implicit trace: Trace): Gen[R1, B] =
    Gen(sample.map(sample => f(sample.value)))

  /**
   * Sets the size parameter for this generator to the specified value.
   */
  def resize(size: Int)(implicit trace: Trace): Gen[R, A] =
    Sized.withSizeGen(size)(self)

  /**
   * Runs the generator once and collects all of its values in a list. A random
   * generator such as `Gen.int` gives a list of one value.
   */
  def runCollect(implicit trace: Trace): ZIO[R, Nothing, List[A]] =
    sample.map(_.value).runCollect.map(_.toList)

  /**
   * Repeatedly runs the generator and collects the specified number of values
   * in a list. A deterministic generator repeats its sequence. If the
   * generator produces no values, the effect does not complete.
   */
  def runCollectN(n: Int)(implicit trace: Trace): ZIO[R, Nothing, List[A]] =
    sample.map(_.value).forever.take(n.toLong).runCollect.map(_.toList)

  /**
   * Runs the generator and returns its first value, or `None` if it produces
   * no values.
   */
  def runHead(implicit trace: Trace): ZIO[R, Nothing, Option[A]] =
    sample.map(_.value).runHead

  /**
   * Composes this generator with the specified generator to create a cartesian
   * product of elements. Shrinking first tries the shrinks of the first value,
   * then the shrinks of the second.
   */
  def zip[R1 <: R, B](
    that: Gen[R1, B]
  )(implicit zippable: Zippable[A, B], trace: Trace): Gen[R1, zippable.Out] =
    self.zipWith(that)(zippable.zip(_, _))

  /**
   * Composes this generator with the specified generator to create a cartesian
   * product of elements with the specified function.
   */
  def zipWith[R1 <: R, B, C](that: Gen[R1, B])(f: (A, B) => C)(implicit trace: Trace): Gen[R1, C] =
    self.flatMap(a => that.map(b => f(a, b)))
}

/**
 * Constructors of [[Gen]] values: generators of numbers, characters, strings
 * and collections, of `java.time` values, of functions and of effects.
 *
 * The generators of numbers, characters and collections that take a `min` and
 * a `max` (`int(min, max)`, `listOfBounded(min, max)`, ...) die with an
 * `IllegalArgumentException` when `min` is greater than `max`.
 * A sized generator (`listOf`, `string`, `small`, ...) reads the size of the
 * [[Sized]] service, which is 100 in the test environment.
 */
object Gen extends GenZIO with FunctionVariants with TimeVariants {

  /**
   * A generator of ASCII letters (`A` to `Z` and `a` to `z`), each with the
   * same probability. Shrinks toward `'A'`.
   */
  def alphaChar(implicit trace: Trace): Gen[Any, Char] =
    weighted(char(65, 90) -> 26, char(97, 122) -> 26)

  /**
   * A generator of ASCII digits and letters, each with the same probability.
   * Shrinks toward `'0'`.
   */
  def alphaNumericChar(implicit trace: Trace): Gen[Any, Char] =
    weighted(char(48, 57) -> 10, char(65, 90) -> 26, char(97, 122) -> 26)

  /**
   * A generator of alphanumeric strings. Shrinks towards the empty string.
   */
  def alphaNumericString(implicit trace: Trace): Gen[Any, String] =
    Gen.string(alphaNumericChar)

  /**
   * A generator of alphanumeric strings whose length is between `min` and
   * `max`, both inclusive. Shrinks toward a string of `min` `'0'` characters.
   */
  def alphaNumericStringBounded(min: Int, max: Int)(implicit
    trace: Trace
  ): Gen[Any, String] =
    Gen.stringBounded(min, max)(alphaNumericChar)

  /**
   * A generator of US-ASCII characters, from code 0 to code 127. Shrinks toward
   * the character with code 0.
   */
  def asciiChar(implicit trace: Trace): Gen[Any, Char] =
    Gen.oneOf(Gen.char('\u0000', '\u007F'))

  /**
   * A sized generator of US-ASCII strings. Shrinks towards the empty string.
   */
  def asciiString(implicit trace: Trace): Gen[Any, String] =
    Gen.string(Gen.asciiChar)

  /**
   * A generator of big decimals between `min` and `max`, both inclusive. The
   * shrinker will shrink toward `min`.
   *
   * The values are `min` plus a multiple of the smallest step that the scale
   * of `max - min` allows: for `min = 0.25` and `max = 1.5` the difference
   * `1.25` has two decimal places, so the values are `0.25`, `0.26`, ...,
   * `1.50`.
   */
  def bigDecimal(min: BigDecimal, max: BigDecimal)(implicit trace: Trace): Gen[Any, BigDecimal] =
    if (min > max)
      Gen.fromZIO(ZIO.die(new IllegalArgumentException("invalid bounds")))
    else {
      val difference = max - min
      val decimals   = difference.scale max 0
      val bigInt     = (difference * BigDecimal(10).pow(decimals)).toBigInt
      Gen.bigInt(0, bigInt).map(bigInt => min + BigDecimal(bigInt) / BigDecimal(10).pow(decimals))
    }

  /**
   * A generator of [[java.math.BigDecimal]] values between `min` and `max`,
   * both inclusive. The shrinker will shrink toward `min`.
   *
   * The values are `min` plus a multiple of the smallest step that the scale
   * of `max - min` allows, as for [[bigDecimal]].
   */
  def bigDecimalJava(min: BigDecimal, max: BigDecimal)(implicit trace: Trace): Gen[Any, java.math.BigDecimal] =
    Gen.bigDecimal(min, max).map(_.underlying)

  /**
   * A generator of big integers between `min` and `max`, both inclusive. The
   * shrinker will shrink toward `min`. When `min` equals `max`, it generates
   * that value with no shrinking.
   */
  def bigInt(min: BigInt, max: BigInt)(implicit trace: Trace): Gen[Any, BigInt] =
    Gen.fromZIOSample {
      if (min > max) ZIO.die(new IllegalArgumentException("invalid bounds"))
      else if (min == max) ZIO.succeed(Sample.noShrink(min))
      else {
        val bitLength  = (max - min).bitLength
        val byteLength = ((bitLength.toLong + 7) / 8).toInt
        val excessBits = byteLength * 8 - bitLength
        val mask       = (1 << (8 - excessBits)) - 1
        val effect = nextBytes(byteLength).map { bytes =>
          val arr = bytes.toArray
          arr(0) = (arr(0) & mask).toByte
          min + BigInt(arr)
        }.repeatUntil(n => min <= n && n <= max)
        effect.map(Sample.shrinkIntegral(min))
      }
    }

  /**
   * A generator of [[java.math.BigInteger]] values between `min` and `max`,
   * both inclusive. The shrinker will shrink toward `min`.
   *
   * @see
   *   [[bigInt]], which generates the values.
   */
  def bigIntegerJava(min: BigInt, max: BigInt)(implicit trace: Trace): Gen[Any, java.math.BigInteger] =
    Gen.bigInt(min, max).map(_.underlying)

  /**
   * A generator of booleans, `true` and `false` with the same probability.
   * Shrinks toward `false`.
   */
  def boolean(implicit trace: Trace): Gen[Any, Boolean] =
    elements(false, true)

  /**
   * Passes a size between `min` and `max`, both inclusive, to `f` and
   * generates the values of the generator it returns. The size shrinks toward
   * `min`.
   */
  def bounded[R, A](min: Int, max: Int)(f: Int => Gen[R, A])(implicit trace: Trace): Gen[R, A] =
    int(min, max).flatMap(f)

  /**
   * A generator of bytes over the whole `Byte` range. Shrinks toward `0`.
   */
  def byte(implicit trace: Trace): Gen[Any, Byte] =
    fromZIOSample {
      nextIntBounded(Byte.MaxValue - Byte.MinValue + 1)
        .map(r => (Byte.MinValue + r).toByte)
        .map(Sample.shrinkIntegral(0))
    }

  /**
   * A generator of byte values between `min` and `max`, both inclusive. The
   * shrinker will shrink toward `min`.
   */
  def byte(min: Byte, max: Byte)(implicit trace: Trace): Gen[Any, Byte] =
    int(min.toInt, max.toInt).map(_.toByte)

  /**
   * A generator of characters over the whole `Char` range, surrogates
   * included. Shrinks toward the character with code 0.
   */
  def char(implicit trace: Trace): Gen[Any, Char] =
    fromZIOSample {
      nextIntBounded(Char.MaxValue - Char.MinValue + 1)
        .map(r => (Char.MinValue + r).toChar)
        .map(Sample.shrinkIntegral(0))
    }

  /**
   * A generator of character values between `min` and `max`, both inclusive.
   * The shrinker will shrink toward `min`.
   */
  def char(min: Char, max: Char)(implicit trace: Trace): Gen[Any, Char] =
    int(min.toInt, max.toInt).map(_.toChar)

  /**
   * A sized generator of chunks. The length is drawn as in [[small]], and the
   * chunk shrinks toward the empty chunk.
   */
  def chunkOf[R, A](g: Gen[R, A])(implicit trace: Trace): Gen[R, Chunk[A]] =
    small(chunkOfN(_)(g))

  /**
   * A sized generator of non-empty chunks. Shrinks toward a chunk of one
   * element.
   */
  def chunkOf1[R, A](g: Gen[R, A])(implicit
    trace: Trace
  ): Gen[R, NonEmptyChunk[A]] =
    for {
      h <- g
      t <- small(n => chunkOfN(n - 1 max 0)(g))
    } yield NonEmptyChunk(h) ++ t

  /**
   * A generator of chunks whose length is between `min` and `max`, both
   * inclusive. The length shrinks toward `min`.
   */
  def chunkOfBounded[R, A](min: Int, max: Int)(g: Gen[R, A])(implicit
    trace: Trace
  ): Gen[R, Chunk[A]] =
    bounded(min, max)(chunkOfN(_)(g))

  /**
   * A generator of chunks of the specified size. A size of zero or less gives
   * empty chunks.
   */
  def chunkOfN[R, A](n: Int)(g: Gen[R, A])(implicit trace: Trace): Gen[R, Chunk[A]] =
    buildN(n)(g)(Chunk.empty[A])(_ :+ _)(identity)

  /**
   * Composes the specified generators into a generator of lists, with one
   * value from each generator in order. For deterministic generators the
   * result is the cartesian product of their values.
   *
   * {{{
   * // List(List(1, 3), List(1, 4), List(2, 3), List(2, 4))
   * val pairs: Gen[Any, List[Int]] =
   *   Gen.collectAll(List(Gen.fromIterable(List(1, 2)), Gen.fromIterable(List(3, 4))))
   * }}}
   */
  def collectAll[R, A](gens: Iterable[Gen[R, A]])(implicit trace: Trace): Gen[R, List[A]] =
    Gen.suspend {

      def loop(gens: List[Gen[R, A]], as: List[A]): Gen[R, List[A]] =
        gens match {
          case gen :: gens => gen.flatMap(a => loop(gens, a :: as))
          case Nil         => Gen.const(as.reverse)
        }

      loop(gens.toList, Nil)
    }

  /**
   * Combines the specified deterministic generators to return a new
   * deterministic generator that generates all of the values generated by the
   * specified generators.
   */
  def concatAll[R, A](gens: => Iterable[Gen[R, A]])(implicit trace: Trace): Gen[R, A] =
    Gen.suspend(gens.foldLeft[Gen[R, A]](Gen.empty)(_ ++ _))

  /**
   * A constant generator of the specified value. It produces one sample, with
   * no shrinking.
   */
  def const[A](a: => A)(implicit trace: Trace): Gen[Any, A] =
    Gen(ZStream.succeed(Sample.noShrink(a)))

  /**
   * A constant generator of the specified sample.
   */
  def constSample[R, A](sample: => Sample[R, A])(implicit trace: Trace): Gen[R, A] =
    Gen(ZStream.succeed(sample))

  /**
   * A generator of the currencies that `java.util.Currency` lists as
   * available.
   */
  def currency(implicit trace: Trace): Gen[Any, java.util.Currency] =
    elements(java.util.Currency.getAvailableCurrencies.asScala.toSeq: _*)

  /**
   * A generator of doubles between `0.0` (inclusive) and `1.0` (exclusive).
   * Shrinks toward `0.0`. Use `double(min, max)` for another range.
   */
  def double(implicit trace: Trace): Gen[Any, Double] =
    fromZIOSample(nextDouble.map(Sample.shrinkFractional(0f)))

  /**
   * A generator of double values from `min` (inclusive) to `max` (exclusive).
   * The shrinker will shrink toward `min`. When `min` equals `max`, the value
   * generated is the double just below `max`.
   */
  def double(min: Double, max: Double)(implicit trace: Trace): Gen[Any, Double] =
    if (min > max)
      Gen.fromZIO(ZIO.die(new IllegalArgumentException("invalid bounds")))
    else
      uniform.map { r =>
        val n = min + r * (max - min)
        if (n < max) n else Math.nextAfter(max, Double.NegativeInfinity)
      }

  /**
   * A generator that chooses `left` or `right` with the same probability and
   * wraps the value in `Left` or `Right`. Shrinks toward a `Left`.
   */
  def either[R, A, B](left: Gen[R, A], right: Gen[R, B])(implicit
    trace: Trace
  ): Gen[R, Either[A, B]] =
    oneOf(left.map(Left(_)), right.map(Right(_)))

  /**
   * A generator that chooses one of the specified values, each with the same
   * probability. Shrinks toward the first value. With no values it generates
   * nothing, like [[empty]].
   *
   * {{{
   * val weekdays: Gen[Any, String] = Gen.elements("Mon", "Tue", "Wed", "Thu", "Fri")
   * }}}
   */
  def elements[A](as: A*)(implicit trace: Trace): Gen[Any, A] =
    if (as.isEmpty) empty else int(0, as.length - 1).map(as)

  /**
   * A generator that produces no values. `check` with a generator that
   * produces no values does not complete.
   */
  def empty(implicit trace: Trace): Gen[Any, Nothing] =
    emptyGen

  /**
   * A generator of exponentially distributed doubles with mean `1`. The
   * shrinker will shrink toward `0`.
   */
  def exponential(implicit trace: Trace): Gen[Any, Double] =
    uniform.map(n => -math.log(1 - n))

  /**
   * Constructs a deterministic generator that generates the specified values
   * in order. The values do not shrink unless a `shrinker` is given, which
   * returns the shrinks of a value.
   *
   * {{{
   * val ports: Gen[Any, Int] = Gen.fromIterable(List(80, 443, 8080))
   * }}}
   */
  def fromIterable[R, A](
    as: Iterable[A],
    shrinker: A => ZStream[R, Nothing, A] = defaultShrinker
  )(implicit trace: Trace): Gen[R, A] =
    Gen(ZStream.fromIterable(as).map(a => Sample.unfold(a)(a => (a, shrinker(a)))))

  /**
   * Constructs a generator from a function that uses randomness. The returned
   * generator will not have any shrinking.
   */
  final def fromRandom[A](f: Random => UIO[A])(implicit trace: Trace): Gen[Any, A] =
    fromRandomSample(f(_).map(Sample.noShrink))

  /**
   * Constructs a generator from a function that uses randomness to produce a
   * sample, which carries its own shrinks.
   */
  final def fromRandomSample[R, A](f: Random => UIO[Sample[R, A]])(implicit
    trace: Trace
  ): Gen[R, A] =
    fromZIOSample(ZIO.randomWith(f))

  /**
   * Constructs a generator from an effect that constructs a value. The value
   * does not shrink.
   */
  def fromZIO[R, A](effect: URIO[R, A])(implicit trace: Trace): Gen[R, A] =
    fromZIOSample(effect.map(Sample.noShrink))

  /**
   * Constructs a generator from an effect that constructs a sample.
   */
  def fromZIOSample[R, A](effect: ZIO[R, Nothing, Sample[R, A]])(implicit trace: Trace): Gen[R, A] =
    Gen(ZStream.fromZIO(effect))

  /**
   * A generator of floats between `0.0` (inclusive) and `1.0` (exclusive).
   * Shrinks toward `0.0`.
   */
  def float(implicit trace: Trace): Gen[Any, Float] =
    fromZIOSample(nextFloat.map(Sample.shrinkFractional(0f)))

  /**
   * A generator of hexadecimal digits (`0` to `9`, `A` to `F` and `a` to `f`),
   * each with the same probability. Shrinks toward `'0'`.
   */
  def hexChar(implicit trace: Trace): Gen[Any, Char] = weighted(
    char('\u0030', '\u0039') -> 10,
    char('\u0041', '\u0046') -> 6,
    char('\u0061', '\u0066') -> 6
  )

  /**
   * A generator of lower-case hexadecimal digits (`0` to `9` and `a` to `f`),
   * each with the same probability. Shrinks toward `'0'`.
   */
  def hexCharLower(implicit trace: Trace): Gen[Any, Char] =
    weighted(
      char('\u0030', '\u0039') -> 10,
      char('\u0061', '\u0066') -> 6
    )

  /**
   * A generator of upper-case hexadecimal digits (`0` to `9` and `A` to `F`),
   * each with the same probability. Shrinks toward `'0'`.
   */
  def hexCharUpper(implicit trace: Trace): Gen[Any, Char] =
    weighted(
      char('\u0030', '\u0039') -> 10,
      char('\u0041', '\u0046') -> 6
    )

  /**
   * A generator of integers over the whole `Int` range. Shrinks toward `0`.
   */
  def int(implicit trace: Trace): Gen[Any, Int] =
    fromZIOSample(nextInt.map(Sample.shrinkIntegral(0)))

  /**
   * A generator of integers between `min` and `max`, both inclusive. The
   * shrinker will shrink toward `min`.
   *
   * {{{
   * val dice: Gen[Any, Int] = Gen.int(1, 6)
   * }}}
   */
  def int(min: Int, max: Int)(implicit trace: Trace): Gen[Any, Int] =
    Gen.fromZIOSample {
      if (min > max) ZIO.die(new IllegalArgumentException("invalid bounds"))
      else {
        val effect =
          if (max < Int.MaxValue) nextIntBetween(min, max + 1)
          else if (min > Int.MinValue) nextIntBetween(min - 1, max).map(_ + 1)
          else nextInt
        effect.map(Sample.shrinkIntegral(min))
      }
    }

  /**
   * A sized generator of strings that can be encoded in the ISO-8859-1
   * character set. Shrinks toward the empty string.
   */
  def iso_8859_1(implicit trace: Trace): Gen[Any, String] =
    chunkOf(byte).map(chunk => new String(chunk.toArray, StandardCharsets.ISO_8859_1))

  /**
   * A sized generator that passes a size between `min` and the current size,
   * drawn with the same probability for each size, to `f`. With the default
   * size of 100 the mean size is about 50. The size shrinks toward `min`.
   */
  def large[R, A](f: Int => Gen[R, A], min: Int = 0)(implicit
    trace: Trace
  ): Gen[R, A] =
    size.flatMap(max => int(min, max)).flatMap(f)

  /**
   * A sized generator of lists. The length is drawn as in [[small]], and the
   * list shrinks toward the empty list.
   *
   * {{{
   * val scores: Gen[Any, List[Int]] = Gen.listOf(Gen.int(0, 100))
   * }}}
   */
  def listOf[R, A](g: Gen[R, A])(implicit trace: Trace): Gen[R, List[A]] =
    small(listOfN(_)(g))

  /**
   * A sized generator of non-empty lists. Shrinks toward a list of one
   * element.
   */
  def listOf1[R, A](g: Gen[R, A])(implicit trace: Trace): Gen[R, ::[A]] =
    for {
      h <- g
      t <- small(n => listOfN(n - 1 max 0)(g))
    } yield ::(h, t)

  /**
   * A generator of lists whose length is between `min` and `max`, both
   * inclusive. The length shrinks toward `min`.
   */
  def listOfBounded[R, A](min: Int, max: Int)(g: Gen[R, A])(implicit
    trace: Trace
  ): Gen[R, List[A]] =
    bounded(min, max)(listOfN(_)(g))

  /**
   * A generator of lists of the specified size. A size of zero or less gives
   * empty lists. The elements shrink, the length does not.
   */
  def listOfN[R, A](n: Int)(g: Gen[R, A])(implicit trace: Trace): Gen[R, List[A]] =
    buildN(n)(g)(List.empty[A])((list, a) => a :: list)(_.reverse)

  /**
   * A generator of longs over the whole `Long` range. Shrinks toward `0`.
   */
  def long(implicit trace: Trace): Gen[Any, Long] =
    fromZIOSample(nextLong.map(Sample.shrinkIntegral(0L)))

  /**
   * A generator of long values between `min` and `max`, both inclusive. The
   * shrinker will shrink toward `min`.
   */
  def long(min: Long, max: Long)(implicit trace: Trace): Gen[Any, Long] =
    Gen.fromZIOSample {
      if (min > max) ZIO.die(new IllegalArgumentException("invalid bounds"))
      else {
        val effect =
          if (max < Long.MaxValue) nextLongBetween(min, max + 1L)
          else if (min > Long.MinValue) nextLongBetween(min - 1L, max).map(_ + 1L)
          else nextLong
        effect.map(Sample.shrinkIntegral(min))
      }
    }

  /**
   * A sized generator of maps. The size is drawn as in [[small]], and the map
   * shrinks toward the empty map.
   */
  def mapOf[R, A, B](key: Gen[R, A], value: Gen[R, B])(implicit
    trace: Trace
  ): Gen[R, Map[A, B]] =
    small(mapOfN(_)(key, value))

  /**
   * A sized generator of non-empty maps. Shrinks toward a map of one entry.
   */
  def mapOf1[R, A, B](key: Gen[R, A], value: Gen[R, B])(implicit
    trace: Trace
  ): Gen[R, Map[A, B]] =
    small(mapOfN(_)(key, value), min = 1)

  /**
   * A generator of maps of the specified size. A draw that repeats a key is
   * discarded, so the key generator needs at least `n` distinct values.
   */
  def mapOfN[R, A, B](n: Int)(key: Gen[R, A], value: Gen[R, B])(implicit
    trace: Trace
  ): Gen[R, Map[A, B]] = {
    val pair = key.zip(value)
    def loop(acc: Map[A, B]): Gen[R, Map[A, B]] =
      if (acc.size >= n) Gen.const(acc)
      else
        pair.flatMap { case (k, v) =>
          if (acc.contains(k)) Gen.empty
          else loop(acc.updated(k, v))
        }

    loop(Map.empty)
  }

  /**
   * A generator of maps whose size is between `min` and `max`, both
   * inclusive.
   */
  def mapOfBounded[R, A, B](min: Int, max: Int)(key: Gen[R, A], value: Gen[R, B])(implicit
    trace: Trace
  ): Gen[R, Map[A, B]] =
    mapOfN(min)(key, value).zipWith(listOfBounded(0, max - min)(key.zip(value)))(_ ++ _)

  /**
   * A sized generator that passes a size between `min` and the current size,
   * drawn from an exponential distribution, to `f`. Most sizes are small but
   * some larger sizes are generated as well: with the default size of 100 the
   * mean size is about 10. The size shrinks toward `min`.
   */
  def medium[R, A](f: Int => Gen[R, A], min: Int = 0)(implicit
    trace: Trace
  ): Gen[R, A] = {
    val gen = for {
      max <- size
      n   <- exponential
    } yield clamp(math.round(n * max / 10.0).toInt, min, max)
    gen.reshrink(Sample.shrinkIntegral(min)).flatMap(f)
  }

  /**
   * A constant generator of the empty value.
   */
  def none(implicit trace: Trace): Gen[Any, Option[Nothing]] =
    noneGen

  /**
   * A generator of the digits `0` to `9`. Shrinks toward `'0'`.
   */
  def numericChar(implicit trace: Trace): Gen[Any, Char] =
    weighted(char(48, 57) -> 10)

  /**
   * A generator of optional values, `None` and `Some` with the same
   * probability. Shrinks toward `None`.
   */
  def option[R, A](gen: Gen[R, A])(implicit trace: Trace): Gen[R, Option[A]] =
    oneOf(none, gen.map(Some(_)))

  /**
   * A generator that chooses one of the specified generators, each with the
   * same probability, and generates its value. Shrinks toward the values of
   * the first generator. With no generators it generates nothing, like
   * [[empty]].
   *
   * {{{
   * val statusCodes: Gen[Any, Int] = Gen.oneOf(Gen.int(200, 299), Gen.int(500, 599))
   * }}}
   */
  def oneOf[R, A](as: Gen[R, A]*)(implicit trace: Trace): Gen[R, A] =
    if (as.isEmpty) empty else int(0, as.length - 1).flatMap(as)

  /**
   * Constructs a generator of partial functions from `A` to `B` given a
   * generator of `B` values. Two `A` values will be considered to be equal, and
   * thus will generate the same `B` value or both be outside the partial
   * function's domain, if they have the same `hashCode`. About half of the
   * inputs are outside the domain.
   */
  def partialFunction[R, A, B](gen: Gen[R, B])(implicit
    trace: Trace
  ): Gen[R, PartialFunction[A, B]] =
    partialFunctionWith(gen)(_.hashCode)

  /**
   * Constructs a generator of partial functions from `A` to `B` given a
   * generator of `B` values and a hashing function for `A` values. Two `A`
   * values will be considered to be equal, and thus will generate the same `B`
   * value or both be outside the partial function's domain, if they have the
   * same hash. This is useful when `A` does not implement `hashCode` in a way
   * that is consistent with equality.
   */
  def partialFunctionWith[R, A, B](gen: Gen[R, B])(hash: A => Int)(implicit
    trace: Trace
  ): Gen[R, PartialFunction[A, B]] =
    functionWith(option(gen))(hash).map(Function.unlift)

  /**
   * A generator of printable ASCII characters, from `'!'` (code 33) to `'~'`
   * (code 126), so the space is not included. Shrinks toward `'!'`.
   */
  def printableChar(implicit trace: Trace): Gen[Any, Char] =
    char(33, 126)

  /**
   * A sized generator of sets. The size is drawn as in [[small]], and the set
   * shrinks toward the empty set.
   */
  def setOf[R, A](gen: Gen[R, A])(implicit trace: Trace): Gen[R, Set[A]] =
    small(setOfN(_)(gen))

  /**
   * A sized generator of non-empty sets. Shrinks toward a set of one element.
   */
  def setOf1[R, A](gen: Gen[R, A])(implicit trace: Trace): Gen[R, Set[A]] =
    small(setOfN(_)(gen), min = 1)

  /**
   * A generator of sets whose size is between `min` and `max`, both
   * inclusive.
   */
  def setOfBounded[R, A](min: Int, max: Int)(g: Gen[R, A])(implicit
    trace: Trace
  ): Gen[R, Set[A]] =
    setOfN(min)(g).zipWith(listOfBounded(0, max - min)(g))(_ ++ _)

  /**
   * A generator of sets of the specified size. A draw that repeats an element
   * is discarded, so the element generator needs at least `n` distinct values.
   */
  def setOfN[R, A](n: Int)(gen: Gen[R, A])(implicit trace: Trace): Gen[R, Set[A]] = {
    def loop(acc: Set[A]): Gen[R, Set[A]] =
      if (acc.size >= n) Gen.const(acc)
      else
        gen.flatMap(elem =>
          if (acc.contains(elem)) Gen.empty
          else loop(acc + elem)
        )

    loop(Set.empty)
  }

  /**
   * A generator of shorts over the whole `Short` range. Shrinks toward `0`.
   */
  def short(implicit trace: Trace): Gen[Any, Short] =
    fromZIOSample {
      nextIntBounded(Short.MaxValue - Short.MinValue + 1)
        .map(r => (Short.MinValue + r).toShort)
        .map(Sample.shrinkIntegral(0))
    }

  /**
   * A generator of short values between `min` and `max`, both inclusive. The
   * shrinker will shrink toward `min`.
   */
  def short(min: Short, max: Short)(implicit trace: Trace): Gen[Any, Short] =
    int(min.toInt, max.toInt).map(_.toShort)

  /**
   * A generator of the current size of the [[Sized]] service (100 in the test
   * environment).
   */
  def size(implicit trace: Trace): Gen[Any, Int] =
    Gen.fromZIO(Sized.size)

  /**
   * Passes the current size of the [[Sized]] service to `f` and generates the
   * values of the generator it returns.
   *
   * {{{
   * val upToSize: Gen[Any, Int] = Gen.sized(size => Gen.int(0, size))
   * }}}
   */
  def sized[R, A](f: Int => Gen[R, A])(implicit trace: Trace): Gen[R, A] =
    size.flatMap(f)

  /**
   * A sized generator that passes a size between `min` and the current size,
   * drawn from an exponential distribution, to `f`. The sizes are strongly
   * concentrated towards the lower end of the range but a few larger sizes
   * are generated as well: with the default size of 100 the mean size is
   * about 4. The size shrinks toward `min`. The collection generators such as
   * [[listOf]] use it.
   */
  def small[R, A](f: Int => Gen[R, A], min: Int = 0)(implicit
    trace: Trace
  ): Gen[R, A] = {
    val gen = for {
      max <- size
      n   <- exponential
    } yield clamp(math.round(n * max / 25.0).toInt, min, max)
    gen.reshrink(Sample.shrinkIntegral(min)).flatMap(f)
  }

  /** A generator that wraps the values of `gen` in `Some`. */
  def some[R, A](gen: Gen[R, A])(implicit trace: Trace): Gen[R, Option[A]] =
    gen.map(Some(_))

  /**
   * A sized generator of strings of [[unicodeChar]] characters. Shrinks
   * towards the empty string.
   */
  def string(implicit trace: Trace): Gen[Any, String] =
    Gen.string(Gen.unicodeChar)

  /**
   * A sized generator of strings of the characters that `char` generates. The
   * length is drawn as in [[small]], and the string shrinks toward the empty
   * string.
   */
  def string[R](char: Gen[R, Char])(implicit trace: Trace): Gen[R, String] =
    small(stringN(_)(char))

  /**
   * A sized generator of non-empty strings. Shrinks toward a string of one
   * character.
   */
  def string1[R](char: Gen[R, Char])(implicit trace: Trace): Gen[R, String] =
    small(stringN(_)(char), min = 1)

  /**
   * A generator of strings whose length is between `min` and `max`, both
   * inclusive. The length shrinks toward `min`.
   */
  def stringBounded[R](min: Int, max: Int)(g: Gen[R, Char])(implicit
    trace: Trace
  ): Gen[R, String] =
    bounded(min, max)(stringN(_)(g))

  /**
   * A generator of strings of the specified length. A length of zero or less
   * gives empty strings.
   */
  def stringN[R](n: Int)(char: Gen[R, Char])(implicit trace: Trace): Gen[R, String] =
    buildN(n)(char)(Chunk.empty[Char])(_ :+ _)(chunk => new String(chunk.toArray))

  /**
   * Lazily constructs a generator. This is useful to avoid infinite recursion
   * when creating generators that refer to themselves.
   */
  def suspend[R, A](gen: => Gen[R, A])(implicit trace: Trace): Gen[R, A] =
    Gen(ZStream.suspend(gen.sample))

  /**
   * A generator of a new `Throwable` with no message for each sample.
   */
  def throwable(implicit trace: Trace): Gen[Any, Throwable] =
    Gen.const(new Throwable)

  /**
   * A sized generator of lists, where each list is generated by repeatedly
   * applying a function to an initial state. The length is drawn as in
   * [[small]].
   */
  def unfoldGen[R, S, A](s: S)(f: S => Gen[R, (S, A)])(implicit
    trace: Trace
  ): Gen[R, List[A]] =
    small(unfoldGenN(_)(s)(f))

  /**
   * A generator of lists of the specified size, where each list is generated
   * by repeatedly applying a function to an initial state: `f` returns the
   * next state and the next element.
   */
  def unfoldGenN[R, S, A](n: Int)(s: S)(f: S => Gen[R, (S, A)])(implicit trace: Trace): Gen[R, List[A]] = {

    def loop(n: Int, s: S, as: List[A]): Gen[R, List[A]] =
      if (n <= 0)
        Gen.const(as.reverse)
      else
        f(s).flatMap { case (s, a) => loop(n - 1, s, a :: as) }

    loop(n, s, Nil)
  }

  /**
   * A generator of Unicode characters outside the surrogate range: it chooses
   * the range of codes `0x0000` to `0xD7FF` or the range `0xE000` to `0xFFFD`
   * with the same probability, then a character in that range. Shrinks toward
   * the character with code 0.
   */
  def unicodeChar(implicit trace: Trace): Gen[Any, Char] =
    Gen.oneOf(Gen.char('\u0000', '\uD7FF'), Gen.char('\uE000', '\uFFFD'))

  /**
   * A generator of uniformly distributed doubles between `0.0` (inclusive) and
   * `1.0` (exclusive). The shrinker will shrink toward `0`.
   */
  def uniform(implicit trace: Trace): Gen[Any, Double] =
    fromZIOSample(nextDouble.map(Sample.shrinkFractional(0.0)))

  /**
   * A constant generator of the unit value.
   */
  def unit(implicit trace: Trace): Gen[Any, Unit] =
    unitGen

  /**
   * A generator of universally unique identifiers. The returned generator will
   * not have any shrinking.
   */
  def uuid(implicit trace: Trace): Gen[Any, UUID] =
    Gen.fromZIO(nextUUID)

  /**
   * A sized generator of vectors. The length is drawn as in [[small]], and the
   * vector shrinks toward the empty vector.
   */
  def vectorOf[R, A](g: Gen[R, A])(implicit trace: Trace): Gen[R, Vector[A]] =
    small(vectorOfN(_)(g))

  /**
   * A sized generator of non-empty vectors. Shrinks toward a vector of one
   * element.
   */
  def vectorOf1[R, A](g: Gen[R, A])(implicit trace: Trace): Gen[R, Vector[A]] =
    small(vectorOfN(_)(g), min = 1)

  /**
   * A generator of vectors whose length is between `min` and `max`, both
   * inclusive. The length shrinks toward `min`.
   */
  def vectorOfBounded[R, A](min: Int, max: Int)(g: Gen[R, A])(implicit
    trace: Trace
  ): Gen[R, Vector[A]] =
    bounded(min, max)(vectorOfN(_)(g))

  /**
   * A generator of vectors of the specified size. A size of zero or less gives
   * empty vectors.
   */
  def vectorOfN[R, A](n: Int)(g: Gen[R, A])(implicit trace: Trace): Gen[R, Vector[A]] =
    buildN(n)(g)(Vector.empty[A])(_ :+ _)(identity)

  /**
   * A generator which chooses one of the given generators according to their
   * weights. A generator with weight zero is never chosen. Shrinks toward the
   * values of the first generator with a positive weight. For example, the
   * following generator will generate 90% true and 10% false values.
   *
   * {{{
   * val trueFalse = Gen.weighted((Gen.const(true), 9), (Gen.const(false), 1))
   * }}}
   */
  def weighted[R, A](gs: (Gen[R, A], Double)*)(implicit trace: Trace): Gen[R, A] = {
    val sum = gs.map(_._2).sum
    val (map, _) = gs.foldLeft((SortedMap.empty[Double, Gen[R, A]], 0.0)) { case ((map, acc), (gen, d)) =>
      if ((acc + d) / sum > acc / sum) (map.updated((acc + d) / sum, gen), acc + d)
      else (map, acc)
    }
    uniform.flatMap(n => map.rangeImpl(Some(n), None).head._2)
  }

  /**
   * A generator of the characters for which `Char.isWhitespace` is `true`,
   * each with the same probability. Shrinks toward the tab character.
   */
  def whitespaceChars(implicit trace: Trace): Gen[Any, Char] =
    Gen.elements((Char.MinValue to Char.MaxValue).filter(_.isWhitespace): _*)

  /**
   * Restricts an integer to the specified range.
   */
  private def clamp(n: Int, min: Int, max: Int): Int =
    if (n < min) min
    else if (n > max) max
    else n

  private def buildN[R, A, B, C](n: Int)(g: Gen[R, A])(zero: B)(add: (B, A) => B)(build: B => C)(implicit
    trace: Trace
  ): Gen[R, C] = {
    def loop(remaining: Int, acc: B): Gen[R, C] =
      if (remaining <= 0) Gen.const(build(acc))
      else g.flatMap(a => loop(remaining - 1, add(acc, a)))

    loop(n, zero)
  }

  private val defaultShrinker: Any => ZStream[Any, Nothing, Nothing] =
    _ => ZStream.empty(Trace.empty)

  private val emptyGen: Gen[Any, Nothing]        = Gen(ZStream.empty(Trace.empty))
  private val unitGen: Gen[Any, Unit]            = const(())(Trace.empty)
  private val noneGen: Gen[Any, Option[Nothing]] = const(None)(Trace.empty)
}
