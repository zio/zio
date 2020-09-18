package zio.test.laws

import zio.random.Random
import zio.test.{ FunctionVariants, Gen }

/**
 * A `GenF` knows how to construct a generator of `F[A,B]` values given a
 * generator of `A` and generator of `B` values. For example, a `GenF2` of `Function1` values
 * knows how to generate functions A => B with elements given a generator of elements of
 * that type `B`.
 */
trait GenF2[-R, F[_,_]] {

  /**
   * Construct a generator of `F[A,B]` values given a generators of `A` and `B` values.
   */
  def apply[R1 <: R, A, B](gen: Gen[R1, B]): Gen[R1, F[A, B]]
}

object GenF2 extends FunctionVariants {

  /**
   * A generator of `Function1` A => B values.
   */
  val function1: GenF2[Random, Function1] =
    new GenF2[Random, Function1] {

      override def apply[R1 <: Random, A, B](gen: Gen[R1, B]): Gen[R1, Function1[A, B]] =
        function[R1,A,B](gen)
    }
}
