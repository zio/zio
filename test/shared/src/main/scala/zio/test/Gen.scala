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

import zio.{ UIO, ZIO }
import zio.random._
import zio.stream.{ Stream, ZStream }

/**
 * A `Gen[R, A]` represents a generator of values of type `A`, which requires
 * an environment `R`. Generators may be random or deterministic.
 */
case class Gen[-R, +A](sample: ZStream[R, Nothing, Sample[R, A]]) { self =>

  final def <*>[R1 <: R, B](that: Gen[R1, B]): Gen[R1, (A, B)] =
    self zip that

  final def filter(f: A => Boolean): Gen[R, A] =
    flatMap(a => if (f(a)) Gen.const(a) else Gen.empty)

  final def flatMap[R1 <: R, B](f: A => Gen[R1, B]): Gen[R1, B] = Gen {
    sample.flatMap { a =>
      f(a.value).sample.map { b =>
        val rest = a.shrink.flatMap(a => f(a).sample).flatMap(sample => ZStream(sample.value) ++ sample.shrink)
        Sample(b.value, b.shrink ++ rest)
      }
    }
  }

  final def map[B](f: A => B): Gen[R, B] =
    Gen(sample.map(_.map(f)))

  final def noShrink: Gen[R, A] =
    Gen(sample.map(sample => Sample.noShrink(sample.value)))

  final def withShrink[R1 <: R, A1 >: A](f: A => ZStream[R1, Nothing, A1]): Gen[R1, A1] =
    Gen(sample.map(sample => Sample(sample.value, f(sample.value) ++ sample.shrink.flatMap(f))))

  final def zip[R1 <: R, B](that: Gen[R1, B]): Gen[R1, (A, B)] =
    self.flatMap(a => that.map(b => (a, b)))

  final def zipAll[R1 <: R, B](that: Gen[R1, B]): Gen[R1, (Option[A], Option[B])] =
    Gen {
      (self.sample.map(Some(_)) ++ Stream(None).forever)
        .zip(that.sample.map(Some(_)) ++ Stream(None).forever)
        .collectWhile {
          case (Some(a), Some(b)) => a.zipWith(b) { case (a, b) => (Some(a), Some(b)) }
          case (Some(a), None)    => a.map(a => (Some(a), None))
          case (None, Some(b))    => b.map(b => (None, Some(b)))
        }
    }

  final def zipWith[R1 <: R, B, C](that: Gen[R1, B])(f: (A, B) => C): Gen[R1, C] =
    (self zip that) map f.tupled
}

object Gen {

  /**
   * A generator of booleans.
   */
  final val boolean: Gen[Random, Boolean] =
    choose(false, true)

  final def char(range: Range): Gen[Random, Char] =
    integral[Char](range)

  final def choose[A](a: A, as: A*): Gen[Random, A] =
    oneOf(const(a), as.map(a => const(a)): _*)

  /**
   * A constant generator of the specified value.
   */
  final def const[A](a: => A): Gen[Any, A] =
    Gen(ZStream.succeedLazy(Sample.noShrink(a)))

  /**
   * A constant generator of the specified sample.
   */
  final def constSample[R, A](sample: => Sample[R, A]): Gen[R, A] =
    fromEffectSample(ZIO.succeedLazy(sample))

  final def double(min: Double, max: Double): Gen[Random, Double] =
    uniform.map(r => min + r * (max - min))

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
    Gen(ZStream.fromIterable(as).map(a => Sample(a, shrinker(a))))

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
  final def int(range: Range): Gen[Random, Int] =
    integral[Int](range)

  /**
   * A generator of integral values inside the specified range: [start, end].
   * The shrinker will shrink toward the lower end of the range ("smallest").
   */
  final def integral[A](range: Range)(implicit I: Integral[A]): Gen[Random, A] =
    fromEffectSample(
      nextInt(range.end - range.start + 1)
        .map(r => I.fromInt(r + range.start))
        .map(int => Sample(int, ZStream.fromIterable(range.start.until(I.toInt(int)).reverse).map(n => I.fromInt(n))))
    )

  final def listOf[R, A](range: Range)(gen: Gen[R, A]): Gen[R with Random, List[A]] =
    int(range).flatMap(n => listOfN(n)(gen))

  final def listOfN[R, A](n: Int)(gen: Gen[R, A]): Gen[R, List[A]] =
    List.fill(n)(gen).foldRight[Gen[R, List[A]]](const(Nil))((a, gen) => a.zipWith(gen)(_ :: _))

  final def optionOf[R, A](gen: Gen[R, A]): Gen[R with Random, Option[A]] =
    oneOf(const(None), gen.map(Some(_)))

  final def oneOf[R, A](a: Gen[R, A], as: Gen[R, A]*): Gen[R with Random, A] =
    int(0 to as.length).flatMap(n => if (n == 0) a else as(n - 1))

  /**
   * A sized generator, whose size falls within the specified bounds.
   */
  final def sized[R <: Random, A](min: Int, max: Int)(f: Int => Gen[R, A]): Gen[R, A] =
    integral[Int](min to max).flatMap(f)

  final def string[R](range: Range)(char: Gen[R, Char]): Gen[R with Random, String] =
    listOf(range)(char).map(_.toString)

  final def stringN[R](n: Int)(char: Gen[R, Char]): Gen[R, String] =
    listOfN(n)(char).map(_.toString)

  /**
   * A generator of uniformly distributed doubles between [0, 1].
   *
   * TODO: Make Shrinker go toward `0`
   */
  final def uniform: Gen[Random, Double] =
    Gen(ZStream.fromEffect(nextDouble.map(Sample.noShrink)))

  final def union[R, A](gen1: Gen[R, A], gen2: Gen[R, A]): Gen[R, A] =
    gen1.zipAll(gen2).flatMap {
      case (a, a2) =>
        Gen {
          a.fold[Gen[R, A]](empty)(const(_)).sample ++
            a2.fold[Gen[R, A]](empty)(const(_)).sample
        }
    }

  /**
   * A constant generator of the unit value.
   */
  final val unit: Gen[Any, Unit] =
    const(())

  final def vectorOf[R, A](range: Range)(gen: Gen[R, A]): Gen[R with Random, Vector[A]] =
    listOf(range)(gen).map(_.toVector)

  final def vectorOfN[R, A](n: Int)(gen: Gen[R, A]): Gen[R, Vector[A]] =
    listOfN(n)(gen).map(_.toVector)
}
