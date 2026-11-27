/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.test

import zio.{Duration, Trace}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.time.Instant

/**
 * The result of running a spec: the number of tests that succeeded, failed and
 * were ignored, a text report of the failures in `failureDetails`, and how long
 * the run took.
 */
sealed case class Summary(
  success: Int,
  fail: Int,
  ignore: Int,
  failureDetails: String,
  duration: Duration = Duration.Zero
) { self =>
  import Summary.Interval

  /**
   * Returns this summary updated with one execution event. A `Test` event adds
   * one to `success`, `fail` or `ignore`; a failed `Test` event and a
   * `RuntimeFailure` event also add their failure to `failureDetails`; other
   * events leave the counts and failure details unchanged.
   */
  final def add(executionEvent: ExecutionEvent)(implicit trace: Trace): Summary =
    SummaryBuilder.buildSummary(executionEvent, self)

  /**
   * Combines two summaries: adds the counts, appends the non-blank failure
   * details of `that`, and spans both intervals.
   */
  final def add(that: Summary): Summary =
    Summary(
      self.success + that.success,
      self.fail + that.fail,
      self.ignore + that.ignore,
      self.failureDetails +
        (if (that.failureDetails.trim.isEmpty)
           ""
         else
           "\n" + that.failureDetails),
      self.interval <> that.interval
    )

  /**
   * The interval of the run, or `Interval.empty` when the summary was built
   * without one (see `timed`).
   */
  def interval: Interval =
    Interval.empty

  /**
   * Returns `Summary.Failure` when `failureDetails` has non-blank text and
   * `Summary.Success` otherwise. The counts are not consulted.
   */
  final def status: Summary.Status =
    if (failureDetails.trim.isEmpty)
      Summary.Success
    else
      Summary.Failure

  /**
   * Returns this summary with its interval set to the span from `start` to
   * `end`. `TestRunner` calls it with the start and end of the run.
   */
  final def timed(start: Instant, end: Instant): Summary =
    Summary(
      self.success,
      self.fail,
      self.ignore,
      self.failureDetails,
      Interval.finite(start, end)
    )

  /**
   * Returns the number of tests counted: `success + fail + ignore`.
   */
  def total: Int =
    success + fail + ignore
}

/**
 * Constructors for [[Summary]] values, with the types of its interval and
 * status.
 */
object Summary {

  /**
   * Creates a summary whose `interval` is `interval0` and whose `duration` is
   * the length of that interval.
   */
  def apply(
    success: Int,
    fail: Int,
    ignore: Int,
    failureDetails: String,
    interval0: Interval
  ): Summary =
    new Summary(success, fail, ignore, failureDetails, interval0.duration) {
      override def interval: Interval = interval0
    }

  /**
   * A summary with no tests, no failure details and a zero duration.
   */
  val empty: Summary =
    Summary(0, 0, 0, "")

  /**
   * The time span of a run, from a start to an end instant, or empty.
   */
  sealed trait Interval { self =>

    /**
     * Combines two intervals into the span from the earliest start to the
     * latest end. An empty interval leaves the other one unchanged.
     */
    final def <>(that: Interval): Interval =
      (self, that) match {
        case (self, Interval.Empty) => self
        case (Interval.Empty, that) => that
        case (Interval.Finite(start1, end1), Interval.Finite(start2, end2)) =>
          val start = if (start1.isBefore(start2)) start1 else start2
          val end   = if (end1.isAfter(end2)) end1 else end2
          Interval.Finite(start, end)
      }

    /**
     * Returns the length of the interval, or `Duration.Zero` when it is empty.
     * An end before the start gives a negative duration.
     */
    final def duration: Duration =
      self match {
        case Interval.Empty              => Duration.Zero
        case Interval.Finite(start, end) => Duration.fromInterval(start, end)
      }
  }

  /**
   * Constructors for `Summary.Interval`.
   */
  object Interval {

    /**
     * The empty interval, with a zero duration.
     */
    val empty: Interval =
      Empty

    /**
     * Creates the interval from `start` to `end`.
     */
    def finite(start: Instant, end: Instant): Interval =
      Finite(start, end)

    private case object Empty                                     extends Interval
    private final case class Finite(start: Instant, end: Instant) extends Interval
  }

  /**
   * Whether a run had failures, as returned by `Summary#status`.
   */
  sealed trait Status

  /**
   * The status of a run whose failure details are blank.
   */
  object Success extends Status

  /**
   * The status of a run whose failure details have text.
   */
  object Failure extends Status
}
