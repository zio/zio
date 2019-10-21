package zio

import scala.annotation.implicitAmbiguous

/**
 * Evidence type `A` is not equal to type `B`.
 *
 * Based on https://stackoverflow.com/a/6944070.
 */
trait =!=[A, B]

object =!= {

  implicit def neq[A, B]: A =!= B = new =!=[A, B] {}

  @implicitAmbiguous("Cannot prove that ${A} =!= ${A}")
  implicit def neqAmbig1[A]: A =!= A = ???
  implicit def neqAmbig2[A]: A =!= A = ???
}
