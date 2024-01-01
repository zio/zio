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

import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

object Trace {

  def apply(location: String, file: String, line: Int): Trace =
    Tracer.instance(location, file, line)

  def equalIgnoreLocation(left: Trace, right: Trace): Boolean =
    (left, right) match {
      case (Trace(_, leftFile, leftLine), Trace(_, rightFile, rightLine)) =>
        leftFile == rightFile && leftLine == rightLine
      case _ =>
        false
    }

  val empty: Trace =
    Tracer.instance.empty

  def fromJava(stackTraceElement: StackTraceElement): Trace =
    Trace(
      stackTraceElement.getClassName + "." + stackTraceElement.getMethodName,
      stackTraceElement.getFileName,
      stackTraceElement.getLineNumber
    )

  def toJava(trace: Trace): Option[StackTraceElement] =
    trace match {
      case Trace(location, file, line) =>
        val last = location.lastIndexOf(".")

        val (before, after) = if (last < 0) ("", "." + location) else location.splitAt(last)

        Some(new StackTraceElement(before, after.drop(1), file, line))

      case _ => None
    }

  def unapply(trace: Trace): Option[(String, String, Int)] =
    Tracer.instance.unapply(trace)
}
