/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
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

import scala.collection.IterableOps

private[zio] trait BuildFromCompat {

  /**
   * An alias for the standard library's builder factory:
   * `scala.collection.generic.CanBuildFrom` on Scala 2.12, and
   * `scala.collection.BuildFrom` on Scala 2.13 and Scala 3. Operations such as
   * `ZIO.foreach` take one to build a result collection of the same kind as
   * their input.
   */
  type BuildFrom[-From, -A, +C] = scala.collection.BuildFrom[From, A, C]

  /**
   * An implicit `BuildFrom` that builds a collection of `Nothing` from a
   * collection of the same kind, available when the result element type is
   * `Nothing`, as in `ZIO.foreach(list)(_ => ZIO.never)`. It delegates to the
   * standard library's `BuildFrom.buildFromIterableOps`.
   */
  implicit def buildFromNothing[Collection[+Element] <: Iterable[Element] with IterableOps[Any, Collection, _]]
    : BuildFrom[Collection[Any], Nothing, Collection[Nothing]] =
    scala.collection.BuildFrom.buildFromIterableOps[Collection, Any, Nothing]
}
