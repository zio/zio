/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
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

import zio.internal.stacktracer.{ParsedTrace, Tracer}
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * Constructors and helpers for `Trace`, the source location that ZIO records in
 * execution traces.
 */
object Trace {

  /** Creates a trace for the code location `location` at `file`:`line`. */
  def apply(location: String, file: String, line: Int): Trace =
    Tracer.instance(location, file, line)

  /**
   * Returns `true` when both traces point to the same file and line, whatever
   * their locations (the class and method names). Returns `false` when either
   * trace cannot be parsed, as for `Trace.empty`.
   */
  def equalIgnoreLocation(left: Trace, right: Trace): Boolean = {
    val leftParsed = parseOrNull(left)
    if (leftParsed eq null) return false

    val rightParsed = parseOrNull(right)
    if (rightParsed eq null) return false

    val leftFile  = leftParsed.file
    val leftLine  = leftParsed.line
    val rightFile = rightParsed.file
    val rightLine = rightParsed.line

    leftFile == rightFile && leftLine == rightLine
  }

  /** The empty trace, which carries no location. */
  val empty: Trace = Tracer.instance.empty

  /**
   * Creates a trace from a Java stack trace element, with
   * `className.methodName` as its location.
   */
  def fromJava(stackTraceElement: StackTraceElement): Trace =
    Trace(
      stackTraceElement.getClassName + "." + stackTraceElement.getMethodName,
      stackTraceElement.getFileName,
      stackTraceElement.getLineNumber
    )

  /**
   * Converts a trace to a Java stack trace element, splitting its location at
   * the last `.` into a class name and a method name. Returns `None` when the
   * trace cannot be parsed, as for `Trace.empty`.
   */
  def toJava(trace: Trace): Option[StackTraceElement] = {
    val parsed = parseOrNull(trace)
    if (parsed eq null) None
    else {
      val location = parsed.location
      val file     = parsed.file
      val line     = parsed.line

      val last = location.lastIndexOf('.')

      val e =
        if (last < 0) new StackTraceElement("", location, file, line)
        else {
          val before = location.substring(0, last)
          val after  = location.substring(last + 1)
          new StackTraceElement(before, after, file, line)
        }

      Some(e)
    }
  }

  /**
   * Extracts the location, file and line of a trace, or returns `None` when the
   * trace cannot be parsed, as for `Trace.empty`.
   */
  def unapply(trace: Trace): Option[(String, String, Int)] = Tracer.instance.unapply(trace)

  private[zio] def parseOrNull(trace: Trace): ParsedTrace = Tracer.instance.parseOrNull(trace)

}
