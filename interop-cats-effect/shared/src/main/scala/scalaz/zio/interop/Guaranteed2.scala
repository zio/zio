package scalaz.zio
package interop

import cats.{Applicative, Bifunctor}

trait Guaranteed2[F[+ _, + _]] extends Bifunctor[F] {
  def applicative[E]: Applicative[F[E, ?]]

  def guarantee[E, A](fa: F[E, A], f: F[Nothing, Unit]): F[E, A]
}
