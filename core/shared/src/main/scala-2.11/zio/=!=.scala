package zio

import scala.annotation.implicitNotFound

/**
 * Evidence type `A` is not equal to type `B`.
 *
 * Based on https://stackoverflow.com/a/6944070.
 */
@implicitNotFound("${A} must not be ${B}")
trait =!=[A, B]

object =!= {

  implicit def neq[A, B] : A =!= B = new =!=[A, B] {}
  implicit def neqAmbig1[A] : A =!= A = ???
  implicit def neqAmbig2[A] : A =!= A = ???
}
