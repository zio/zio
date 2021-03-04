package zio

import scala.annotation.implicitNotFound

/**
 * A value of type `NotNull[A]` provides implicit evidence that the type `A` is not `Null`.
 */
@implicitNotFound(
  "Null is being inferred, but it shouldn't. You probably need to add an explicit type parameter somewhere."
)
sealed abstract class NotNull[-A]

object NotNull extends NotNull[Any] {

  implicit def notNull[A]: NotNull[A] = NotNull

  // Provide multiple ambiguous values so an implicit NotNull[Null] cannot be found.
  implicit val canFailAmbiguous1: NotNull[Null] = NotNull
  implicit val canFailAmbiguous2: NotNull[Null] = NotNull
}
