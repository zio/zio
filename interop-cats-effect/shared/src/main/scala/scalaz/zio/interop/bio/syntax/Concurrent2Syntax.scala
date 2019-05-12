package scalaz.zio
package interop
package bio
package syntax

import cats.kernel.Semigroup
import scalaz.zio.interop.bio.syntax.Concurrent2Syntax.Concurrent2Ops

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

private[syntax] trait Concurrent2Syntax {

  @inline implicit def concurrent2Syntax[F[+ _, + _], E, A](fa: F[E, A]): Concurrent2Ops[F, E, A] =
    new Concurrent2Ops(fa)
}

private[syntax] object Concurrent2Syntax {

  final class Concurrent2Ops[F[+ _, + _], E, A](private val fa: F[E, A]) extends AnyVal {

    @inline def start(implicit C: Concurrent2[F]): F[Nothing, Fiber2[F, E, A]] =
      C.start(fa)

    @inline def uninterruptible(implicit C: Concurrent2[F]): F[E, A] =
      C.uninterruptible(fa)

    @inline def onInterrupt(cleanup: F[Nothing, Unit])(
      implicit
      C: Concurrent2[F],
      ev: ConcurrentData2[F]
    ): F[E, A] =
      C.onInterrupt(fa)(cleanup)

    @inline def yieldTo(implicit C: Concurrent2[F]): F[E, A] =
      C.yieldTo(fa)

    @inline def evalOn(ec: ExecutionContext)(implicit C: Concurrent2[F]): F[E, A] =
      C.evalOn(fa, ec)

    @inline def race[EE >: E, AA >: A](fa2: F[EE, AA])(
      implicit
      C: Concurrent2[F],
      ev1: ConcurrentData2[F],
      ev2: Semigroup[EE]
    ): F[EE, Option[AA]] =
      C.race(fa, fa2)

    @inline def raceEither[EE >: E, B](fa2: F[EE, B])(
      implicit
      C: Concurrent2[F],
      ev1: ConcurrentData2[F],
      ev2: Semigroup[EE]
    ): F[EE, Option[Either[A, B]]] =
      C.raceEither(fa, fa2)

    @inline def raceAll[EE >: E, AA >: A](xs: Iterable[F[EE, AA]])(
      implicit
      C: Concurrent2[F],
      ev1: ConcurrentData2[F],
      ev2: Semigroup[EE]
    ): F[EE, Option[AA]] =
      C.raceAll[E, EE, A, AA](fa)(xs)

    @inline def <&>[EE >: E: Semigroup, B](fa1: F[E, A], fa2: F[EE, B])(
      implicit
      C: Concurrent2[F],
      ev: ConcurrentData2[F]
    ): F[EE, (A, B)] =
      C.zipPar(fa1, fa2)

    @inline def <&[EE >: E: Semigroup, B](fa1: F[E, A], fa2: F[EE, B])(
      implicit
      C: Concurrent2[F],
      ev: ConcurrentData2[F]
    ): F[EE, A] =
      C.zipParLeft(fa1, fa2)

    @inline def &>[EE >: E: Semigroup, B](fa1: F[E, A], fa2: F[EE, B])(
      implicit
      C: Concurrent2[F],
      ev: ConcurrentData2[F]
    ): F[EE, B] =
      C.zipParRight(fa1, fa2)
  }
}
