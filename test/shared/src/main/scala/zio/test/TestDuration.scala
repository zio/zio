package zio.test

import zio._

import java.time.Instant

/**
 * The time a test took, as the interval from a start to an end instant, or
 * zero. `TestAspect.timed` records it in the `TestAnnotation.timing`
 * annotation.
 */
sealed trait TestDuration { self =>
  import TestDuration._

  /**
   * Combines two durations into the span from the earliest start to the latest
   * end: overlapping intervals are counted once, and the gap between disjoint
   * intervals is included. Combining with `zero` returns the other duration.
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
   * Returns `true` for `zero` and for an interval whose start and end are the
   * same instant.
   */
  final def isZero: Boolean =
    toDuration.isZero

  /**
   * Returns the duration as text, using the `render` method of `zio.Duration`.
   */
  final def render: String =
    toDuration.render

  /**
   * Returns the length of the interval as a `zio.Duration`, or `Duration.Zero`
   * for `zero`. An interval whose end is before its start gives a negative
   * duration.
   */
  final def toDuration: Duration =
    self match {
      case Zero               => Duration.Zero
      case Finite(start, end) => Duration.fromInterval(start, end)
    }

  /**
   * Returns the length of the duration in milliseconds.
   */
  final def toMillis: Long =
    toDuration.toMillis

  /**
   * Returns the length of the duration in nanoseconds.
   */
  final def toNanos: Long =
    toDuration.toNanos
}

/**
 * Constructors for [[TestDuration]].
 */
object TestDuration {

  private final case class Finite(start: Instant, end: Instant) extends TestDuration
  private case object Zero                                      extends TestDuration

  /**
   * Creates the duration of the interval from `start` to `end`.
   */
  def fromInterval(start: Instant, end: Instant): TestDuration =
    Finite(start, end)

  /**
   * The zero duration. Combining it with another duration with `<>` returns the
   * other duration.
   */
  val zero: TestDuration =
    Zero
}
