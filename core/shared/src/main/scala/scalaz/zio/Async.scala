// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio
import scalaz.zio.ExitResult.Cause

/**
 * The `Async` class describes the return value of an asynchronous effect
 * that is imported into an `IO` value.
 *
 * Asynchronous effects can return `later`, which represents an uninterruptible
 * asynchronous action, `now` which represents a synchronously computed value,
 * `maybeLater`, which represents an interruptible asynchronous action or `maybeLaterIO`
 * which represents an interruptible asynchronous action where the canceler has the
 * form `Throwable => IO[Nothing, Unit]`
 */
sealed abstract class Async[+E, +A] extends Product with Serializable { self =>
  final def fold[E1, B](
    f: A => ExitResult[E1, B],
    g: Cause[E] => ExitResult[E1, B]
  ): Async[E1, B] =
    self match {
      case Async.Now(r)            => Async.Now(r.fold(f, g))
      case x @ Async.MaybeLater(_) => x
    }
}

object Async extends Serializable {
  // TODO: Optimize this common case to less overhead with opaque types
  final case class Now[E, A](value: ExitResult[E, A]) extends Async[E, A]
  final case class MaybeLater(canceler: Canceler)     extends Async[Nothing, Nothing]

  /**
   * Constructs an `Async` that represents an uninterruptible asynchronous
   * action. The action should invoke the callback passed to the handler when
   * the value is available or the action has failed.
   *
   * See `IO.async0` for more information.
   */
  final val later: Async[Nothing, Nothing] = MaybeLater(IO.unit)

  /**
   * Constructs an `Async` that represents a synchronous return. The
   * handler should never invoke the callback.
   *
   * See `IO.async0` for more information.
   */
  final def now[E, A](result: ExitResult[E, A]): Async[E, A] = Now(result)

  /**
   * Constructs an `Async` that represents an interruptible asynchronous
   * action. The action should invoke the callback passed to the handler when
   * the value is available or the action has failed.
   *
   * The specified canceler, which must be idempotent, should attempt to cancel
   * the asynchronous action to avoid wasting resources for an action whose
   * results are no longer needed because the fiber computing them has been
   * terminated.
   */
  final def maybeLater(canceler: Canceler): Async[Nothing, Nothing] =
    MaybeLater(canceler)
}
