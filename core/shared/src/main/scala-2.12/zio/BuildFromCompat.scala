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

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.Builder

private[zio] trait BuildFromCompat {

  /**
   * An alias for the standard library's builder factory:
   * `scala.collection.generic.CanBuildFrom` on Scala 2.12, and
   * `scala.collection.BuildFrom` on Scala 2.13 and Scala 3. Operations such as
   * `ZIO.foreach` take one to build a result collection of the same kind as
   * their input.
   */
  type BuildFrom[-From, -A, +C] = CanBuildFrom[From, A, C]

  /**
   * Adds the Scala 2.13 `BuildFrom` methods `fromSpecific` and `newBuilder` to
   * a Scala 2.12 `CanBuildFrom`.
   */
  implicit class BuildFromOps[From, A, C](private val self: BuildFrom[From, A, C]) {
    /**
     * Builds a collection of type `C` from the elements of `iterable`, with
     * `from` as the source collection.
     */
    def fromSpecific(from: From)(iterable: Iterable[A]): C = {
      val builder = newBuilder(from)
      builder ++= iterable
      builder.result()
    }
    /**
     * Returns a new builder for a collection of type `C`, with `from` as the
     * source collection.
     */
    def newBuilder(from: From): Builder[A, C] =
      self.apply(from)
  }
}
