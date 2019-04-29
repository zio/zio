package scalaz.zio
package interop

import scala.concurrent.ExecutionContext

trait Concurrent2[F[+ _, + _]] extends Temporal2[F] {

  def start[E, A](fa: F[E, A]): F[Nothing, Fiber2[F, E, A]]

  def uninterruptible[E, A](fa: F[E, A]): F[E, A]

  def yieldTo[E, A](fa: F[E, A]): F[E, A]

  def evalOn[E, A](fa: F[E, A], ec: ExecutionContext): F[E, A]
}
