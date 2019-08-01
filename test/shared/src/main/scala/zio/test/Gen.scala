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
import zio.stream.ZStream

/**
 * A `Gen[R, A]` represents a generator of values of type `A`, which requires
 * an environment `R`. Generators may be random or deterministic.
 */
case class Gen[-R, +A](sample: ZStream[R, Nothing, Sample[R, A]]) { self =>
  final def <*>[R1 <: R, B](that: Gen[R1, B]): Gen[R1, (A, B)] = self zip that

  final def flatMap[R1 <: R, B](f: A => Gen[R1, B]): Gen[R1, B] =
    Gen(sample.flatMap(sample => f(sample.value).sample))

  final def map[B](f: A => B): Gen[R, B] = Gen(sample.map(_.map(f)))

  final def zip[R1 <: R, B](that: Gen[R1, B]): Gen[R1, (A, B)] = self.flatMap(a => that.map(b => (a, b)))

  final def zipWith[R1 <: R, B, C](that: Gen[R1, B])(f: (A, B) => C): Gen[R1, C] =
    (self zip that) map f.tupled
}
object Gen {

  /**
   * A constant generator of the specified value.
   */
  final def const[A](a: => A): Gen[Any, A] = Gen(ZStream.succeedLazy(Sample.noShrink(a)))

  /**
   * A constant generator of the specified sample.
   */
  final def constSample[R, A](sample: => Sample[R, A]): Gen[R, A] = fromEffectSample(ZIO.succeedLazy(sample))

  /**
   * Constructs a generator from an effect that constructs a value.
   */
  final def fromEffect[R, A](effect: ZIO[R, Nothing, A]): Gen[R, A] =
    Gen(ZStream.fromEffect(effect.map(Sample.noShrink(_))))

  /**
   * Constructs a generator from an effect that constructs a sample.
   */
  final def fromEffectSample[R, A](effect: ZIO[R, Nothing, Sample[R, A]]): Gen[R, A] = Gen(ZStream.fromEffect(effect))

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
    Gen(ZStream.fromEffect(ZIO.accessM[Random](r => f(r.random)).map(Sample.noShrink(_))))

  /**
   * A generator of integral values inside the specified range: [start, end).
   * The shrinker will shrink toward the lower end of the range ("smallest").
   */
  final def integral[A](range: Range)(implicit I: Integral[A]): Gen[Random, A] =
    fromEffectSample(
      nextInt(range.end - range.start)
        .map(r => I.fromInt(r + range.start))
        .map(int => Sample(int, ZStream.fromIterable(range.drop(1).reverse).map(I.fromInt(_))))
    )

  /**
   * A sized generator, whose size falls within the specified bounds.
   */
  final def sized[R <: Random, A](min: Int, max: Int)(f: Int => Gen[R, A]): Gen[R, A] =
    integral[Int](min to max).flatMap(f)

  /**
   * A generator of uniformly distributed doubles between [0, 1].
   *
   * TODO: Make Shrinker go toward `0`
   */
  final def uniform: Gen[Random, Double] = Gen(ZStream.fromEffect(nextDouble.map(Sample.noShrink(_))))
}
