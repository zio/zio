package zio.test

import zio._

import java.time.Instant

sealed trait TestDuration { self =>
  import TestDuration._

  final def <>(that: TestDuration): TestDuration =
    (self, that) match {
      case (Zero, right) => right
      case (left, Zero)  => left
      case (Finite(leftStart, leftEnd), Finite(rightStart, rightEnd)) =>
        val start = if (leftStart.isBefore(rightStart)) leftStart else rightStart
        val end   = if (leftEnd.isAfter(rightEnd)) leftEnd else rightEnd
        Finite(start, end)
    }

  final def isZero: Boolean =
    toDuration.isZero

  final def render: String =
    toDuration.render

  final def toDuration: Duration =
    self match {
      case Zero               => Duration.Zero
      case Finite(start, end) => Duration.fromInterval(start, end)
    }

  final def toMillis: Long =
    toDuration.toMillis

  final def toNanos: Long =
    toDuration.toNanos
}

object TestDuration {

  private final case class Finite(start: Instant, end: Instant) extends TestDuration
  private case object Zero                                      extends TestDuration

  def fromInterval(start: Instant, end: Instant): TestDuration =
    Finite(start, end)

  val zero: TestDuration =
    Zero
}
