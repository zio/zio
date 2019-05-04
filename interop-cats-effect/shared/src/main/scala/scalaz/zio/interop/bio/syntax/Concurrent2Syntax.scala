package scalaz.zio
package interop
package bio
package syntax

import cats.kernel.Monoid
import scalaz.zio.interop.bio.syntax.Concurrent2Syntax.Concurrent2Ops

import scala.language.implicitConversions

private[syntax] trait Concurrent2Syntax {

  @inline implicit def concurrent2Syntax[F[+ _, + _], E, A](fa: F[E, A]): Concurrent2Ops[F, E, A] =
    new Concurrent2Ops(fa)
}

private[syntax] object Concurrent2Syntax {

  final class Concurrent2Ops[F[+ _, + _], E, A](private val fa: F[E, A]) extends AnyVal {

    @inline def <&>[EE >: E, B](fa1: F[E, A], fa2: F[EE, B])(
      implicit
      C: Concurrent2[F],
      CD: ConcurrentData2[F],
      MD: Monoid[EE]
    ): F[EE, (A, B)] =
      C.zipPar(fa1, fa2)

    @inline def <&[EE >: E, B](fa1: F[E, A], fa2: F[EE, B])(
      implicit
      C: Concurrent2[F],
      CD: ConcurrentData2[F],
      MD: Monoid[EE]
    ): F[EE, A] =
      C.zipParLeft(fa1, fa2)

    @inline def &>[EE >: E, B](fa1: F[E, A], fa2: F[EE, B])(
      implicit
      C: Concurrent2[F],
      CD: ConcurrentData2[F],
      MD: Monoid[EE]
    ): F[EE, B] =
      C.zipParRight(fa1, fa2)
  }
}
