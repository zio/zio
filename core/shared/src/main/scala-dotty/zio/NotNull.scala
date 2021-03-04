package zio

import scala.annotation.implicitNotFound
import scala.util.NotGiven

/**
 * A value of type `NotNull[A]` provides implicit evidence that the type `A` is not `Null`.
 */
@implicitNotFound(
  "Null is being inferred, but it shouldn't. You probably need to add an explicit type parameter somewhere."
)
sealed abstract class NotNull[-A]

object NotNull extends NotNull[Any] {
  implicit def notNull[A](implicit ev: NotGiven[A =:= Null]): NotNull[A] = NotNull
}
