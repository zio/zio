package zio.test

import scala.annotation.implicitNotFound

/**
 * A value of type `Eql[A, B]` provides implicit evidence that two values with
 * types `A` and `B` could potentially be equal, that is, that
 * `A` is a subtype of `B` or `B` is a subtype of `A`.
 */
@implicitNotFound(
  "This operation assumes that values of types ${A} and ${B} could " +
    "potentially be equal. However, ${A} and ${B} are unrelated types, so " +
    "they cannot be equal."
)
sealed trait Eql[A, B]

object Eql {
  implicit final def eqlReflexive[A]: Eql[A, A]        = new Eql[A, A] {}
  implicit final def eqlSubtype1[A <: B, B]: Eql[A, B] = new Eql[A, B] {}
  implicit final def eqlSubtype2[A, B <: A]: Eql[A, B] = new Eql[A, B] {}
}
