/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

import scala.collection.immutable.SortedMap

import zio.{ UIO, ZIO }
import zio.random._
import zio.stream.{ Stream, ZStream }

/**
 * A `Gen[R, A]` represents a generator of values of type `A`, which requires
 * an environment `R`. Generators may be random or deterministic.
 */
case class Gen[-R, +A](sample: ZStream[R, Nothing, Sample[R, A]]) { self =>

  final def <*>[R1 <: R, B](that: Gen[R1, B]): Gen[R1, (A, B)] =
    self.zip(that)

  final def filter(f: A => Boolean): Gen[R, A] =
    flatMap(a => if (f(a)) Gen.const(a) else Gen.empty)

  final def flatMap[R1 <: R, B](f: A => Gen[R1, B]): Gen[R1, B] = Gen {
    self.sample.flatMap { sample =>
      val values  = f(sample.value).sample
      val shrinks = Gen(sample.shrink).flatMap(f).sample
      values.map(_.flatMap(Sample(_, shrinks)))
    }
  }

  final def map[B](f: A => B): Gen[R, B] =
    Gen(sample.map(_.map(f)))

  final def zip[R1 <: R, B](that: Gen[R1, B]): Gen[R1, (A, B)] =
    self.flatMap(a => that.map(b => (a, b)))

  final def zipWith[R1 <: R, B, C](that: Gen[R1, B])(f: (A, B) => C): Gen[R1, C] =
    self.zip(that).map(f.tupled)
}

object Gen {

  /**
   * A generator of alphanumeric characters. Shrinks toward '0'.
   */
  final val alphaNumericChar: Gen[Random, Char] =
    weighted((char(48, 57), 10), (char(65, 122), 52))

  /**
   * A generator of bytes. Shrinks toward '0'.
   */
  final val anyByte: Gen[Random, Byte] =
    fromEffectSample {
      nextInt(Byte.MaxValue - Byte.MinValue + 1)
        .map(r => (Byte.MinValue + r).toByte)
        .map(Sample.shrinkIntegral(0))
    }

  /**
   * A generator of characters. Shrinks toward '0'.
   */
  final val anyChar: Gen[Random, Char] =
    fromEffectSample {
      nextInt(Char.MaxValue - Char.MinValue + 1)
        .map(r => (Char.MinValue + r).toChar)
        .map(Sample.shrinkIntegral(0))
    }

  /**
   * A generator of integers. Shrinks toward '0'.
   */
  final val anyFloat: Gen[Random, Float] =
    fromEffectSample(nextFloat.map(Sample.shrinkFractional(0f)))

  /**
   * A generator of integers. Shrinks toward '0'.
   */
  final val anyInt: Gen[Random, Int] =
    fromEffectSample(nextInt.map(Sample.shrinkIntegral(0)))

  /**
   * A generator of longs. Shrinks toward '0'.
   */
  final val anyLong: Gen[Random, Long] =
    fromEffectSample(nextLong.map(Sample.shrinkIntegral(0L)))

  /**
   * A generator of shorts. Shrinks toward '0'.
   */
  final val anyShort: Gen[Random, Short] =
    fromEffectSample {
      nextInt(Short.MaxValue - Short.MinValue + 1)
        .map(r => (Short.MinValue + r).toShort)
        .map(Sample.shrinkIntegral(0))
    }

  /**
   * A generator of booleans. Shrinks toward 'false'.
   */
  final val boolean: Gen[Random, Boolean] =
    elements(false, true)

  /**
   * A generator of byte values inside the specified range: [start, end].
   * The shrinker will shrink toward the lower end of the range ("smallest").
   */
  final def byte(min: Byte, max: Byte): Gen[Random, Byte] =
    integral(min, max)

  /**
   * A generator of character values inside the specified range: [start, end].
   * The shrinker will shrink toward the lower end of the range ("smallest").
   */
  final def char(min: Char, max: Char): Gen[Random, Char] =
    integral(min, max)

  /**
   * A constant generator of the specified value.
   */
  final def const[A](a: => A): Gen[Any, A] =
    Gen(ZStream.succeed(Sample.noShrink(a)))

  /**
   * A constant generator of the specified sample.
   */
  final def constSample[R, A](sample: => Sample[R, A]): Gen[R, A] =
    fromEffectSample(ZIO.succeed(sample))

  /**
   * A generator of double values inside the specified range: [start, end].
   * The shrinker will shrink toward the lower end of the range ("smallest").
   */
  final def double(min: Double, max: Double): Gen[Random, Double] =
    uniform.map { r =>
      val n = min + r * (max - min)
      if (n < max) n else Math.nextAfter(max, Double.NegativeInfinity)
    }

  final def either[R <: Random, A, B](left: Gen[R, A], right: Gen[R, B]): Gen[R, Either[A, B]] =
    oneOf(left.map(Left(_)), right.map(Right(_)))

  final def elements[A](as: A*): Gen[Random, A] =
    if (as.isEmpty) empty else int(0, as.length - 1).map(as)

  final val empty: Gen[Any, Nothing] =
    Gen(Stream.empty)

  /**
   * Constructs a generator from an effect that constructs a value.
   */
  final def fromEffect[R, A](effect: ZIO[R, Nothing, A]): Gen[R, A] =
    Gen(ZStream.fromEffect(effect.map(Sample.noShrink)))

  /**
   * Constructs a generator from an effect that constructs a sample.
   */
  final def fromEffectSample[R, A](effect: ZIO[R, Nothing, Sample[R, A]]): Gen[R, A] =
    Gen(ZStream.fromEffect(effect))

  /**
   * Constructs a deterministic generator that only generates the specified fixed values.
   */
  final def fromIterable[R, A](
    as: Iterable[A],
    shrinker: (A => ZStream[R, Nothing, A]) = (_: A) => ZStream.empty
  ): Gen[R, A] =
    Gen(ZStream.fromIterable(as).map(a => Sample.unfold(a)(a => (a, shrinker(a)))))

  /**
   * Constructs a generator from a function that uses randomness. The returned
   * generator will not have any shrinking.
   */
  final def fromRandom[A](f: Random.Service[Any] => UIO[A]): Gen[Random, A] =
    Gen(ZStream.fromEffect(ZIO.accessM[Random](r => f(r.random)).map(Sample.noShrink)))

  /**
   * Constructs a generator from a function that uses randomness to produce a
   * sample.
   */
  final def fromRandomSample[R <: Random, A](f: Random.Service[Any] => UIO[Sample[R, A]]): Gen[R, A] =
    Gen(ZStream.fromEffect(ZIO.accessM[Random](r => f(r.random))))

  /**
   * A generator of integers inside the specified range: [start, end].
   * The shrinker will shrink toward the lower end of the range ("smallest").
   */
  final def int(min: Int, max: Int): Gen[Random, Int] =
    integral(min, max)

  /**
   * A generator of integral values inside the specified range: [start, end].
   * The shrinker will shrink toward the lower end of the range ("smallest").
   */
  final def integral[A](min: A, max: A)(implicit I: Integral[A]): Gen[Random, A] =
    fromEffectSample {
      nextInt(I.toInt(max) - I.toInt(min) + 1)
        .map(r => I.plus(min, I.fromInt(r)))
        .map(Sample.shrinkIntegral(min))
    }

  final def listOf[R <: Random with Sized, A](g: Gen[R, A]): Gen[R, List[A]] =
    sized(n => int(0, n max 0)).flatMap(listOfN(_)(g))

  final def listOf1[R <: Random with Sized, A](g: Gen[R, A]): Gen[R, List[A]] =
    sized(n => int(1, n max 1)).flatMap(listOfN(_)(g))

  final def listOfN[R <: Random, A](n: Int)(g: Gen[R, A]): Gen[R, List[A]] =
    List.fill(n)(g).foldRight[Gen[R, List[A]]](const(Nil))((a, gen) => a.zipWith(gen)(_ :: _))

  /**
   * A constant generator of the empty value.
   */
  final val none: Gen[Any, Option[Nothing]] =
    Gen.const(None)

  /**
   * A generator of optional values. Shrinks toward `None`.
   */
  final def option[R <: Random, A](gen: Gen[R, A]): Gen[R, Option[A]] =
    oneOf(none, gen.map(Some(_)))

  final def oneOf[R <: Random, A](as: Gen[R, A]*): Gen[R, A] =
    if (as.isEmpty) empty else int(0, as.length - 1).flatMap(as)

  /**
   * A generator of printable characters. Shrinks toward '!'.
   */
  final val printableChar: Gen[Random, Char] =
    char(33, 126)

  /**
   * A generator of short values inside the specified range: [start, end].
   * The shrinker will shrink toward the lower end of the range ("smallest").
   */
  final def short(min: Short, max: Short): Gen[Random, Short] =
    integral(min, max)

  final val size: Gen[Sized, Int] =
    Gen.fromEffect(Sized.size)

  /**
   * A sized generator, whose size falls within the specified bounds.
   */
  final def sized[R <: Sized, A](f: Int => Gen[R, A]): Gen[R, A] =
    size.flatMap(f)

  final def some[R, A](gen: Gen[R, A]): Gen[R, Option[A]] =
    gen.map(Some(_))

  final def string[R <: Random with Sized](char: Gen[R, Char]): Gen[R, String] =
    listOf(char).map(_.mkString)

  final def string1[R <: Random with Sized](char: Gen[R, Char]): Gen[R, String] =
    listOf1(char).map(_.mkString)

  final def stringN[R <: Random](n: Int)(char: Gen[R, Char]): Gen[R, String] =
    listOfN(n)(char).map(_.mkString)

  /**
   * A generator of uniformly distributed doubles between [0, 1].
   * The shrinker will shrink toward `0`.
   */
  final def uniform: Gen[Random, Double] =
    fromEffectSample(nextDouble.map(Sample.shrinkFractional(0.0)))

  /**
   * A constant generator of the unit value.
   */
  final val unit: Gen[Any, Unit] =
    const(())

  final def vectorOf[R <: Random with Sized, A](g: Gen[R, A]): Gen[R, Vector[A]] =
    listOf(g).map(_.toVector)

  final def vectorOf1[R <: Random with Sized, A](g: Gen[R, A]): Gen[R, Vector[A]] =
    listOf1(g).map(_.toVector)

  final def vectorOfN[R <: Random, A](n: Int)(g: Gen[R, A]): Gen[R, Vector[A]] =
    listOfN(n)(g).map(_.toVector)

  final def weighted[R <: Random, A](gs: (Gen[R, A], Double)*): Gen[R, A] = {
    implicit val ord = new Ordering[Double] {
      def compare(x: Double, y: Double): Int = java.lang.Double.compare(x, y)
    }
    val sum = gs.map(_._2).sum
    val (map, _) = gs.foldLeft((SortedMap.empty[Double, Gen[R, A]], 0.0)) {
      case ((map, acc), (gen, d)) => (map.updated((acc + d) / sum, gen), acc + d)
    }
    uniform.flatMap(n => map.rangeImpl(Some(n), None).head._2)
  }
}
