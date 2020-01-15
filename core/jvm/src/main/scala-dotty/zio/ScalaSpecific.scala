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

private[zio] object ScalaSpecific {
  type Tagged[A] = scala.reflect.ClassTag[A]
  type TagType   = scala.reflect.ClassTag[_]

  private[zio] def taggedIsSubtype[A, B](left: TagType, right: TagType): Boolean =
    right.runtimeClass.isAssignableFrom(left.runtimeClass)

  private[zio] def taggedTagType[A](tagged: Tagged[A]): TagType = tagged

  private[zio] def taggedGetHasServices[A](t: TagType): Set[TagType] = {
    val _ = t
    Set()
  }
}
