package scalaz.zio
package interop

import cats.Monad

trait Errorful2[F[+ _, + _]] extends Guaranteed2[F] {
  def monad[E]: Monad[F[E, ?]]

  def raiseError[E](e: E): F[E, Nothing]

  def redeem[E1, E2, A, B](fa: F[E1, A])(err: E1 => F[E2, B], succ: A => F[E2, B]): F[E2, B]
}
