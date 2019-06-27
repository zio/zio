/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio
package interop
package bio

import cats.data.NonEmptyList
import cats.{ Bifunctor, Monad }
import zio.interop.bio.Failed.{ Defects, Errors, Interrupt }

trait Errorful2[F[+_, +_]] extends Bifunctor[F] { self =>

  def monad[E]: Monad[F[E, ?]]

  /**
   * Allows to recover from the error, accepting effects that handle both
   * the failure and the success case. The handling effects can still fail
   * themselves with an error of type `E2`.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   * @see [[redeem]] to recover from the error in a non failing way.
   *
   */
  def redeemWith[E1, E2, A, B](fa: F[E1, A])(failure: E1 => F[E2, B], success: A => F[E2, B]): F[E2, B]

  /**
   * Returns an effect that completes with the provided exit case.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def unsuccessful[E, A](failure: Failed[E]): F[E, Nothing]

  /**
   * Returns an effect `F` that will fail with an error of type `E`.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def raiseError[E](e: E): F[E, Nothing] =
    unsuccessful(Errors(NonEmptyList.one(e)))

  /**
   * Returns an interrupted effect `F`.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def interrupt: F[Nothing, Nothing] =
    unsuccessful(Interrupt)

  /**
   * Returns an dead effect `F`.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def dieWith(t: Throwable): F[Nothing, Nothing] =
    unsuccessful(Defects(NonEmptyList.one(t)))

  @inline def dieWithMany(ts: NonEmptyList[Throwable]): F[Nothing, Nothing] =
    unsuccessful(Defects(ts))

  /**
   * Allows to recover from the error in a non failing way.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def redeem[E1, A, B](fa: F[E1, A])(failure: E1 => B, success: A => B): F[Nothing, B] =
    redeemWith(fa)(
      failure andThen monad.pure,
      success andThen monad.pure
    )

  /**
   * Allows to specify an alternative in case the original effect fail.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def orElse[E1, E2, A, AA >: A](fa: F[E1, A])(that: => F[E2, AA]): F[E2, AA] =
    redeemWith(fa)(_ => that, monad.pure)

  /**
   * Allows to surface a possible failure of the effect and make it explicit
   * in the result type as an `Either`
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   * * @see [[rethrow]] to submerge the error in `F`
   *
   */
  @inline def attempt[E, A](fa: F[E, A]): F[Nothing, Either[E, A]] =
    redeem(fa)(Left(_), Right(_))

  /**
   * Inverse of [[attempt]] submerges the `Either`'s error in `F`
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def rethrow[E, A](fa: F[Nothing, Either[E, A]]): F[E, A] =
    monad.flatMap(fa)(
      _.fold(raiseError, monad.pure)
    )

  /**
   * Recovers from the error if the effect fails, and forwards the result
   * in case of success
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def handleErrorWith[E1, E2, A, AA >: A](fa: F[E1, A])(f: E1 => F[E2, AA]): F[E2, AA] =
    redeemWith(fa)(f, monad.pure)

  /**
   * Recovers from some or all the errors depending on `pf`'s domain
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def recoverWith[E, EE >: E, A, AA >: A](fa: F[E, A])(pf: PartialFunction[E, F[EE, AA]]): F[EE, AA] =
    redeemWith(fa)(
      pf.applyOrElse(_, raiseError[EE]),
      monad.pure
    )

  /**
   * Verifies a predicate and returns a failing effect if false
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def ensure[E, A](fa: F[E, A])(predicate: A => Boolean)(ifFalse: A => E): F[E, A] =
    monad.flatMap(fa) { a =>
      if (predicate(a)) monad.pure(a) else raiseError(ifFalse(a))
    }

  /**
   * Flips the types of `F`
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def flip[E, A](fa: F[E, A]): F[A, E] =
    redeemWith(fa)(monad.pure, raiseError)

  /**
   * Gives an effect that runs `fa` but ignores
   * the error or the result from it.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def ignore[E, A](fa: F[E, A]): F[Nothing, Unit] = {

    implicit val ev: Errorful2[F] = self

    monad.unit tap (_ => attempt(fa))
  }

  /**
   * Lifts an `Option` into `F`
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def fromOption[A](oa: Option[A]): F[Unit, A] =
    fromOption(())(oa)

  /**
   * Lifts an `Option` into `F` provided the error if `None`
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def fromOption[E, A](ifNone: => E)(oa: Option[A]): F[E, A] =
    oa match {
      case Some(a) => monad.pure(a)
      case None    => raiseError(ifNone)
    }

  /**
   * Lifts an `Either` into `F`
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def fromEither[E, A](ea: Either[E, A]): F[E, A] =
    ea match {
      case Left(e)  => raiseError(e)
      case Right(a) => monad.pure(a)
    }

  /**
   * Lifts a `Try` into `F`
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def fromTry[A](ta: scala.util.Try[A]): F[Throwable, A] =
    ta match {
      case scala.util.Failure(th) => raiseError(th)
      case scala.util.Success(a)  => monad.pure(a)
    }

  override def bimap[A, B, C, D](fab: F[A, B])(f: A => C, g: B => D): F[C, D] =
    redeemWith(fab)(
      f andThen raiseError,
      g andThen monad.pure
    )
}

object Errorful2 {

  @inline def apply[F[+_, +_]: Errorful2]: Errorful2[F] = implicitly
}
