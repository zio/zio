package scalaz.zio
package interop

import java.time.Instant

import scala.concurrent.duration.Duration

trait Temporal2[F[+ _, + _]] extends Errorful2[F] {

  def sleep(duration: Duration): F[Nothing, Unit]

  def now: F[Nothing, Instant]
}
