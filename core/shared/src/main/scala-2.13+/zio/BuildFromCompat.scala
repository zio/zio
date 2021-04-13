/*
 * Copyright 2020-2021 John A. De Goes and the ZIO Contributors
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

import scala.collection.IterableOps

private[zio] trait BuildFromCompat {

  type BuildFrom[-From, -A, +C] = scala.collection.BuildFrom[From, A, C]

  @deprecated("Use BuildFrom.buildFromIterableOps or buildFromNothing instead", "1.0.6")
  def buildFromAny[Element, Collection[+Element] <: Iterable[Element] with IterableOps[Any, Collection, Any]]
    : BuildFrom[Collection[Any], Element, Collection[Element]] =
    scala.collection.BuildFrom.buildFromIterableOps[Collection, Any, Element]

  implicit def buildFromNothing[A, Collection[+Element] <: Iterable[Element] with IterableOps[A, Collection, _]]
    : BuildFrom[Collection[A], Nothing, Collection[Nothing]] =
    scala.collection.BuildFrom.buildFromIterableOps[Collection, A, Nothing]
}
