// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

/**
 * An `Exit[E, A]` describes the result of executing an `IO` value. The
 * result is either succeeded with a value `A`, or failed with a `Cause[E]`.
 */
sealed abstract class Exit[+E, +A] extends Product with Serializable { self =>
  import Exit._

  /**
   * Retrieves the `A` if succeeded, or else returns the specified default `A`.
   */
  final def getOrElse[A1 >: A](orElse: Cause[E] => A1): A1 = self match {
    case Success(value) => value
    case Failure(cause) => orElse(cause)
  }

  /**
   * Converts the `Exit` to an `Either[Throwable, A]`, by wrapping the
   * cause in `FiberFailure` (if the result is failed).
   */
  final def toEither: Either[Throwable, A] = self match {
    case Success(value) => Right(value)
    case Failure(cause) => Left(FiberFailure(cause))
  }

  /**
   * Maps over the error type.
   */
  final def leftMap[E1](f: E => E1): Exit[E1, A] =
    self match {
      case e @ Success(_) => e
      case Failure(c)     => fail(c.map(f))
    }

  /**
   * Maps over the value type.
   */
  final def map[A1](f: A => A1): Exit[E, A1] =
    self match {
      case Success(v)     => Exit.succeed(f(v))
      case e @ Failure(_) => e
    }

  /**
   * Flat maps over the value ty pe.
   */
  final def flatMap[E1 >: E, A1](f: A => Exit[E1, A1]): Exit[E1, A1] =
    self match {
      case Success(a)     => f(a)
      case e @ Failure(_) => e
    }

  /**
   * Maps over both the error and value type.
   */
  final def bimap[E1, A1](f: E => E1, g: A => A1): Exit[E1, A1] = leftMap(f).map(g)

  /**
   * Zips this result together with the specified result.
   */
  final def zip[E1 >: E, B](that: Exit[E1, B]): Exit[E1, (A, B)] = zipWith(that)((_, _), _ ++ _)

  /**
   * Zips this result together with the specified result, in parallel.
   */
  final def zipPar[E1 >: E, B](that: Exit[E1, B]): Exit[E1, (A, B)] = zipWith(that)((_, _), _ && _)

  /**
   * Zips this together with the specified result using the combination functions.
   */
  final def zipWith[E1 >: E, B, C](that: Exit[E1, B])(
    f: (A, B) => C,
    g: (Cause[E], Cause[E1]) => Cause[E1]
  ): Exit[E1, C] =
    (self, that) match {
      case (Success(a), Success(b)) => Exit.succeed(f(a, b))
      case (Failure(l), Failure(r)) => Exit.fail(g(l, r))
      case (e @ Failure(_), _)      => e
      case (_, e @ Failure(_))      => e
    }

  /**
   * Determines if the result is a success.
   */
  final def succeeded: Boolean = self match {
    case Success(_) => true
    case _          => false
  }

  /**
   * Determines if the result is interrupted.
   */
  final def interrupted: Boolean = self match {
    case Success(_) => false
    case Failure(c) => c.interrupted
  }

  /**
   * Folds over the value or cause.
   */
  final def fold[Z](failed: Cause[E] => Z, completed: A => Z): Z =
    self match {
      case Success(v)     => completed(v)
      case Failure(cause) => failed(cause)
    }

  /**
   * Effectfully folds over the value or cause.
   */
  final def redeem[E1, B](failed: Cause[E] => IO[E1, B], completed: A => IO[E1, B]): IO[E1, B] =
    self match {
      case Failure(cause) => failed(cause)
      case Success(v)     => completed(v)
    }
}

object Exit extends Serializable {

  final case class Success[A](value: A)        extends Exit[Nothing, A]
  final case class Failure[E](cause: Cause[E]) extends Exit[E, Nothing]

  final def succeed[A](a: A): Exit[Nothing, A]         = Success(a)
  final def fail[E](cause: Cause[E]): Exit[E, Nothing] = Failure(cause)

  final def checked[E](error: E): Exit[E, Nothing]          = fail(Cause.checked(error))
  final val interrupted: Exit[Nothing, Nothing]             = fail(Cause.interrupted)
  final def unchecked(t: Throwable): Exit[Nothing, Nothing] = fail(Cause.unchecked(t))

  final def fromOption[A](o: Option[A]): Exit[Unit, A] =
    o.fold[Exit[Unit, A]](checked(()))(succeed(_))

  final def fromEither[E, A](e: Either[E, A]): Exit[E, A] =
    e.fold(checked(_), succeed(_))

  final def fromTry[A](t: scala.util.Try[A]): Exit[Throwable, A] =
    t match {
      case scala.util.Success(a) => succeed(a)
      case scala.util.Failure(t) => checked(t)
    }

  final def flatten[E, A](exit: Exit[E, Exit[E, A]]): Exit[E, A] =
    exit.flatMap(identity _)

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

    final def isChecked: Boolean =
      self match {
        case Checked(_)        => true
        case Then(left, right) => left.isChecked || right.isChecked
        case Both(left, right) => left.isChecked || right.isChecked
        case _                 => false
      }

    final def interrupted: Boolean =
      self match {
        case Interruption      => true
        case Then(left, right) => left.interrupted || right.interrupted
        case Both(left, right) => left.interrupted || right.interrupted
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

  object Cause extends Serializable {

    final def checked[E](error: E): Cause[E] = Checked(error)

    final def unchecked(defect: Throwable): Cause[Nothing] = Unchecked(defect)

    final val interrupted: Cause[Nothing] = Interruption

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
