// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio
import scalaz.zio.ExitResult.Cause.Failure

/**
 * A description of the result of executing an `IO` value. The result is either
 * completed with a value, failed because of an uncaught `E`, or terminated
 * due to interruption or runtime error.
 */
sealed abstract class ExitResult[+E, +A] extends Product with Serializable { self =>
  import ExitResult._

  final def toEither: Either[Throwable, A] = self match {
    case Completed(value)  => Right(value)
    case Terminated(cause) => Left(cause.toThrowable())
  }

  final def leftMap[E1](f: E => E1): ExitResult[E1, A] =
    self match {
      case e @ Completed(_) => e
      case Terminated(c)    => Terminated(c.map(f))
    }

  final def map[A1](f: A => A1): ExitResult[E, A1] =
    self match {
      case Completed(v)      => Completed(f(v))
      case e @ Terminated(_) => e
    }

  final def bimap[E1, A1](f: E => E1, g: A => A1): ExitResult[E1, A1] =
    leftMap(f).map(g)

  final def zip[E1 >: E, B](that: ExitResult[E1, B]): ExitResult[E1, (A, B)] =
    (self, that) match {
      case (Completed(a), Completed(b))   => Completed(a -> b)
      case (Terminated(l), Terminated(r)) => Terminated(l ++ r)
      case (e @ Terminated(_), _)         => e
      case (_, e @ Terminated(_))         => e
    }

  final def zipPar[E1 >: E, B](that: ExitResult[E1, B]): ExitResult[E1, (A, B)] =
    (self, that) match {
      case (Completed(a), Completed(b))   => Completed(a -> b)
      case (Terminated(l), Terminated(r)) => Terminated(l && r)
      case (e @ Terminated(_), _)         => e
      case (_, e @ Terminated(_))         => e
    }

  final def zipWith[E1 >: E, B, C](that: ExitResult[E1, B])(f: (A, B) => C): ExitResult[E1, C] =
    self.zip(that).map(f.tupled)

  final def exceptions: Option[List[Throwable]] = self match {
    case Completed(_)      => None
    case Terminated(cause) => Some(cause.exceptions)
  }

  final def mapError[E2, A1 >: A](f: E => ExitResult[E2, A1]): ExitResult[E2, A1] = self match {
    case ExitResult.Terminated(Failure(e)) => f(e)
    case x                                 => x.asInstanceOf[ExitResult[E2, A1]]
  }

  final def succeeded: Boolean = self match {
    case Completed(_) => true
    case _            => false
  }

  final def fold[Z](completed: A => Z, terminated: Cause[E] => Z): Z =
    self match {
      case Completed(v)      => completed(v)
      case Terminated(cause) => terminated(cause)
    }
}
object ExitResult {

  final case class Completed[A](value: A)         extends ExitResult[Nothing, A]
  final case class Terminated[E](cause: Cause[E]) extends ExitResult[E, Nothing]

  sealed abstract class Cause[+E] extends Product with Serializable { self =>
    import Cause._
    final def ++[E1 >: E](that: Cause[E1]): Cause[E1] =
      Then(self, that)

    final def &&[E1 >: E](that: Cause[E1]): Cause[E1] =
      Both(self, that)

    final def map[E1](f: E => E1): Cause[E1] = self match {
      case Failure(value)      => Failure(f(value))
      case c @ Exception(_)    => c
      case c @ Interruption(_) => c

      case Then(left, right) => Then(left.map(f), right.map(f))
      case Both(left, right) => Both(left.map(f), right.map(f))
    }

    final def failures[E1 >: E]: Set[E1] =
      self.fold(Set.empty[E1]) {
        case (z, Failure(v)) => z + v
      }

    final def exceptions: List[Throwable] =
      self
        .fold(List.empty[Throwable]) {
          case (z, Exception(v)) => v :: z
        }
        .reverse

    final def interruptions: Option[List[Throwable]] =
      self
        .fold(Option.empty[List[Throwable]]) {
          case (z, Interruption(Some(v))) =>
            z.fold(Some(v :: Nil))(vs => Some(v :: vs))
          case (z, Interruption(None)) =>
            z.fold(Some(List[Throwable]()))(vs => Some(vs))
        }
        .map(_.reverse)

    final def fold[Z](z: Z)(f: PartialFunction[(Z, Cause[E]), Z]): Z =
      (f.lift(z -> self).getOrElse(z), self) match {
        case (z, Then(left, right)) => right.fold(left.fold(z)(f))(f)
        case (z, Both(left, right)) => right.fold(left.fold(z)(f))(f)

        case (z, _) => z
      }

    final def toThrowable(causes: List[Throwable] = Nil): Throwable =
      self match {
        case Failure(error)      => Errors.UnhandledError(error, causes)
        case Exception(defect)   => Errors.TerminatedFiber(defect, causes)
        case Interruption(cause) => Errors.InterruptedFiber(cause.map(_ :: Nil).getOrElse(Nil), causes)
        case Then(left, right)   => left.toThrowable(right.toThrowable() :: causes)
        case Both(left, right)   => Errors.ParallelFiberError(left.toThrowable(), right.toThrowable(), causes)
      }

    final def failure: Option[E] = self match {
      case Failure(e)      => Some(e)
      case Exception(_)    => None
      case Interruption(_) => None
      case Then(left, _)   => left.failure
      case Both(_, _)      => None
    }
  }
  object Cause {
    final def point[E](e: => E): Cause[E] = Failure(e)

    final def failure[E](error: E, defects: List[Throwable] = Nil): Cause[E] = Cause(Failure(error), defects)

    final def exception(defect: Throwable, defects: List[Throwable] = Nil): Cause[Nothing] =
      Cause(Exception(defect), defects)

    final def interruption(causes: List[Throwable], defects: List[Throwable] = Nil): Cause[Nothing] =
      causes match {
        case Nil => Cause(Interruption(None), defects)
        case head :: tail =>
          tail.foldLeft(Cause(Interruption(Some(head)), defects))(
            (causes, cause) => causes ++ Interruption(Some(cause))
          )
      }

    final def apply[E](cause: Cause[E], defects: List[Throwable]): Cause[E] =
      defects.foldLeft[Cause[E]](cause)((causes, defect) => causes ++ Exception(defect))

    final case class Failure[E](value: E)                   extends Cause[E]
    final case class Exception(value: Throwable)            extends Cause[Nothing]
    final case class Interruption(value: Option[Throwable]) extends Cause[Nothing]

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
