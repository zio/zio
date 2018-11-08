// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scalaz.zio.Errors.FiberFailure

/**
 * A description of the result of executing an `IO` value. The result is either
 * completed with a value, failed because of an uncaught `E`, or terminated
 * due to interruption or runtime error.
 */
sealed abstract class ExitResult[+E, +A] extends Product with Serializable { self =>
  import ExitResult._

  final def toEither: Either[Throwable, A] = self match {
    case Succeeded(value) => Right(value)
    case Failed(cause)    => Left(FiberFailure(cause))
  }

  final def leftMap[E1](f: E => E1): ExitResult[E1, A] =
    self match {
      case e @ Succeeded(_) => e
      case Failed(c)        => failed(c.map(f))
    }

  final def map[A1](f: A => A1): ExitResult[E, A1] =
    self match {
      case Succeeded(v)  => ExitResult.succeeded(f(v))
      case e @ Failed(_) => e
    }

  final def bimap[E1, A1](f: E => E1, g: A => A1): ExitResult[E1, A1] =
    leftMap(f).map(g)

  final def zip[E1 >: E, B](that: ExitResult[E1, B]): ExitResult[E1, (A, B)] = zipWith(that)((_, _), _ ++ _)

  final def zipPar[E1 >: E, B](that: ExitResult[E1, B]): ExitResult[E1, (A, B)] = zipWith(that)((_, _), _ && _)

  final def zipWith[E1 >: E, B, C](that: ExitResult[E1, B])(
    f: (A, B) => C,
    g: (Cause[E], Cause[E1]) => Cause[E1]
  ): ExitResult[E1, C] =
    (self, that) match {
      case (Succeeded(a), Succeeded(b)) => ExitResult.succeeded(f(a, b))
      case (Failed(l), Failed(r))       => ExitResult.failed(g(l, r))
      case (e @ Failed(_), _)           => e
      case (_, e @ Failed(_))           => e
    }

  final def <>[E1, A1 >: A](that: ExitResult[E1, A1]): ExitResult[E1, A1] = self match {
    case Failed(cause) if cause.isChecked => that
    case _                                => self.asInstanceOf[ExitResult[E1, A1]]
  }

  final def succeeded: Boolean = self match {
    case Succeeded(_) => true
    case _            => false
  }

  final def fold[Z](completed: A => Z, terminated: Cause[E] => Z): Z =
    self match {
      case Succeeded(v)  => completed(v)
      case Failed(cause) => terminated(cause)
    }
}
object ExitResult {

  final case class Succeeded[A](value: A)     extends ExitResult[Nothing, A]
  final case class Failed[E](cause: Cause[E]) extends ExitResult[E, Nothing]

  final def succeeded[A](a: A): ExitResult[Nothing, A]         = Succeeded(a)
  final def failed[E](cause: Cause[E]): ExitResult[E, Nothing] = Failed(cause)

  final def checked[E](error: E): ExitResult[E, Nothing]          = failed(Cause.checked(error))
  final def interrupted: ExitResult[Nothing, Nothing]             = failed(Cause.interrupted)
  final def unchecked(t: Throwable): ExitResult[Nothing, Nothing] = failed(Cause.unchecked(t))

  sealed abstract class Cause[+E] extends Product with Serializable { self =>
    import Cause._
    final def ++[E1 >: E](that: Cause[E1]): Cause[E1] =
      Then(self, that)

    final def &&[E1 >: E](that: Cause[E1]): Cause[E1] =
      Both(self, that)

    final def map[E1](f: E => E1): Cause[E1] = self match {
      case Checked(value)   => Checked(f(value))
      case c @ Unchecked(_) => c
      case Interruption     => Interruption

      case Then(left, right) => Then(left.map(f), right.map(f))
      case Both(left, right) => Both(left.map(f), right.map(f))
    }

    final def isUnchecked: Boolean =
      self match {
        case Unchecked(_)      => true
        case Then(left, right) => left.isUnchecked || right.isUnchecked
        case Both(left, right) => left.isUnchecked || right.isUnchecked
        case _                 => false
      }

    final def isChecked: Boolean =
      self match {
        case Checked(_)        => true
        case Then(left, right) => left.isChecked || right.isChecked
        case Both(left, right) => left.isChecked || right.isChecked
        case _                 => false
      }

    final def isInterrupted: Boolean =
      self match {
        case Interruption      => true
        case Then(left, right) => left.isInterrupted || right.isInterrupted
        case Both(left, right) => left.isInterrupted || right.isInterrupted
        case _                 => false
      }

    final def checked[E1 >: E]: List[E1] =
      self
        .fold(List.empty[E1]) {
          case (z, Checked(v)) => v :: z
        }
        .reverse

    final def unchecked: List[Throwable] =
      self
        .fold(List.empty[Throwable]) {
          case (z, Unchecked(v)) => v :: z
        }
        .reverse

    final def fold[Z](z: Z)(f: PartialFunction[(Z, Cause[E]), Z]): Z =
      (f.lift(z -> self).getOrElse(z), self) match {
        case (z, Then(left, right)) => right.fold(left.fold(z)(f))(f)
        case (z, Both(left, right)) => right.fold(left.fold(z)(f))(f)

        case (z, _) => z
      }

    final def checkedOrRefail: Either[E, Cause[Nothing]] = self.checked.headOption match {
      case Some(error) => Left(error)
      case None        => Right(self.asInstanceOf[Cause[Nothing]]) // no E inside this cause, can safely cast
    }
  }

  object Cause {

    final def checked[E](error: E): Cause[E] = Checked(error)

    final def unchecked(defect: Throwable): Cause[Nothing] = Unchecked(defect)

    final def interrupted: Cause[Nothing] = Interruption

    final case class Checked[E](value: E)        extends Cause[E]
    final case class Unchecked(value: Throwable) extends Cause[Nothing]
    final case object Interruption               extends Cause[Nothing]

    final case class Then[E](left: Cause[E], right: Cause[E]) extends Cause[E] { self =>
      final def flatten: Set[Cause[E]] = {
        def flattenThen(c: Cause[E]): Set[Cause[E]] = c match {
          case Then(left, right) => flattenThen(left) ++ flattenThen(right)
          case x                 => Set(x)
        }

        flattenThen(left) ++ flattenThen(right)
      }

      override final def hashCode: Int = flatten.hashCode

      override final def equals(that: Any): Boolean = that match {
        case that: Then[_] => self.flatten.equals(that.flatten)
        case _             => false
      }
    }
    final case class Both[E](left: Cause[E], right: Cause[E]) extends Cause[E] { self =>
      final def flatten: Set[Cause[E]] = {
        def flattenBoth(c: Cause[E]): Set[Cause[E]] = c match {
          case Both(left, right) => flattenBoth(left) ++ flattenBoth(right)
          case x                 => Set(x)
        }

        flattenBoth(left) ++ flattenBoth(right)
      }

      override final def hashCode: Int = flatten.hashCode

      override final def equals(that: Any): Boolean = that match {
        case that: Both[_] => self.flatten.equals(that.flatten)
        case _             => false
      }
    }
  }
}
