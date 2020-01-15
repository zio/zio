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
  import scala.reflect.runtime.universe._

  type Tagged[A] = TypeTag[A]
  type TagType   = Type

  private[zio] def taggedTagType[A](t: Tagged[A]): TagType = t.tpe.dealias

  private[zio] def taggedIsSubtype(left: TagType, right: TagType): Boolean =
    left <:< right

  private[zio] def taggedGetHasServices[A](t: TagType): Set[TagType] =
    t.dealias match {
      case RefinedType(parents, _) => parents.toSet.flatMap((p: TagType) => taggedGetHasServices(p))
      case t                       => Set(t.typeArgs(0).dealias)
    }
}
