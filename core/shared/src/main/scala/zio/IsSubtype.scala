package zio

import scala.annotation.implicitNotFound

@implicitNotFound("This operator requires that the output type be a subtype of ${B} but the actual type was ${A}.")
sealed abstract class IsSubtypeOfOutput[-A, +B] extends (A => B) with Serializable
object IsSubtypeOfOutput {
  implicit def impl[A, B](implicit subtype: A <:< B): IsSubtypeOfOutput[A, B] = new IsSubtypeOfOutput[A, B] {
    override def apply(a: A): B = subtype(a)
  }

  implicit def implNothing[B]: IsSubtypeOfOutput[Nothing, B] = new IsSubtypeOfOutput[Nothing, B] {
    override def apply(a: Nothing): B = a
  }
}

@implicitNotFound("This operator requires that the error type be a subtype of ${B} but the actual type was ${A}.")
sealed abstract class IsSubtypeOfError[-A, +B] extends (A => B) with Serializable
object IsSubtypeOfError {
  implicit def impl[A, B](implicit subtype: A <:< B): IsSubtypeOfError[A, B] = new IsSubtypeOfError[A, B] {
    override def apply(a: A): B = subtype(a)
  }

  implicit def implNothing[B]: IsSubtypeOfError[Nothing, B] = new IsSubtypeOfError[Nothing, B] {
    override def apply(a: Nothing): B = a
  }
}
