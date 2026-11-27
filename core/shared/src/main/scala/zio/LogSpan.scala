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

/**
 * A named period of time that is attached to the log statements written while
 * it is active.
 *
 * @param label
 *   The name of the span.
 * @param startTime
 *   The start of the span, as milliseconds since the Unix epoch (the value of
 *   `java.lang.System.currentTimeMillis` when the span started).
 */
final case class LogSpan(label: String, startTime: Long) {
  private[zio] def renderInto(sb: StringBuilder, now: Long)(implicit unsafe: Unsafe): Unit = {
    if (label.indexOf(' ') < 0) sb.append(label)
    else {
      sb.append('"')
      sb.append(label)
      sb.append('"')
    }

    sb.append('=')
    sb.append(now - startTime)
    sb.append("ms")

    ()
  }

  /**
   * Renders the span as `label=<elapsed>ms`, where `elapsed` is `now` minus
   * `startTime`, in milliseconds. `elapsed` is negative when `now` is before
   * `startTime`.
   *
   * {{{
   * val a: String = LogSpan("db", 100L).render(250L)
   * // db=150ms
   * }}}
   */
  def render(now: Long): String = {
    val sb = new StringBuilder()

    renderInto(sb, now)(Unsafe)

    sb.toString()
  }
}
