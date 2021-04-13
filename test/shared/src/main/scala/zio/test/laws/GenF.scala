/*
 * Copyright 2020-2021 John A. De Goes and the ZIO Contributors
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

package zio.test.laws

import zio.Chunk
import zio.random.Random
import zio.test.{Gen, Sized}

/**
 * A `GenF` knows how to construct a generator of `F[A]` values given a
 * generator of `A` values for any `A`. For example, a `GenF` of `List` values
 * knows how to generate lists with elements given a generator of elements of
 * that type. You can think of `GenF` as a "recipe" for building generators
 * for parameterized types.
 */
trait GenF[-R, F[_]] {

  /**
   * Construct a generator of `F[A]` values given a generator of `A` values.
   */
  def apply[R1 <: R, A](gen: Gen[R1, A]): Gen[R1, F[A]]
}

object GenF {

  /**
   * A generator of `Chunk` values.
   */
  val chunk: GenF[Random with Sized, Chunk] =
    new GenF[Random with Sized, Chunk] {
      def apply[R1 <: Random with Sized, A](gen: Gen[R1, A]): Gen[R1, Chunk[A]] =
        Gen.chunkOf(gen)
    }

  /**
   * A generator of `Either` values.
   */
  def either[R <: Random, A](a: Gen[R, A]): GenF[R, ({ type lambda[+x] = Either[A, x] })#lambda] =
    new GenF[R, ({ type lambda[+x] = Either[A, x] })#lambda] {
      def apply[R1 <: R, B](b: Gen[R1, B]): Gen[R1, Either[A, B]] =
        Gen.either(a, b)
    }

  /**
   * A generator of `List` values.
   */
  val list: GenF[Random with Sized, List] =
    new GenF[Random with Sized, List] {
      def apply[R1 <: Random with Sized, A](gen: Gen[R1, A]): Gen[R1, List[A]] =
        Gen.listOf(gen)
    }

  /**
   * A generator of `Map` values.
   */
  def map[R <: Random with Sized, A](a: Gen[R, A]): GenF[R, ({ type lambda[+x] = Map[A, x] })#lambda] =
    new GenF[R, ({ type lambda[+x] = Map[A, x] })#lambda] {
      def apply[R1 <: R, B](b: Gen[R1, B]): Gen[R1, Map[A, B]] =
        Gen.mapOf(a, b)
    }

  /**
   * A generator of `Option` values.
   */
  val option: GenF[Random, Option] =
    new GenF[Random, Option] {
      def apply[R1 <: Random, A](gen: Gen[R1, A]): Gen[R1, Option[A]] =
        Gen.option(gen)
    }

  /**
   * A generator of `Set` values.
   */
  val set: GenF[Random with Sized, Set] =
    new GenF[Random with Sized, Set] {
      def apply[R1 <: Random with Sized, A](gen: Gen[R1, A]): Gen[R1, Set[A]] =
        Gen.setOf(gen)
    }

  /**
   * A generator of `Vector` values.
   */
  val vector: GenF[Random with Sized, Vector] =
    new GenF[Random with Sized, Vector] {
      def apply[R1 <: Random with Sized, A](gen: Gen[R1, A]): Gen[R1, Vector[A]] =
        Gen.vectorOf(gen)
    }
}
