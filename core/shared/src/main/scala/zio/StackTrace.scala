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

package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * The ZIO stack trace of a fiber: the identity of the fiber and the locations
 * (`Trace` values) of the effects it was running, most recent first. A
 * `Cause` carries one for each failure, and `ZIO.stackTrace` returns the
 * current one.
 *
 * {{{
 * val trace: UIO[String] =
 *   ZIO.stackTrace.map(_.prettyPrint("Current trace:"))
 * }}}
 */
final case class StackTrace(
  fiberId: FiberId,
  stackTrace: Chunk[Trace]
) { self =>

  /**
   * Concatenates two stack traces: the locations of this one, then those of
   * `that` one, with the fiber ids combined. Returns the other one when either
   * is empty.
   */
  def ++(that: StackTrace): StackTrace =
    if ((self eq that) || self.isEmpty) that
    else if (that.isEmpty) self
    else StackTrace(self.fiberId combine that.fiberId, self.stackTrace ++ that.stackTrace)

  /** Returns `true` when there is no fiber id and no location. */
  def isEmpty: Boolean = (fiberId eq FiberId.None) && stackTrace.isEmpty

  /** Returns the number of locations. */
  def size: Int = stackTrace.length

  /**
   * Converts the ZIO trace into a Java stack trace, by converting each trace
   * element into a Java stack trace element. Locations that cannot be parsed,
   * such as `Trace.empty`, are left out, and adjacent duplicates are removed.
   */
  def toJava: Chunk[StackTraceElement] =
    stackTrace.flatMap(Trace.toJava).dedupe

  /**
   * Renders the trace as text: the `prefix` line, then one `at <location>`
   * line per Java stack trace element (see `toJava`), indented with a tab.
   */
  def prettyPrint(prefix: String): String = prettyPrint(Some(prefix))

  /**
   * Renders the trace as text: one `at <location>` line per Java stack trace
   * element (see `toJava`), indented with a tab.
   */
  def prettyPrint: String = prettyPrint(None)

  private def prettyPrint(prefix: Option[String]): String = {
    def renderElements(indent: Int, elements: Chunk[StackTraceElement]) = {
      val baseIndent  = "\t" * indent
      val traceIndent = baseIndent + "\t"

      val suffix = elements.map(trace => s"${traceIndent}at ${trace}").mkString("\n")

      prefix.map(prefix => s"${baseIndent}${prefix}\n").getOrElse("") + suffix
    }

    renderElements(0, toJava)
  }

  override def toString(): String =
    if (isEmpty) "StackTrace.none"
    else prettyPrint("Stack trace for thread \"" + fiberId.threadName + "\":")
}

/**
 * Constructors for [[StackTrace]].
 */
object StackTrace {

  /**
   * Converts a Java stack trace into a ZIO stack trace for the specified fiber.
   * It keeps the elements before the first `zio.internal.FiberRuntime` frame,
   * and stops at the first element whose file and line are those of `trace`.
   */
  def fromJava(fiberId: FiberId, stackTrace: Array[StackTraceElement])(implicit trace: Trace): StackTrace =
    StackTrace(
      fiberId,
      Chunk
        .fromArray(stackTrace.takeWhile(_.getClassName != "zio.internal.FiberRuntime"))
        .map(Trace.fromJava)
        .takeWhile(!Trace.equalIgnoreLocation(_, trace))
    )

  /** The empty stack trace, with no fiber id and no location. */
  val none: StackTrace =
    StackTrace(FiberId.None, Chunk.empty)
}
