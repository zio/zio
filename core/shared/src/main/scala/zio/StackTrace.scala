/*
 * Copyright 2019-2023 John A. De Goes and the ZIO Contributors
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
    stackTrace.flatMap(Trace.toJava).dedupe

  def prettyPrint(prefix: String): String = prettyPrint(Some(prefix))

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
    prettyPrint("Stack trace for thread \"" + fiberId.threadName + "\":")
}

object StackTrace {

  def fromJava(fiberId: FiberId, stackTrace: Array[StackTraceElement])(implicit trace: Trace): StackTrace =
    StackTrace(
      fiberId,
      Chunk
        .fromArray(stackTrace.takeWhile(_.getClassName != "zio.internal.FiberRuntime"))
        .map(Trace.fromJava)
        .takeWhile(!Trace.equalIgnoreLocation(_, trace))
    )

  val none: StackTrace =
    StackTrace(FiberId.None, Chunk.empty)
}
