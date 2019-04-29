package scalaz.zio
package interop

trait RunAsync2[F[+ _, + _]] extends Async2[F] {

  def runAsync[G[+ _, + _], E, A](fa: F[E, A], k: Either[E, A] => G[Nothing, Unit])(
    implicit G: Sync2[G]
  ): G[Nothing, Unit]
}
