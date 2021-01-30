package zio.internal.macros

import zio._
import zio.internal.macros.Validation.{Failure, Success}

sealed trait Validation[+E, +A] { self =>

  /**
   * A symbolic alias for `zipParLeft`.
   */
  final def <&[E1 >: E, B](that: Validation[E1, B]): Validation[E1, A] =
    zipParLeft(that)

  /**
   * A symbolic alias for `zipParRight`.
   */
  final def &>[E1 >: E, B](that: Validation[E1, B]): Validation[E1, B] =
    zipParRight(that)

  /**
   * A symbolic alias for `zipPar`.
   */
  final def <&>[E1 >: E, B](that: Validation[E1, B]): Validation[E1, (A, B)] =
    zipPar(that)

  /**
   * Returns whether this `Validation` and the specified `Validation` are equal
   * to each other.
   */
  override final def equals(that: Any): Boolean =
    (self, that) match {
      case (self, that: AnyRef) if self.eq(that) => true
      case (Failure(es), Failure(e1s))           => es.groupBy(identity) == e1s.groupBy(identity)
      case (Success(a), Success(a1))             => a == a1
      case _                                     => false
    }

  /**
   * Transforms the value of this `Validation` with the specified validation
   * function if it is a success or returns the value unchanged otherwise.
   */
  final def flatMap[E1 >: E, B](f: A => Validation[E1, B]): Validation[E1, B] =
    self match {
      case Failure(es) => Failure(es)
      case Success(a)  => f(a)
    }

  /**
   * Folds over the error and success values of this `Validation`.
   */
  final def fold[B](failure: NonEmptyChunk[E] => B, success: A => B): B =
    self match {
      case Failure(es) => failure(es)
      case Success(a)  => success(a)
    }

  /**
   * Transforms the successful value of this `Validation` with the specified
   * function.
   */
  final def map[B](f: A => B): Validation[E, B] =
    self match {
      case Success(a)           => Success(f(a))
      case failure @ Failure(_) => failure
    }

  /**
   * Transforms the error value of this `Validation` with the specified
   * function.
   */
  final def mapError[E1](f: E => E1): Validation[E1, A] =
    self match {
      case Failure(es)          => Failure(es.map(f))
      case success @ Success(_) => success
    }

  /**
   * Transforms this `Validation` to an `Either`.
   */
  final def toEither[E1 >: E]: Either[NonEmptyChunk[E1], A] =
    fold(Left(_), Right(_))

  /**
   * Transforms this `Validation` to an `Option`, discarding information about
   * the errors.
   */
  final def toOption: Option[A] =
    fold(_ => None, Some(_))

  /**
   * Transforms this `Validation` to a `Try`, discarding all but the first error.
   */
  final def toTry(implicit ev: E <:< Throwable): scala.util.Try[A] =
    fold(e => scala.util.Failure(ev(e.head)), scala.util.Success(_))

  /**
   * Converts this `Validation` into a `ZIO` effect.
   */
  final def toZIO: IO[NonEmptyChunk[E], A] = ZIO.fromEither(self.toEither)

  /**
   * A variant of `zipPar` that keeps only the left success value, but returns
   * a failure with all errors if either this `Validation` or the specified
   * `Validation` fail.
   */
  final def zipParLeft[E1 >: E, B](that: Validation[E1, B]): Validation[E1, A] =
    zipWithPar(that)((a, _) => a)

  /**
   * A variant of `zipPar` that keeps only the right success value, but returns
   * a failure with all errors if either this `Validation` or the specified
   * `Validation` fail.
   */
  final def zipParRight[E1 >: E, B](that: Validation[E1, B]): Validation[E1, B] =
    zipWithPar(that)((_, b) => b)

  /**
   * Combines this `Validation` with the specified `Validation`, returning a
   * tuple of their results. Returns either the combined result if both were
   * successes or otherwise returns a failure with all errors.
   */
  final def zipPar[E1 >: E, B](that: Validation[E1, B]): Validation[E1, (A, B)] =
    zipWithPar(that)((_, _))

  /**
   * Combines this `Validation` with the specified `Validation`, using the
   * function `f` to combine their success values. Returns either the combined
   * result if both were successes or otherwise returns a failure with all
   * errors.
   */
  final def zipWithPar[E1 >: E, B, C](that: Validation[E1, B])(f: (A, B) => C): Validation[E1, C] =
    (self, that) match {
      case (Failure(es), Failure(e1s)) => Failure(es ++ e1s)
      case (failure @ Failure(_), _)   => failure
      case (_, failure @ Failure(_))   => failure
      case (Success(a), Success(b))    => Success(f(a, b))
    }
}

object Validation {

  final case class Failure[+E](errors: NonEmptyChunk[E]) extends Validation[E, Nothing]
  final case class Success[+A](value: A)                 extends Validation[Nothing, A]

  def succeed[E, A](value: A): Validation[E, A] = Success(value)
  def fail[E, A](error: E): Validation[E, A]    = Failure(NonEmptyChunk(error))

  def fromEither[E, A](either: Either[E, A]): Validation[E, A] =
    either.fold(fail, succeed)
}
