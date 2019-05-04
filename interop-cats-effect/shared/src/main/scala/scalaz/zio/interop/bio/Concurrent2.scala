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

package scalaz.zio
package interop
package bio

import cats.syntax.option._
import cats.syntax.flatMap.catsSyntaxFlatten
import scalaz.zio.interop.bio.data.{ Deferred2, Ref2 }

import scala.concurrent.ExecutionContext

abstract class Concurrent2[F[+ _, + _]] extends Temporal2[F] { self =>

  /**
   * Returns an effect that runs `fa` into its own separate `Fiber2`
   * and returns the fiver as result.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def start[E, A](fa: F[E, A]): F[Nothing, Fiber2[F, E, A]]

  /**
   * Performs `fa` uninterruptedly. This will prevent it from
   * being terminated externally, but it may still fail for internal
   * reasons.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def uninterruptible[E, A](fa: F[E, A]): F[E, A]

  /**
   * Executes the `cleanup` effect if `fa` is interrupted
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def onInterrupt[E, A](fa: F[E, A])(cleanup: F[Nothing, _]): F[E, A]

  /**
   *
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def yieldTo[E, A](fa: F[E, A]): F[E, A]

  /**
   * Executes `fa` on the ExecutionContext `ec` and shifts back to
   * the default one when completed.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def evalOn[E, A](fa: F[E, A], ec: ExecutionContext): F[E, A]

  /**
   * Returns an effect that races `fa1` with `fa2` returning the first
   * successful `A` from the faster of the two. If one effect succeeds
   * the other will be interrupted. If neither succeeds, then the
   * effect will fail with some error.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def race[E1, A, E, EE >: E, AA >: A](fa1: F[E, A], fa2: F[EE, AA])(
    implicit CD: ConcurrentData2[F]
  ): F[EE, Option[AA]] =
    monad.map(raceEither(fa1, fa2))(_ map (_.merge))

  /**
   * Returns an effect that races `fa1` with `fa2`, calling specified
   * finisher as soon as one result or the other has been computed.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def raceEither[E, EE >: E, A, B](fa1: F[E, A], fa2: F[EE, B])(
    implicit ev: ConcurrentData2[F]
  ): F[EE, Option[Either[A, B]]] = {

    import cats.syntax.either._

    implicit val _: Errorful2[F] = self

    raceWith(fa1, fa2)(
      (fa1Result, fiber2) =>
        fa1Result.fold[F[EE, Option[Either[A, B]]]](monad.pure(None)) {
          _.fold(
            _ => fiber2.join map (f2r => Some(f2r.asRight)),
            a => monad.pure(Some(a.asLeft)) <* fiber2.cancel
          )
        },
      (fa2Result, fiber1) =>
        fa2Result.fold[F[EE, Option[Either[A, B]]]](monad.pure(None)) {
          _.fold(
            _ => fiber1.join map (f1r => Some(f1r.asLeft)),
            b => monad.pure(Some(b.asRight)) <* fiber1.cancel
          )
        }
    )
  }

  /**
   * Returns an effect that races `fa1` with `fa2`, calling specified
   * finisher as soon as one result or the other has been computed.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def raceWith[E1, E2, E3, A, B, C](fa1: F[E1, A], fa2: F[E2, B])(
    leftDone: (Option[Either[E1, A]], Fiber2[F, E2, B]) => F[E3, C],
    rightDone: (Option[Either[E2, B]], Fiber2[F, E1, A]) => F[E3, C]
  )(
    implicit CD: ConcurrentData2[F]
  ): F[E3, C] = {

    implicit val ev: Errorful2[F] = self

    def arbiter[EE0, EE1, AA, BB](
      f: (Option[Either[EE0, AA]], Fiber2[F, EE1, BB]) => F[E3, C],
      loser: Fiber2[F, EE1, BB],
      race: Ref2[F, Int],
      whenDone: Deferred2[F, E3, C]
    )(res: Option[Either[EE0, AA]]): F[Nothing, Unit] =
      race.modify { c =>
        (if (c > 0) monad.unit else whenDone.done(f(res, loser)) map (_ => ())) -> (c + 1)
      }.flatten

    for {
      done <- CD.deferred[E3, C]
      race <- CD.refSet(0)
      c <- uninterruptible[E3, C](
            for {
              left  <- start(fa1)
              right <- start(fa2)
              _     <- start[Nothing, Unit](left.await >>= arbiter(leftDone, right, race, done))
              _     <- start[Nothing, Unit](right.await >>= arbiter(rightDone, left, race, done))
              rc    <- onInterrupt(done.await)(left.cancel *> right.cancel)
            } yield rc
          )
    } yield c
  }

  /**
   * Returns an effect that races `fa` with all the effects in `xs`.
   * The semantic is the same as `race` applied to a collection of
   * parallel effects.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  def raceAll[E, A, EE >: E, AA >: A](fa: F[E, A])(xs: Iterable[F[EE, AA]])(
    implicit CD: ConcurrentData2[F]
  ): F[EE, Option[AA]] = {

    implicit val _: Errorful2[F] = self

    val init = fa.widenBoth[EE, AA] map (_.some)

    xs.foldLeft(init) { (acc, curr) =>
      race(acc, curr.map(_.some)) map (_.flatten)
    }
  }

  // may be
  def cont[E, A](r: (F[E, A] => F[Nothing, Unit]) => F[Nothing, Unit]): F[E, A]
}

object Concurrent2 {

  @inline def apply[F[+ _, + _]: Concurrent2]: Concurrent2[F] = implicitly
}
