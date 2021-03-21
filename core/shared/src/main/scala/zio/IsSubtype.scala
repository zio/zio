package zio

import scala.annotation.implicitNotFound

@implicitNotFound("\nOutput Type Mismatch\n  expected: ${B}\n    actual: ${A}\n\n")
sealed abstract class HasOutput[-A, +B] extends (A => B)
object HasOutput {
  implicit def impl[A, B](implicit subtype: A <:< B): HasOutput[A, B] = new HasOutput[A, B] {
    override def apply(a: A): B = subtype(a)
  }

  implicit def implNothing[B]: HasOutput[Nothing, B] = new HasOutput[Nothing, B] {
    override def apply(a: Nothing): B = a
  }
}

@implicitNotFound("\nError Type Mismatch\n  expected: ${B}\n    actual: ${A}\n\n")
sealed abstract class HasError[-A, +B] extends (A => B)
object HasError {
  implicit def impl[A, B](implicit subtype: A <:< B): HasError[A, B] = new HasError[A, B] {
    override def apply(a: A): B = subtype(a)
  }

  implicit def implNothing[B]: HasError[Nothing, B] = new HasError[Nothing, B] {
    override def apply(a: Nothing): B = a
  }
}
