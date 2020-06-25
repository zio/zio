package zio.query

/**
 * A `Described[A]` is a value of type `A` along with a string description of
 * that value. The description may be used to generate a hash associated with
 * the value, so values that are equal should have the same description and
 * values that are not equal should have different descriptions.
 */
final case class Described[+A](value: A, description: String)

object Described {

  implicit class AnySyntax[A](private val value: A) extends AnyVal {
    def ?(description: String): Described[A] =
      Described(value, description)
  }
}
