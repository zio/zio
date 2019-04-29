package scalaz.zio
package interop

trait Sync2[F[+ _, + _]] extends Errorful2[F] {

  def delay[A](a: =>A): F[Nothing, A]
}
