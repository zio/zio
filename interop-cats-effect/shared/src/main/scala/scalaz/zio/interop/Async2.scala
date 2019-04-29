package scalaz.zio
package interop

trait Async2[F[+ _, + _]] extends Sync2[F] {

  def async[E, A](k: (F[E, A] => Unit) => Unit): F[E, A]

  def asyncF[E, A](k: (F[E, A] => Unit) => F[Nothing, Unit]): F[E, A]
}
