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

import cats.Monad
import cats.kernel.Semigroup
import zio.interop.bio.Failed.{ Defects, Errors, Interrupt }
import zio.interop.bio.data.{ Deferred2, Ref2 }

import scala.concurrent.ExecutionContext

abstract class Concurrent2[F[+_, +_]] extends Temporal2[F] { self =>

  def concurrentData: ConcurrentData2[F]

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
  @inline def race[E, EE >: E: Semigroup, A, AA >: A](fa1: F[E, A], fa2: F[EE, AA]): F[EE, AA] = {

    implicit val _: Monad[F[EE, ?]] = self.monad

    raceEither(fa1, fa2) map (_.merge)
  }

  /**
   * Returns an effect that races `fa1` with `fa2` returning the result of
   * one or the other depending on the winner. If both of them fail it will
   * return the combined error. If both are interrupted it will return an
   * interrupted effect. if both die, it will return a dead effect.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def raceEither[E, EE >: E, A, B](fa1: F[E, A], fa2: F[EE, B])(
    implicit SG: Semigroup[EE]
  ): F[EE, Either[A, B]] = {

    import cats.syntax.either._

    implicit val _: Concurrent2[F] = self

    def arbiter[E1 <: EE, E2 <: EE, A1, B1](
      winner: Either[Failed[E1], A1],
      loser: Fiber2[F, E2, B1]
    ): F[EE, Either[A1, B1]] =
      winner match {
        case Left(Errors(wes)) =>
          loser.await >>= {
            case Right(l)          => monad.pure(l.asRight)
            case Left(Interrupt)   => raiseError(wes.reduce(SG.combine))
            case Left(Errors(les)) => raiseError(SG.combine(wes.reduce(SG.combine), les.reduce(SG.combine)))
            case Left(Defects(ts)) => dieWithMany(ts)
          }

        case Left(Interrupt) | Left(Defects(_)) =>
          loser.join >>= (l => monad.pure(l.asRight))

        case Right(w) =>
          monad.pure(w.asLeft) <* loser.cancel
      }

    raceWith(fa1, fa2)(
      arbiter(_, _),
      arbiter(_, _) map (_.swap)
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
    leftDone: (Either[Failed[E1], A], Fiber2[F, E2, B]) => F[E3, C],
    rightDone: (Either[Failed[E2], B], Fiber2[F, E1, A]) => F[E3, C]
  ): F[E3, C] = {

    implicit val ev: Concurrent2[F] = self

    def arbiter[EE0, EE1, AA, BB](
      f: (Either[Failed[EE0], AA], Fiber2[F, EE1, BB]) => F[E3, C],
      loser: Fiber2[F, EE1, BB],
      race: Ref2[F, Int],
      oneStatus: Deferred2[F, E3, C]
    )(res: Either[Failed[EE0], AA]): F[Nothing, Unit] =
      race.modify { c =>
        (if (c > 0) monad.unit else oneStatus.done(f(res, loser)) *> monad.unit) -> (c + 1)
      }.flatten

    for {
      done <- concurrentData.deferred[E3, C]
      race <- concurrentData.ref(0)
      c <- bracket(
            for {
              left  <- start(fa1)
              right <- start(fa2)
              _     <- start[Nothing, Unit](left.await >>= arbiter(leftDone, right, race, done))
              _     <- start[Nothing, Unit](right.await >>= arbiter(rightDone, left, race, done))
            } yield (left, right),
            (_: (Fiber2[F, E1, A], Fiber2[F, E2, B]), _: Either[Failed[_], C]) => monad.unit
          ) {
            case (left, right) => onInterrupt(done.await)(left.cancel *> right.cancel *> monad.unit)
          }
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
  @inline def raceAll[E, EE >: E: Semigroup, A, AA >: A](fa: F[E, A])(xs: Iterable[F[EE, AA]]): F[EE, AA] =
    xs.foldLeft(fa.widenBoth[EE, AA]) { (acc, curr) =>
      race(acc, curr)
    }

  /**
   * Returns an effect that executes both `fa1` and `fa2` in parallel
   * and combines their results with `f`. If one of the two fails
   * then the other will be interrupted.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def zipWithPar[E, EE >: E, A, B, C](fa1: F[E, A], fa2: F[EE, B])(f: (A, B) => C)(
    implicit SG: Semigroup[EE]
  ): F[EE, C] = {

    implicit val _: Concurrent2[F] = self

    def coordinate[E1 <: EE, E2 <: EE, AA, BB](f: (AA, BB) => C)(
      winner: Either[Failed[E1], AA],
      loser: Fiber2[F, E2, BB]
    ): F[EE, C] =
      winner match {
        case Left(Errors(wes)) =>
          loser.cancel >>= {
            case Right(_) | Left(Interrupt) => raiseError(wes.reduce(SG.combine))
            case Left(Errors(les))          => raiseError(SG.combine(wes.reduce(SG.combine), les.reduce(SG.combine)))
            case Left(Defects(ts))          => dieWithMany(ts)
          }

        case Left(Interrupt) =>
          loser.cancel >>= {
            case Right(_) | Left(Interrupt) => interrupt
            case Left(Errors(les))          => raiseError(les.reduce(SG.combine))
            case Left(Defects(ts))          => dieWithMany(ts)
          }

        case Left(Defects(ts)) => dieWithMany(ts)

        case Right(wa) =>
          loser.join map (f(wa, _))
      }

    val g = (b: B, a: A) => f(a, b)

    raceWith(fa1, fa2)(coordinate(f), coordinate(g))
  }

  /**
   * Returns an effect that executes both `fa1` and `fa2` in parallel
   * and combines their results in a tuple. If one of the two fails
   * then the other will be interrupted.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def zipPar[E, EE >: E: Semigroup, A, B](fa1: F[E, A], fa2: F[EE, B]): F[EE, (A, B)] =
    zipWithPar(fa1, fa2)((a, b) => (a, b))

  /**
   * Returns an effect that executes both `fa1` and `fa2` in parallel
   * and returns the result of `fa1`. If one of the two fails
   * then the other will be interrupted.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline def zipParLeft[E, EE >: E: Semigroup, A, B](fa1: F[E, A], fa2: F[EE, B]): F[EE, A] =
    zipWithPar(fa1, fa2)((a, _) => a)

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
  @inline def zipParRight[E, EE >: E: Semigroup, A, B](fa1: F[E, A], fa2: F[EE, B]): F[EE, B] =
    zipWithPar(fa1, fa2)((_, b) => b)

  /**
   * Returns an effect that will acquire a resource and will release
   * it after the execution of `use` regardless the fact that `use`
   * succeed or fail.
   *
   * TODO: Example:
   * {{{
   *
   * }}}
   *
   */
  @inline override def bracket[E, A, B](
    acquire: F[E, A],
    release: (A, Either[Failed[E], B]) => F[Nothing, Unit]
  )(use: A => F[E, B]): F[E, B] = {

    implicit val _: Concurrent2[F] = self

    concurrentData.ref(monad.unit) >>= { m =>
      guarantee(
        uninterruptible(
          acquire >>= { a =>
            start(use(a)) tap { useFiber =>
              m set (useFiber.cancel >>= (release(a, _)))
            }
          }
        ) >>= (_.join),
        m.get.flatten
      )
    }
  }
}

object Concurrent2 {

  @inline def apply[F[+_, +_]: Concurrent2]: Concurrent2[F] = implicitly
}
