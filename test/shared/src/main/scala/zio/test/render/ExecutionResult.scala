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

package zio.test.render

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.{SuiteId, TestAnnotationMap}
import zio.test.render.ExecutionResult.Status._
import zio.test.render.ExecutionResult.{ResultType, Status}
import zio.test.render.LogLine.Line

/**
 * The output of a [[TestRenderer]] for one suite or test event, before it is turned into text.
 *
 * `streamingLines` are shown while the tests run, and `summaryLines` are used for the summary at the end of the run.
 *
 * @param resultType
 *   whether the result describes a test, a suite or other output
 * @param label
 *   the label of the test or suite; for a test, [[IntelliJRenderer]] stores the test's own label and [[ConsoleRenderer]] the outermost label of its path
 * @param status
 *   the state of the test or suite
 * @param offset
 *   the indentation level, taken from the nesting depth of the test or suite
 * @param annotations
 *   the annotations of the test, used for the summary and for the source location
 * @param duration
 *   the run time of the test in milliseconds, when the renderer records it
 */
case class ExecutionResult(
  resultType: ResultType,
  label: String,
  status: Status,
  offset: Int,
  annotations: List[TestAnnotationMap],
  streamingLines: List[Line],
  summaryLines: List[Line],
  duration: Option[Long]
) {
  self =>

  /**
   * Combines two results, keeping a failure over a pass. The cases are checked in this order:
   *
   *   - this result is `Started` or `Ignored`: returns `that`
   *   - `that` is `Ignored`: returns this result
   *   - both are `Failed`: returns this result with the streaming lines of `that`, except its first line, appended; throws a `java.lang.UnsupportedOperationException` when `that` has no streaming lines
   *   - this result is `Passed`: returns `that`
   *   - `that` is `Passed`: returns this result
   *
   * This result `Failed` with `that` `Started` matches no case and throws a `scala.MatchError`.
   */
  def &&(that: ExecutionResult): ExecutionResult =
    (self.status, that.status) match {
      case (Started, _)     => that
      case (Ignored, _)     => that
      case (_, Ignored)     => self
      case (Failed, Failed) => self.copy(streamingLines = self.streamingLines ++ that.streamingLines.tail)
      case (Passed, _)      => that
      case (_, Passed)      => self
    }

  /**
   * Combines two results, keeping a pass over a failure. The cases are checked in this order:
   *
   *   - this result is `Started` or `Ignored`: returns `that`
   *   - `that` is `Ignored`: returns this result
   *   - both are `Failed`: returns this result with the streaming lines of `that`, except its first line, appended; throws a `java.lang.UnsupportedOperationException` when `that` has no streaming lines
   *   - this result is `Passed`: returns this result
   *   - `that` is `Passed`: returns `that`
   *
   * This result `Failed` with `that` `Started` matches no case and throws a `scala.MatchError`.
   */
  def ||(that: ExecutionResult): ExecutionResult =
    (self.status, that.status) match {
      case (Started, _)     => that
      case (Ignored, _)     => that
      case (_, Ignored)     => self
      case (Failed, Failed) => self.copy(streamingLines = self.streamingLines ++ that.streamingLines.tail)
      case (Passed, _)      => self
      case (_, Passed)      => that
    }

  /**
   * Swaps `Passed` and `Failed`, and returns `Started` and `Ignored` results unchanged. Only the status changes; the lines stay the same.
   */
  def unary_! : ExecutionResult =
    self.status match {
      case Started => self
      case Ignored => self
      case Failed  => self.copy(status = Passed)
      case Passed  => self.copy(status = Failed)
    }

  /** Returns a copy of this result with its annotations replaced by `annotations`. */
  def withAnnotations(annotations: List[TestAnnotationMap]): ExecutionResult =
    self.copy(annotations = annotations)
}

/** A constructor for [[ExecutionResult]], and its [[ExecutionResult.Status]] and [[ExecutionResult.ResultType]] cases. */
object ExecutionResult {

  /**
   * Creates a result that uses the same `lines` as both its streaming lines and its summary lines.
   */
  def withoutSummarySpecificOutput(
    resultType: ResultType,
    label: String,
    status: Status,
    offset: Int,
    annotations: List[TestAnnotationMap],
    lines: List[Line],
    duration: Option[Long]
  ): ExecutionResult =
    ExecutionResult(
      resultType,
      label,
      status,
      offset,
      annotations,
      lines,
      lines, // Re-uses lines when we don't have summary-specific output,
      duration
    )

  /** The state of a test or suite in an [[ExecutionResult]]. */
  sealed abstract class Status

  /** The cases of [[ExecutionResult.Status]]. */
  object Status {

    /**
     * The suite has started. [[IntelliJRenderer]] uses it for the start of a suite; [[ConsoleRenderer]] does not produce it.
     */
    case object Started extends Status

    /** The test failed, by a failed assertion or by an error. */
    case object Failed extends Status

    /** The test passed. [[ConsoleRenderer]] also uses it for every suite it shows. */
    case object Passed extends Status

    /** The test was ignored, for example with `TestAspect.ignore`. */
    case object Ignored extends Status
  }

  /** What an [[ExecutionResult]] describes. */
  sealed abstract class ResultType

  /** The cases of [[ExecutionResult.ResultType]]. */
  object ResultType {

    /** A single test. */
    case object Test extends ResultType

    /** A suite of tests. */
    case object Suite extends ResultType

    /** Other output. [[ConsoleRenderer]] shows its streaming lines as they are. */
    case object Other extends ResultType
  }
}
