package scalaz.zio.stream

import scalaz.zio.IO

/**
 * A `Take[E, A]` represents a single `take` from a queue modeling a stream of
 * values. A `Take` may be a failure value `E`, an element value `A`, or end-of-
 * stream marker.
 */
sealed trait Take[+E, +A] { self =>
  final def map[B](f: A => B): Take[E, B] = self match {
    case t @ Take.Fail(_) => t
    case Take.Value(a)    => Take.Value(f(a))
    case Take.End         => Take.End
  }

  final def flatMap[E1 >: E, B](f: A => Take[E1, B]): Take[E1, B] = self match {
    case t @ Take.Fail(_) => t
    case Take.Value(a)    => f(a)
    case Take.End         => Take.End
  }

  final def zipWith[E1 >: E, B, C](that: Take[E1, B])(f: (A, B) => C): Take[E1, C] = (self, that) match {
    case (Take.Value(a), Take.Value(b)) => Take.Value(f(a, b))
    case (Take.End, _)                  => Take.End
    case (t @ Take.Fail(_), _)          => t
    case (_, Take.End)                  => Take.End
    case (_, t @ Take.Fail(_))          => t
  }

  final def zip[E1 >: E, B](that: Take[E1, B]): Take[E1, (A, B)] =
    self.zipWith(that)(_ -> _)
}
object Take {
  final case class Fail[E](value: E)  extends Take[E, Nothing]
  final case class Value[A](value: A) extends Take[Nothing, A]
  final case object End               extends Take[Nothing, Nothing]

  final def option[E, A](io: IO[E, Take[E, A]]): IO[E, Option[A]] =
    io.flatMap {
      case Take.End      => IO.now(None)
      case Take.Value(a) => IO.now(Some(a))
      case Take.Fail(e)  => IO.fail(e)
    }
}
