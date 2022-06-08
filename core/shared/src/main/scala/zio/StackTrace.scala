/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

import scala.annotation.tailrec

final case class StackTrace(
  fiberId: FiberId,
  stackTrace: Chunk[Trace]
) { self =>

  def ++(that: StackTrace): StackTrace =
    StackTrace(self.fiberId combine that.fiberId, self.stackTrace ++ that.stackTrace)

  def size: Int = stackTrace.length

  /**
   * Converts the ZIO trace into a Java stack trace, by converting each trace
   * element into a Java stack trace element.
   */
  def toJava: Chunk[StackTraceElement] =
    stackTrace.flatMap(Trace.toJava)

  override def toString(): String = {
    def renderElements(indent: Int, prefix: String, elements: Chunk[StackTraceElement]) = {
      val baseIndent  = "\t" * indent
      val traceIndent = baseIndent + "\t"

      s"${baseIndent}${prefix}\n" +
        elements.map(trace => s"${traceIndent}at ${trace}").mkString("\n")
    }

    renderElements(0, "Exception in thread \"" + fiberId.threadName + "\":", toJava)
  }
}

object StackTrace {

  lazy val none: StackTrace =
    StackTrace(FiberId.None, Chunk.empty)
}
