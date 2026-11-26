/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import java.io.{PrintStream, PrintWriter}

/**
 * Represents a failure in a fiber. This could be caused by some non-
 * recoverable error, such as a defect or system error, by some typed error, or
 * by interruption (or combinations of all of the above).
 *
 * This class is used to wrap ZIO failures into something that can be thrown, to
 * better integrate with Scala exception handling. For example,
 * `Exit#getOrThrowFiberFailure` throws one for a failed `Exit`.
 *
 * Its message is the message of the first entry of `Cause#unified` (a failure,
 * defect or interruption), or `<unknown>` for an empty cause. Its stack trace
 * is the ZIO trace of the cause, and its `getCause` is the first defect, or
 * else the first typed failure when that failure is a `Throwable`. `toString`
 * and `printStackTrace` render the whole cause with `Cause#prettyPrint`.
 *
 * {{{
 * val failure: FiberFailure = FiberFailure(Cause.fail("payment declined"))
 *
 * val message: String = failure.getMessage
 * // payment declined
 * }}}
 */
final case class FiberFailure(cause: Cause[Any]) extends Throwable(null, null, true, false) {
  override def getMessage: String =
    cause.unified.headOption.fold("<unknown>")(_.message)

  override def getStackTrace: Array[StackTraceElement] =
    cause.trace.toJava.toArray

  override def getCause: Throwable =
    cause.dieOption.orElse(cause.failureOption.collect { case t: Throwable => t }).orNull

  // Note: unlike standard Java exceptions, this includes the stack trace.
  override def toString: String =
    cause.prettyPrint

  override def printStackTrace(s: PrintStream): Unit =
    cause.prettyPrintWith(s.println)(Unsafe.unsafe)

  override def printStackTrace(s: PrintWriter): Unit =
    cause.prettyPrintWith(s.println)(Unsafe.unsafe)

  /**
   * Adds each entry of `Cause#unified` after the first one, converted to a
   * `Throwable`, to the suppressed exceptions of this `FiberFailure`. It does
   * nothing when the `FiberFailure` already has suppressed exceptions, so
   * calling it twice adds them once.
   */
  def fillSuppressed()(implicit unsafe: Unsafe): Unit =
    if (getSuppressed.length == 0)
      for (unified <- cause.unified.iterator.drop(1))
        addSuppressed(unified.toThrowable)
}
