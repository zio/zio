package zio.test.laws

import zio.random.Random
import zio.test.{ FunctionVariants, Gen }

/**
 * A `GenF` knows how to construct a generator of `F[A]` values given a
 * generator of `A` values for any `A`. For example, a `GenF` of `List` values
 * knows how to generate lists with elements given a generator of elements of
 * that type. You can think of `GenF` as a "recipe" for building generators
 * for parameterized types.
 */
trait GenF2[-R, F[-_, +_]] {

  /**
   * Construct a generator of `F[A,B]` values given a generators of `A` and `B` values.
   */
  def apply[R1 <: R, A, B](gen: Gen[R1, B]): Gen[R, F[A, B]]
}

object GenF2 extends FunctionVariants {

  /**
   * A generator of `Function1` A => B values.
   */
  val function1: GenF2[Random, Function1] =
    new GenF2[Random, Function1] {

      /**
       * Construct a generator of `F[A,B]` values given a generators of `A` and `B` values.
       */
      override def apply[R1 <: Random, A, B](gen: Gen[R1, B]): Gen[Random, Function1[A, B]] =
        //function[Random,A,B](gen) // declaration side variance ... is not happy
        ???
    }
}
