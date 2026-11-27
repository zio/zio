package zio.test

import zio._

import java.time.Instant

/**
 * The time span in which one or more tests ran, as the `TestAnnotation.timing`
 * annotation stores it: either zero or the interval between a start and an end
 * instant.
 */
sealed trait TestDuration { self =>
  import TestDuration._

  /**
   * Combines two durations into the smallest interval that covers both, from
   * the earlier start to the later end. Combining with `TestDuration.zero`
   * returns the other duration.
   */
  final def <>(that: TestDuration): TestDuration =
    (self, that) match {
      case (Zero, right) => right
      case (left, Zero)  => left
      case (Finite(leftStart, leftEnd), Finite(rightStart, rightEnd)) =>
        val start = if (leftStart.isBefore(rightStart)) leftStart else rightStart
        val end   = if (leftEnd.isAfter(rightEnd)) leftEnd else rightEnd
        Finite(start, end)
    }

  /**
   * Returns `true` when the length of this duration is zero.
   */
  final def isZero: Boolean =
    toDuration.isZero

  /**
   * Returns the length of this duration as human-readable text.
   */
  final def render: String =
    toDuration.render

  /**
   * Returns the length of this duration: the time from its start to its end, or
   * `Duration.Zero` for `TestDuration.zero`.
   */
  final def toDuration: Duration =
    self match {
      case Zero               => Duration.Zero
      case Finite(start, end) => Duration.fromInterval(start, end)
    }

  /**
   * Returns the length of this duration in milliseconds.
   */
  final def toMillis: Long =
    toDuration.toMillis

  /**
   * Returns the length of this duration in nanoseconds.
   */
  final def toNanos: Long =
    toDuration.toNanos
}

/**
 * Constructors of [[TestDuration]].
 */
object TestDuration {

  private final case class Finite(start: Instant, end: Instant) extends TestDuration
  private case object Zero                                      extends TestDuration

  /**
   * Returns the duration from `start` to `end`. The order is not checked: an
   * `end` before `start` gives a negative length.
   */
  def fromInterval(start: Instant, end: Instant): TestDuration =
    Finite(start, end)

  /**
   * The duration of length zero, which `<>` treats as no interval.
   */
  val zero: TestDuration =
    Zero
}
