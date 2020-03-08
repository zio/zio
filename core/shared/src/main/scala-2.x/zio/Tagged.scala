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

import com.github.ghik.silencer.silent

final case class Tagged[A](tag: TaggedType[A]) {
  override def equals(that: Any): Boolean = that match {
    case Tagged(that) => tag.tag == that.tag
    case _            => false
  }
  override def hashCode: Int    = tag.tag.hashCode
  override def toString: String = tag.tag.toString
}

object Tagged {

  implicit def tagged[A: TaggedType](implicit a: TaggedType[A]): Tagged[A] =
    Tagged(a)

  @silent("is never used")
  implicit def taggedF[F[_], A](implicit tag: TaggedTypeF[F], a: Tagged[A]): Tagged[F[A]] = {
    implicit val tag0 = a.tag
    Tagged(TaggedType[F[A]])
  }

  @silent("is never used")
  implicit def taggedF2[F[_, _], A, B](
    implicit tag: TaggedTypeF2[F],
    a: Tagged[A],
    b: Tagged[B]
  ): Tagged[F[A, B]] = {
    implicit val tag0 = a.tag
    implicit val tag1 = b.tag
    Tagged(TaggedType[F[A, B]])
  }

  @silent("is never used")
  implicit def taggedF3[F[_, _, _], A, B, C](
    implicit tag: TagggedTypeF3[F],
    a: Tagged[A],
    b: Tagged[B],
    c: Tagged[C]
  ): Tagged[F[A, B, C]] = {
    implicit val tag0 = a.tag
    implicit val tag1 = b.tag
    implicit val tag2 = c.tag
    Tagged(TaggedType[F[A, B, C]])
  }
}
