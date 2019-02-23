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
  final def mapError[E1](f: E => E1): Exit[E1, A] =
    self match {
      case e @ Success(_) => e
      case Failure(c)     => halt(c.map(f))
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
   * Flat maps over the value type.
   */
  final def flatMap[E1 >: E, A1](f: A => Exit[E1, A1]): Exit[E1, A1] =
    self match {
      case Success(a)     => f(a)
      case e @ Failure(_) => e
    }

  /**
   * Maps over both the error and value type.
   */
  final def bimap[E1, A1](f: E => E1, g: A => A1): Exit[E1, A1] = mapError(f).map(g)

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
      case (Failure(l), Failure(r)) => Exit.halt(g(l, r))
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
  final def halt[E](cause: Cause[E]): Exit[E, Nothing] = Failure(cause)

  final def fail[E](error: E): Exit[E, Nothing]       = halt(Cause.fail(error))
  final val interrupt: Exit[Nothing, Nothing]         = halt(Cause.interrupt)
  final def die(t: Throwable): Exit[Nothing, Nothing] = halt(Cause.die(t))

  final def fromOption[A](o: Option[A]): Exit[Unit, A] =
    o.fold[Exit[Unit, A]](fail(()))(succeed)

  final def fromEither[E, A](e: Either[E, A]): Exit[E, A] =
    e.fold(fail, succeed)

  final def fromTry[A](t: scala.util.Try[A]): Exit[Throwable, A] =
    t match {
      case scala.util.Success(a) => succeed(a)
      case scala.util.Failure(t) => fail(t)
    }

  final def flatten[E, A](exit: Exit[E, Exit[E, A]]): Exit[E, A] =
    exit.flatMap(identity)

  sealed abstract class Cause[+E] extends Product with Serializable { self =>
    import Cause._
    final def ++[E1 >: E](that: Cause[E1]): Cause[E1] =
      Then(self, that)

    final def &&[E1 >: E](that: Cause[E1]): Cause[E1] =
      Both(self, that)

    final def map[E1](f: E => E1): Cause[E1] = self match {
      case Fail(value) => Fail(f(value))
      case c @ Die(_)  => c
      case Interrupt   => Interrupt

      case Then(left, right)      => Then(left.map(f), right.map(f))
      case Both(left, right)      => Both(left.map(f), right.map(f))
      case c @ FinalizerErrors(_) => c
    }

    final def isFailure: Boolean =
      self match {
        case Fail(_)           => true
        case Then(left, right) => left.isFailure || right.isFailure
        case Both(left, right) => left.isFailure || right.isFailure
        case _                 => false
      }

    final def interrupted: Boolean =
      self match {
        case Interrupt         => true
        case Then(left, right) => left.interrupted || right.interrupted
        case Both(left, right) => left.interrupted || right.interrupted
        case _                 => false
      }

    /**
     * Collects typed errors that caused their respective fibers to exit.
     */
    final def failures[E1 >: E]: List[E1] =
      self
        .fold(List.empty[E1]) {
          case (z, Fail(v)) => v :: z
        }
        .reverse

    /**
     * Collect defects that caused their respective fibers to exit, omits [[finalizerDefects]] that could arise during cleanup
     */
    final def defects: List[Throwable] =
      self
        .fold(List.empty[Throwable]) {
          case (z, Die(v)) => v :: z
        }
        .reverse

    /**
     * Collect only defects that occurred during cleanup after failure
     *
     * Unlike other errors, these are NOT the direct causes of a failure
     */
    final def finalizerDefects: List[Throwable] =
      self
        .fold(List.empty[FinalizerErrors]) {
          case (z, v @ FinalizerErrors(_)) => v :: z
        }
        .reverse
        .flatMap(_.flatten.defects)

    final def fold[Z](z: Z)(f: PartialFunction[(Z, Cause[E]), Z]): Z =
      (f.lift(z -> self).getOrElse(z), self) match {
        case (z, Then(left, right)) => right.fold(left.fold(z)(f))(f)
        case (z, Both(left, right)) => right.fold(left.fold(z)(f))(f)

        case (z, _) => z
      }

    final def failureOrCause: Either[E, Cause[Nothing]] = self.failures.headOption match {
      case Some(error) => Left(error)
      case None        => Right(self.asInstanceOf[Cause[Nothing]]) // no E inside this cause, can safely cast
    }
  }

  object Cause extends Serializable {

    final def fail[E](error: E): Cause[E]                            = Fail(error)
    final def die(defect: Throwable): Cause[Nothing]                 = Die(defect)
    final val interrupt: Cause[Nothing]                              = Interrupt
    final def finalizerErrors(cause: Cause[Nothing]): Cause[Nothing] = FinalizerErrors(cause)

    final case class Fail[E](value: E)     extends Cause[E]
    final case class Die(value: Throwable) extends Cause[Nothing]
    final case object Interrupt            extends Cause[Nothing]

    final case class FinalizerErrors(nested: Cause[Nothing]) extends Cause[Nothing] { self =>
      final def flatten: Cause[Nothing] = {
        def flattenFinalizer(c: Cause[Nothing]): Cause[Nothing] = c match {
          case FinalizerErrors(nested) => flattenFinalizer(nested)
          case Then(left, right)       => Then(flattenFinalizer(left), flattenFinalizer(right))
          case Both(left, right)       => Both(flattenFinalizer(left), flattenFinalizer(right))
          case x                       => x
        }

        flattenFinalizer(nested)
      }
    }

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
