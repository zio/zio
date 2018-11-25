package scalaz.zio

import scala.language.implicitConversions

package object duration {

  implicit final def durationInt(n: Int): DurationSyntax = new DurationSyntax(n.toLong)

  implicit final def durationLong(n: Long): DurationSyntax = new DurationSyntax(n)

}
