/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

final case class LogAnnotations(annotations: Map[String, Any]) { self =>

  def annotate(key: String, value: Any): LogAnnotations =
    self.copy(annotations = annotations + (key -> value))

  def nonEmpty: Boolean =
    annotations.nonEmpty

  def render: String = {
    val sb = new StringBuilder()

    unsafeRender(sb)

    sb.toString()
  }

  private[zio] def unsafeRender(sb: StringBuilder): Unit = {
    val it    = annotations.iterator
    var first = true

    while (it.hasNext) {
      if (first) {
        first = false
      } else {
        sb.append(" ")
      }

      val (key, value) = it.next()

      sb.append(key)
      sb.append("=")
      sb.append(value)
    }
  }
}

object LogAnnotations {

  val empty: LogAnnotations =
    LogAnnotations(Map.empty)
}
