/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

import zio.stm.ZSTM

package object internal {

  /**
   * Returns an effect that models success with the specified value.
   */
  def ZIOSucceedNow[A](a: A): UIO[A] =
    ZIO.succeedNow(a)

  /**
   * Lifts an eager, pure value into a Managed.
   */
  def ZManagedSucceedNow[A](r: A): ZManaged[Any, Nothing, A] =
    ZManaged.succeedNow(r)

  /**
   * Returns an `STM` effect that succeeds with the specified value.
   */
  def ZSTMSucceedNow[A](a: A): ZSTM[Any, Nothing, A] =
    ZSTM.succeedNow(a)

  type ZLogger = (Fiber.Id, LogLevel, () => String, Map[FiberRef.Runtime[_], AnyRef], List[LogSpan]) => Unit

  def defaultLogFormat(
    fiberId: Fiber.Id,
    logLevel: LogLevel,
    message0: () => String,
    context: Map[FiberRef.Runtime[_], AnyRef],
    spans0: List[LogSpan]
  ): String = {
    val sb = new StringBuilder()

    val _ = context

    val now = java.time.Instant.now()

    val nowMillis = java.lang.System.currentTimeMillis()

    sb.append(now.toString())
      .append(" level=")
      .append(logLevel.label)
      .append(" thread=")
      .append(fiberId.toString)
      .append(" message=\"")
      .append(message0())
      .append("\"")

    if (spans0.nonEmpty) {
      sb.append(" ")

      val it    = spans0.iterator
      var first = true

      while (it.hasNext) {
        if (first) {
          first = false
        } else {
          sb.append(" ")
        }

        val span = it.next()

        sb.append(span.render(nowMillis))
      }
    }

    sb.toString()
  }
}
