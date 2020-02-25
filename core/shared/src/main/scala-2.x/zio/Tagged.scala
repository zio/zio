/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

final case class Tagged[A](tag: TaggedType[A]) {
  override def equals(that: Any): Boolean = that match {
    case Tagged(that) => tag.tag == that.tag
    case _            => false
  }
  override def hashCode: Int    = tag.tag.hashCode
  override def toString: String = tag.tag.toString
}

object Tagged {
  implicit def tagged[A](implicit tag: TaggedType[A]): Tagged[A] =
    Tagged(tag)
}
