package zio.blocks.schema.binding

/**
 * A {{Matcher}} is a typeclass that can match a value of type `A` with a
 * specific term of its sum type.
 */
trait Matcher[+A] {

  /**
   * Downcasts a value of type `Any` to a value of type `A` or return `null`.
   */
  def downcastOrNull(any: Any): A

  /**
   * Downcasts a value of type `Any` to a value of type `A` and box it to `Some`
   * or return `None`.
   */
  final def downcastOption(any: Any): Option[A] = Option(downcastOrNull(any))
}
object Matcher {
  def apply[A](implicit m: Matcher[A]): Matcher[A] = m

  implicit def some[A]: Matcher[Some[A]] = _someMatcher.asInstanceOf[Matcher[Some[A]]]

  implicit val none: Matcher[None.type] = _noneMatcher

  implicit def left[A, B]: Matcher[Left[A, B]] = _leftMatcher.asInstanceOf[Matcher[Left[A, B]]]

  implicit def right[A, B]: Matcher[Right[A, B]] = _rightMatcher.asInstanceOf[Matcher[Right[A, B]]]

  implicit def success[A]: Matcher[scala.util.Success[A]] = _successMatcher.asInstanceOf[Matcher[scala.util.Success[A]]]

  implicit def failure[A]: Matcher[scala.util.Failure[A]] = _failureMatcher.asInstanceOf[Matcher[scala.util.Failure[A]]]

  private val _someMatcher: Matcher[Some[Any]] = new Matcher[Some[Any]] {
    def downcastOrNull(any: Any): Some[Any] = any match {
      case x: Some[_] => x
      case _          => null
    }
  }

  private val _noneMatcher: Matcher[None.type] = new Matcher[None.type] {
    def downcastOrNull(any: Any): None.type = any match {
      case None => None
      case _    => null
    }
  }

  private val _leftMatcher: Matcher[Left[Any, Any]] = new Matcher[Left[Any, Any]] {
    def downcastOrNull(any: Any): Left[Any, Any] = any match {
      case x: Left[_, _] => x
      case _             => null
    }
  }

  private val _rightMatcher: Matcher[Right[Any, Any]] = new Matcher[Right[Any, Any]] {
    def downcastOrNull(any: Any): Right[Any, Any] = any match {
      case x: Right[_, _] => x
      case _              => null
    }
  }

  private val _successMatcher: Matcher[scala.util.Success[Any]] = new Matcher[scala.util.Success[Any]] {
    def downcastOrNull(any: Any): scala.util.Success[Any] = any match {
      case x: scala.util.Success[_] => x
      case _                        => null
    }
  }

  private val _failureMatcher: Matcher[scala.util.Failure[Any]] = new Matcher[scala.util.Failure[Any]] {
    def downcastOrNull(any: Any): scala.util.Failure[Any] = any match {
      case x: scala.util.Failure[_] => x
      case _                        => null
    }
  }
}
