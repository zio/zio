package scalaz.zio

import scalaz.zio.duration.Duration

import scala.language.implicitConversions

package object delay {

  implicit final def durationDelay(duration: Duration): DelaySyntax = new DelaySyntax(duration)
}
