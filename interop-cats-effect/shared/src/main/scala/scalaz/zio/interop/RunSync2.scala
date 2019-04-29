package scalaz.zio
package interop

trait RunSync2[F[+ _, + _]] extends Sync2[F] {

  def runSync[G[+ _, + _], E, A](fa: F[E, A])(implicit G: Sync2[G]): G[E, A]
}
