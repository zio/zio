package scalaz.zio
package interop

trait Fiber2[F[+ _, + _], E, A] {

  def cancel: F[Nothing, Option[Either[E, A]]]

  def await: F[Nothing, Option[Either[E, A]]]

  def join: F[E, A]
}
