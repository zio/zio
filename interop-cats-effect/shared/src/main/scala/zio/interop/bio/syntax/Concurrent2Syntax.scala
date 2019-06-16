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
package syntax

import cats.kernel.Semigroup
import zio.interop.bio.syntax.Concurrent2Syntax.Concurrent2Ops

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

private[syntax] trait Concurrent2Syntax {

  @inline implicit def concurrent2Syntax[F[+_, +_], E, A](fa: F[E, A]): Concurrent2Ops[F, E, A] =
    new Concurrent2Ops(fa)
}

private[syntax] object Concurrent2Syntax {

  final class Concurrent2Ops[F[+_, +_], E, A](private val fa: F[E, A]) extends AnyVal {

    @inline def start(implicit C: Concurrent2[F]): F[Nothing, Fiber2[F, E, A]] =
      C.start(fa)

    @inline def yieldTo(implicit C: Concurrent2[F]): F[E, A] =
      C.yieldTo(fa)

    @inline def evalOn(ec: ExecutionContext)(implicit C: Concurrent2[F]): F[E, A] =
      C.evalOn(fa, ec)

    @inline def race[EE >: E: Semigroup, AA >: A](fa2: F[EE, AA])(implicit C: Concurrent2[F]): F[EE, AA] =
      C.race(fa, fa2)

    @inline def raceEither[EE >: E: Semigroup, B](fa2: F[EE, B])(implicit C: Concurrent2[F]): F[EE, Either[A, B]] =
      C.raceEither(fa, fa2)

    @inline def raceWith[E2, E3, B, C](fa2: F[E2, B])(
      leftDone: (Either[Failed[E], A], Fiber2[F, E2, B]) => F[E3, C],
      rightDone: (Either[Failed[E2], B], Fiber2[F, E, A]) => F[E3, C]
    )(implicit C: Concurrent2[F]): F[E3, C] =
      C.raceWith(fa, fa2)(leftDone, rightDone)

    @inline def raceAll[EE >: E: Semigroup, AA >: A](xs: Iterable[F[EE, AA]])(implicit C: Concurrent2[F]): F[EE, AA] =
      C.raceAll[E, EE, A, AA](fa)(xs)

    @inline def zipWithPar[EE >: E: Semigroup, B, C](
      fa2: F[EE, B]
    )(f: (A, B) => C)(implicit C: Concurrent2[F]): F[EE, C] =
      C.zipWithPar(fa, fa2)(f)

    @inline def zipPar[EE >: E: Semigroup, B](fa2: F[EE, B])(implicit C: Concurrent2[F]): F[EE, (A, B)] =
      C.zipPar(fa, fa2)

    @inline def zipParLeft[EE >: E: Semigroup, B](fa2: F[EE, B])(implicit C: Concurrent2[F]): F[EE, A] =
      C.zipParLeft(fa, fa2)

    @inline def zipParRight[EE >: E: Semigroup, B](fa2: F[EE, B])(implicit C: Concurrent2[F]): F[EE, B] =
      C.zipParRight(fa, fa2)

    @inline def bracket[B](
      release: (A, Either[Failed[E], B]) => F[Nothing, Unit]
    )(use: A => F[E, B])(implicit C: Concurrent2[F]): F[E, B] =
      C.bracket(fa, release)(use)

    @inline def <&>[EE >: E: Semigroup, B](fa2: F[EE, B])(implicit C: Concurrent2[F]): F[EE, (A, B)] =
      C.zipPar(fa, fa2)

    @inline def <&[EE >: E: Semigroup, B](fa2: F[EE, B])(implicit C: Concurrent2[F]): F[EE, A] =
      C.zipParLeft(fa, fa2)

    @inline def &>[EE >: E: Semigroup, B](fa2: F[EE, B])(implicit C: Concurrent2[F]): F[EE, B] =
      C.zipParRight(fa, fa2)
  }
}
