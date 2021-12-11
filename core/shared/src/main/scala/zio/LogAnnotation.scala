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

final class LogAnnotation[V] private (
  val identifier: String,
  val initial: V,
  val combine: (V, V) => V,
  val render: V => String,
  private val tag: Tag[V]
) extends Serializable {

  override def equals(that: Any): Boolean = (that: @unchecked) match {
    case that: LogAnnotation[_] => (identifier, tag) == ((that.identifier, that.tag))
  }

  override lazy val hashCode: Int =
    (identifier, tag).hashCode
}

object LogAnnotation {

  def apply[V](
    identifier: String,
    initial: V,
    combine: (V, V) => V = ((_: V, v: V) => v),
    render: V => String = (v: V) => v.toString
  )(implicit
    tag: Tag[V]
  ): LogAnnotation[V] =
    new LogAnnotation(identifier, initial, combine, render, tag)
}
