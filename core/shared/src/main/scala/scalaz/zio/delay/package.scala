package scalaz.zio

import scalaz.zio.duration.Duration

package object delay {

  implicit final def durationDelay(duration: Duration): DelaySyntax = new DelaySyntax(duration)
}
