/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.Gen
import zio.Chunk
import zio.Trace

/**
 * A `GenF` knows how to construct a generator of `F[A]` values given a
 * generator of `A` values for any `A`. For example, a `GenF` of `List` values
 * knows how to generate lists with elements given a generator of elements of
 * that type. You can think of `GenF` as a "recipe" for building generators for
 * parameterized types.
 *
 * The laws in `ZLawsF` take a `GenF` to build the `F[A]` values they check. The
 * companion object has instances for `Chunk`, `Either`, `List`, `Map`,
 * `Option`, `Set` and `Vector`.
 *
 * {{{
 * import zio.Trace
 * import zio.test._
 *
 * val intLists: Gen[Any, List[Int]] = GenF.list(Gen.int(1, 10))
 *
 * val nonEmptyList: GenF[Any, List] = new GenF[Any, List] {
 *   def apply[R1, A](gen: Gen[R1, A])(implicit trace: Trace): Gen[R1, List[A]] =
 *     Gen.listOf1(gen)
 * }
 *
 * val names: Gen[Any, List[String]] = nonEmptyList(Gen.alphaNumericString)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
trait GenF[-R, F[_]] {

  /**
   * Construct a generator of `F[A]` values given a generator of `A` values.
   */
  def apply[R1 <: R, A](gen: Gen[R1, A])(implicit trace: Trace): Gen[R1, F[A]]
}

/**
 * `GenF` instances for container types of the Scala standard library and ZIO.
 */
object GenF {

  /**
   * A `GenF` of `Chunk` values: it builds a generator of chunks with the sized
   * generator `Gen.chunkOf`.
   */
  val chunk: GenF[Any, Chunk] =
    new GenF[Any, Chunk] {
      def apply[R1, A](gen: Gen[R1, A])(implicit
        trace: Trace
      ): Gen[R1, Chunk[A]] =
        Gen.chunkOf(gen)
    }

  /**
   * A `GenF` of `Either` values with left values drawn from `a`: given a
   * generator `b` of right values, it builds a generator of `Either[A, B]` with
   * `Gen.either(a, b)`.
   */
  def either[R, A](a: Gen[R, A]): GenF[R, ({ type lambda[+x] = Either[A, x] })#lambda] =
    new GenF[R, ({ type lambda[+x] = Either[A, x] })#lambda] {
      def apply[R1 <: R, B](b: Gen[R1, B])(implicit trace: Trace): Gen[R1, Either[A, B]] =
        Gen.either(a, b)
    }

  /**
   * A `GenF` of `List` values: it builds a generator of lists with the sized
   * generator `Gen.listOf`.
   */
  val list: GenF[Any, List] =
    new GenF[Any, List] {
      def apply[R1, A](gen: Gen[R1, A])(implicit
        trace: Trace
      ): Gen[R1, List[A]] =
        Gen.listOf(gen)
    }

  /**
   * A `GenF` of `Map` values with keys drawn from `a`: given a generator `b` of
   * values, it builds a generator of `Map[A, B]` with the sized generator
   * `Gen.mapOf(a, b)`.
   */
  def map[R, A](a: Gen[R, A]): GenF[R, ({ type lambda[+x] = Map[A, x] })#lambda] =
    new GenF[R, ({ type lambda[+x] = Map[A, x] })#lambda] {
      def apply[R1 <: R, B](b: Gen[R1, B])(implicit trace: Trace): Gen[R1, Map[A, B]] =
        Gen.mapOf(a, b)
    }

  /**
   * A `GenF` of `Option` values: it builds a generator of `Option[A]` with
   * `Gen.option`, which generates both `None` and `Some` values.
   */
  val option: GenF[Any, Option] =
    new GenF[Any, Option] {
      def apply[R1, A](gen: Gen[R1, A])(implicit trace: Trace): Gen[R1, Option[A]] =
        Gen.option(gen)
    }

  /**
   * A `GenF` of `Set` values: it builds a generator of sets with the sized
   * generator `Gen.setOf`.
   */
  val set: GenF[Any, Set] =
    new GenF[Any, Set] {
      def apply[R1, A](gen: Gen[R1, A])(implicit trace: Trace): Gen[R1, Set[A]] =
        Gen.setOf(gen)
    }

  /**
   * A `GenF` of `Vector` values: it builds a generator of vectors with the
   * sized generator `Gen.vectorOf`.
   */
  val vector: GenF[Any, Vector] =
    new GenF[Any, Vector] {
      def apply[R1, A](gen: Gen[R1, A])(implicit
        trace: Trace
      ): Gen[R1, Vector[A]] =
        Gen.vectorOf(gen)
    }
}
